package org.cmb.infrastructure.persistent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.cmb.infrastructure.persistent.mapper.MessageEventMapper;
import org.cmb.application.domain.AgentEvent;
import org.cmb.common.enums.EventVisibility;
import org.cmb.application.dto.MessageAcceptedResponse;
import org.cmb.application.dto.MessageRequest;
import org.cmb.application.domain.ProjectEvent;
import org.cmb.common.enums.ProjectEventType;
import org.cmb.application.domain.RequestIdentity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * Message/event persistence facade. Owns sequence allocation and JSON
 * (de)serialization; all SQL lives in {@link MessageEventMapper}.
 */
@Repository
public class MessageEventRepository {

    private final MessageEventMapper mapper;
    private final ObjectMapper objectMapper;

    public MessageEventRepository(MessageEventMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public MessageAcceptedResponse findDuplicate(
            RequestIdentity identity, String projectId, String taskId,
            MessageRequest request) {
        List<MessageAcceptedResponse> rows = mapper.findDuplicate(
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
        mapper.insertMessage(
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
            mapper.insertSequence(tenantId, taskId);
            return 1L;
        } catch (DuplicateKeyException ignored) {
            for (int attempt = 0; attempt < 20; attempt++) {
                List<Long> rows = mapper.selectSequence(tenantId, taskId);
                Long next = rows.isEmpty() ? null : rows.get(0);
                int updated = mapper.updateSequence(tenantId, taskId, next, next + 1);
                if (updated == 1) {
                    return next;
                }
            }
            throw new IllegalStateException("Could not allocate a task event sequence.");
        }
    }

    public ProjectEvent insertEvent(
            RequestIdentity identity,
            String projectId,
            String conversationId,
            String messageId,
            ProjectEventType type,
            EventVisibility visibility,
            JsonNode payload) {
        long sequence = allocateSequence(identity.getTenantId(), conversationId);
        ProjectEvent event = new ProjectEvent();
        event.setId("event-" + UUID.randomUUID());
        event.setProjectId(projectId);
        event.setConversationId(conversationId);
        event.setMessageId(messageId);
        event.setSequence(sequence);
        event.setType(type);
        event.setPayload(payload);
        event.setCreatedAt(Instant.now());
        mapper.insertEvent(
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
        mapper.insertDispatch(
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
        java.util.List<String> rows = mapper.findNextMarkerPayload(
                tenantId, conversationId, sessionId, afterSequence);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<ProjectEvent> findPublicEvents(
            String tenantId, String projectId, String taskId, long afterSequence) {
        return findPublicEvents(tenantId, projectId, taskId, afterSequence, 1000);
    }

    public List<ProjectEvent> findPublicEvents(
            String tenantId, String projectId, String taskId,
            long afterSequence, int limit) {
        List<ProjectEvent> events = mapper.findPublicEvents(
                tenantId, projectId, taskId, afterSequence, limit);
        for (ProjectEvent event : events) {
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
        return mapper.findRecentMessageTexts(tenantId, projectId, taskId, limit);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not serialize message data.", ex);
        }
    }
}
