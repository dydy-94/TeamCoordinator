package org.cmb.infrastructure.persistent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.cmb.infrastructure.persistent.mapper.ConversationEventSequenceMapper;
import org.cmb.infrastructure.persistent.mapper.CoordinatorDispatchMapper;
import org.cmb.infrastructure.persistent.mapper.ProjectEventMapper;
import org.cmb.infrastructure.persistent.mapper.ProjectMessageMapper;
import org.cmb.application.domain.AgentEvent;
import org.cmb.common.enums.EventVisibility;
import org.cmb.application.domain.entity.MessageDO;
import org.cmb.application.dto.MessageRequest;
import org.cmb.application.domain.entity.ProjectEventDO;
import org.cmb.common.enums.ProjectEventType;
import org.cmb.application.domain.RequestIdentity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * Message/event persistence facade. Owns sequence allocation and JSON
 * (de)serialization; all SQL lives in the per-table mappers
 * ({@link ProjectMessageMapper}, {@link ProjectEventMapper},
 * {@link ConversationEventSequenceMapper}, {@link CoordinatorDispatchMapper}).
 */
@Repository
public class MessageEventRepository {

    private final ProjectMessageMapper messageMapper;
    private final ProjectEventMapper eventMapper;
    private final ConversationEventSequenceMapper sequenceMapper;
    private final CoordinatorDispatchMapper dispatchMapper;
    private final ObjectMapper objectMapper;

    public MessageEventRepository(
            ProjectMessageMapper messageMapper,
            ProjectEventMapper eventMapper,
            ConversationEventSequenceMapper sequenceMapper,
            CoordinatorDispatchMapper dispatchMapper,
            ObjectMapper objectMapper) {
        this.messageMapper = messageMapper;
        this.eventMapper = eventMapper;
        this.sequenceMapper = sequenceMapper;
        this.dispatchMapper = dispatchMapper;
        this.objectMapper = objectMapper;
    }

    public MessageDO findDuplicate(
            RequestIdentity identity, String projectId, String taskId,
            MessageRequest request) {
        List<MessageDO> rows = messageMapper.findDuplicate(
                identity.getTenantId(), projectId, taskId,
                request.getClientMessageId());
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void insertMessage(
            RequestIdentity identity,
            String projectId,
            String conversationId,
            String messageId,
            MessageRequest request) {
        messageMapper.insertMessage(
                messageId,
                identity.getTenantId(),
                projectId,
                conversationId,
                identity.getUserId(),
                request.getClientMessageId(),
                request.getText(),
                writeJson(request.getAttachmentRefs()));
    }

    public long allocateSequence(String tenantId, String taskId) {
        try {
            sequenceMapper.insertSequence(tenantId, taskId);
            return 1L;
        } catch (DuplicateKeyException ignored) {
            for (int attempt = 0; attempt < 20; attempt++) {
                List<Long> rows = sequenceMapper.selectSequence(tenantId, taskId);
                Long next = rows.isEmpty() ? null : rows.get(0);
                int updated = sequenceMapper.updateSequence(tenantId, taskId, next, next + 1);
                if (updated == 1) {
                    return next;
                }
            }
            throw new IllegalStateException("Could not allocate a task event sequence.");
        }
    }

    public ProjectEventDO insertEvent(
            RequestIdentity identity,
            String projectId,
            String conversationId,
            String messageId,
            ProjectEventType type,
            EventVisibility visibility,
            JsonNode payload) {
        long sequence = allocateSequence(identity.getTenantId(), conversationId);
        ProjectEventDO event = new ProjectEventDO();
        event.setId("event-" + UUID.randomUUID());
        event.setProjectId(projectId);
        event.setConversationId(conversationId);
        event.setMessageId(messageId);
        event.setSequence(sequence);
        event.setType(type);
        event.setPayload(payload);
        event.setCreatedAt(Instant.now());
        eventMapper.insertEvent(
                event.getId(),
                identity.getTenantId(),
                projectId,
                conversationId,
                messageId,
                sequence,
                type.name(),
                visibility.name(),
                payload == null ? null : payload.toString());
        return event;
    }

    public void insertDispatch(
            RequestIdentity identity,
            String projectId,
            String conversationId,
            String messageId) {
        dispatchMapper.insertDispatch(
                "dispatch-" + UUID.randomUUID(),
                identity.getTenantId(),
                projectId,
                conversationId,
                messageId);
    }

/** 同会话中某 MARKER 之后的下一个 MARKER 的 payload（用于窗口上界）。 */
    public String findNextMarkerPayload(
            String tenantId, String conversationId, String sessionId,
            long afterSequence) {
        java.util.List<String> rows = eventMapper.findNextMarkerPayload(
                tenantId, conversationId, sessionId, afterSequence);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<ProjectEventDO> findPublicEvents(
            String tenantId, String projectId, String taskId, long afterSequence) {
        return findPublicEvents(tenantId, projectId, taskId, afterSequence, 1000);
    }

    public List<ProjectEventDO> findPublicEvents(
            String tenantId, String projectId, String taskId,
            long afterSequence, int limit) {
        List<ProjectEventDO> events = eventMapper.findPublicEvents(
                tenantId, projectId, taskId, afterSequence, limit);
        for (ProjectEventDO event : events) {
            JsonNode payload = event.getPayload();
            // Reconstruct AgentEvent for consistent SSE emission on replay
            if (payload != null && payload.has("type") && payload.has("agentId")) {
                try {
                    event.setAgentEvent(
                            objectMapper.treeToValue(payload, AgentEvent.class));
                } catch (Exception ignored) {
                    // Not an AgentEvent payload — use legacy format
                }
            }
        }
        return events;
    }

    public List<String> findRecentMessageTexts(
            String tenantId, String projectId, String taskId, int limit) {
        return messageMapper.findRecentMessageTexts(tenantId, projectId, taskId, limit);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not serialize message data.", ex);
        }
    }
}
