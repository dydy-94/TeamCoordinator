package org.cmb.teamcoordinator.coordinator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.cmb.teamcoordinator.project.RequestIdentity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MessageEventRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public MessageEventRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public MessageAcceptedResponse findDuplicate(
            RequestIdentity identity, String projectId, String taskId,
            MessageRequest request) {
        List<MessageAcceptedResponse> rows = jdbc.query(
                "SELECT m.business_id AS id, m.conversation_id, c.session_id, m.status "
                        + "FROM project_message m JOIN project_conversation c "
                        + "ON c.business_id = m.conversation_id "
                        + "WHERE m.tenant_id = ? AND m.project_id = ? "
                        + "AND m.conversation_id = ? "
                        + "AND m.client_message_id = ? LIMIT 1",
                (rs, rowNum) -> new MessageAcceptedResponse(
                        rs.getString("id"),
                        rs.getString("conversation_id"),
                        rs.getString("session_id"),
                        rs.getString("status")),
                identity.getTenantId(),
                projectId,
                taskId,
                request.getClientMessageId());
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void insertMessage(
            RequestIdentity identity,
            String projectId,
            String conversationId,
            String messageId,
            MessageRequest request) {
        jdbc.update(
                "INSERT INTO project_message "
                        + "(business_id, tenant_id, project_id, conversation_id, user_id, client_message_id, "
                        + "message_text, attachment_refs, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                messageId,
                identity.getTenantId(),
                projectId,
                conversationId,
                identity.getUserId(),
                request.getClientMessageId(),
                request.getText(),
                writeJson(request.getAttachmentRefs()),
                "ACCEPTED");
    }

    public long allocateSequence(String tenantId, String taskId) {
        try {
            jdbc.update(
                    "INSERT INTO conversation_event_sequence "
                            + "(tenant_id, conversation_id, next_sequence) "
                            + "VALUES (?, ?, 2)",
                    tenantId,
                    taskId);
            return 1L;
        } catch (DuplicateKeyException ignored) {
            for (int attempt = 0; attempt < 20; attempt++) {
                Long next = jdbc.queryForObject(
                        "SELECT next_sequence FROM conversation_event_sequence "
                                + "WHERE tenant_id = ? AND conversation_id = ?",
                        Long.class,
                        tenantId,
                        taskId);
                int updated = jdbc.update(
                        "UPDATE conversation_event_sequence SET next_sequence = ? "
                                + "WHERE tenant_id = ? AND conversation_id = ? "
                                + "AND next_sequence = ?",
                        next + 1,
                        tenantId,
                        taskId,
                        next);
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
        jdbc.update(
                "INSERT INTO project_event "
                        + "(business_id, tenant_id, project_id, conversation_id, message_id, sequence, "
                        + "event_type, visibility, payload) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
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
        jdbc.update(
                "INSERT INTO coordinator_dispatch "
                        + "(business_id, tenant_id, project_id, conversation_id, message_id, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "dispatch-" + UUID.randomUUID(),
                identity.getTenantId(),
                projectId,
                conversationId,
                messageId,
                "PENDING");
    }

    public List<ProjectEvent> findPublicEvents(
            String tenantId, String projectId, String taskId, long afterSequence) {
        return findPublicEvents(tenantId, projectId, taskId, afterSequence, 1000);
    }

    public List<ProjectEvent> findPublicEvents(
            String tenantId, String projectId, String taskId,
            long afterSequence, int limit) {
        return jdbc.query(
                "SELECT business_id AS id, project_id, conversation_id, message_id, sequence, event_type, "
                        + "payload, created_at FROM project_event "
                        + "WHERE tenant_id = ? AND project_id = ? AND visibility = 'PUBLIC' "
                        + "AND conversation_id = ? "
                        + "AND sequence > ? ORDER BY sequence LIMIT ?",
                (rs, rowNum) -> mapEvent(rs),
                tenantId,
                projectId,
                taskId,
                afterSequence,
                limit);
    }

    public List<String> findRecentMessageTexts(
            String tenantId, String projectId, String taskId, int limit) {
        return jdbc.queryForList(
                "SELECT message_text FROM project_message "
                        + "WHERE tenant_id = ? AND project_id = ? "
                        + "AND conversation_id = ? "
                        + "ORDER BY created_at DESC LIMIT ?",
                String.class,
                tenantId,
                projectId,
                taskId,
                limit);
    }

    private ProjectEvent mapEvent(ResultSet rs) throws SQLException {
        ProjectEvent event = new ProjectEvent();
        event.setId(rs.getString("id"));
        event.setProjectId(rs.getString("project_id"));
        event.setConversationId(rs.getString("conversation_id"));
        event.setMessageId(rs.getString("message_id"));
        event.setSequence(rs.getLong("sequence"));
        event.setType(ProjectEventType.valueOf(rs.getString("event_type")));
        event.setPayload(readJson(rs.getString("payload")));
        Timestamp createdAt = rs.getTimestamp("created_at");
        event.setCreatedAt(createdAt == null ? null : createdAt.toInstant());
        return event;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not serialize message data.", ex);
        }
    }

    private JsonNode readJson(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read event payload.", ex);
        }
    }
}
