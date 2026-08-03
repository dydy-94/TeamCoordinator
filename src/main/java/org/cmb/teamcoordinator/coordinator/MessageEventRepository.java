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

    public String getOrCreateConversation(RequestIdentity identity, String projectId) {
        List<String> existing = jdbc.queryForList(
                "SELECT id FROM project_conversation WHERE tenant_id = ? AND project_id = ?",
                String.class,
                identity.getTenantId(),
                projectId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        String conversationId = "conversation-" + UUID.randomUUID();
        try {
            jdbc.update(
                    "INSERT INTO project_conversation (id, tenant_id, project_id) VALUES (?, ?, ?)",
                    conversationId,
                    identity.getTenantId(),
                    projectId);
            return conversationId;
        } catch (DuplicateKeyException ex) {
            return jdbc.queryForObject(
                    "SELECT id FROM project_conversation WHERE tenant_id = ? AND project_id = ?",
                    String.class,
                    identity.getTenantId(),
                    projectId);
        }
    }

    public MessageAcceptedResponse findDuplicate(
            RequestIdentity identity, String projectId, MessageRequest request) {
        List<MessageAcceptedResponse> rows = jdbc.query(
                "SELECT id, conversation_id, status FROM project_message "
                        + "WHERE tenant_id = ? AND project_id = ? "
                        + "AND (client_message_id = ? OR idempotency_key = ?) LIMIT 1",
                (rs, rowNum) -> new MessageAcceptedResponse(
                        rs.getString("id"),
                        rs.getString("conversation_id"),
                        rs.getString("status")),
                identity.getTenantId(),
                projectId,
                request.getClientMessageId(),
                request.getIdempotencyKey());
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
                        + "(id, tenant_id, project_id, conversation_id, user_id, client_message_id, "
                        + "idempotency_key, message_text, attachment_refs, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                messageId,
                identity.getTenantId(),
                projectId,
                conversationId,
                identity.getUserId(),
                request.getClientMessageId(),
                request.getIdempotencyKey(),
                request.getText(),
                writeJson(request.getAttachmentRefs()),
                "ACCEPTED");
    }

    public long allocateSequence(String tenantId, String projectId) {
        try {
            jdbc.update(
                    "INSERT INTO project_event_sequence (tenant_id, project_id, next_sequence) "
                            + "VALUES (?, ?, 2)",
                    tenantId,
                    projectId);
            return 1L;
        } catch (DuplicateKeyException ignored) {
            for (int attempt = 0; attempt < 20; attempt++) {
                Long next = jdbc.queryForObject(
                        "SELECT next_sequence FROM project_event_sequence "
                                + "WHERE tenant_id = ? AND project_id = ?",
                        Long.class,
                        tenantId,
                        projectId);
                int updated = jdbc.update(
                        "UPDATE project_event_sequence SET next_sequence = ? "
                                + "WHERE tenant_id = ? AND project_id = ? AND next_sequence = ?",
                        next + 1,
                        tenantId,
                        projectId,
                        next);
                if (updated == 1) {
                    return next;
                }
            }
            throw new IllegalStateException("Could not allocate a project event sequence.");
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
        long sequence = allocateSequence(identity.getTenantId(), projectId);
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
                        + "(id, tenant_id, project_id, conversation_id, message_id, sequence, "
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
                        + "(id, tenant_id, project_id, conversation_id, message_id, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "dispatch-" + UUID.randomUUID(),
                identity.getTenantId(),
                projectId,
                conversationId,
                messageId,
                "PENDING");
    }

    public List<ProjectEvent> findPublicEvents(
            String tenantId, String projectId, long afterSequence) {
        return findPublicEvents(tenantId, projectId, afterSequence, 1000);
    }

    public List<ProjectEvent> findPublicEvents(
            String tenantId, String projectId, long afterSequence, int limit) {
        return jdbc.query(
                "SELECT id, project_id, conversation_id, message_id, sequence, event_type, "
                        + "payload, created_at FROM project_event "
                        + "WHERE tenant_id = ? AND project_id = ? AND visibility = 'PUBLIC' "
                        + "AND sequence > ? ORDER BY sequence LIMIT ?",
                (rs, rowNum) -> mapEvent(rs),
                tenantId,
                projectId,
                afterSequence,
                limit);
    }

    public List<String> findRecentMessageTexts(String tenantId, String projectId, int limit) {
        return jdbc.queryForList(
                "SELECT message_text FROM project_message "
                        + "WHERE tenant_id = ? AND project_id = ? "
                        + "ORDER BY created_at DESC LIMIT ?",
                String.class,
                tenantId,
                projectId,
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
