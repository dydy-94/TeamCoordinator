package org.cmb.teamcoordinator.human;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.cmb.teamcoordinator.project.RequestIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HumanRequestRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public HumanRequestRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public String createCoordinatorClarification(
            String analysisId,
            RequestIdentity identity,
            String projectId,
            String question) {
        String id = "human-" + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO human_request "
                        + "(business_id, analysis_id, tenant_id, project_id, request_type, question, "
                        + "allowed_roles, input_schema, status, expires_at) "
                        + "VALUES (?, ?, ?, ?, 'CLARIFICATION', ?, 'OWNER,MEMBER', ?, "
                        + "'PENDING', ?)",
                id, analysisId, identity.getTenantId(), projectId, question,
                "{\"type\":\"object\",\"required\":[\"answer\"],\"properties\":"
                        + "{\"answer\":{\"type\":\"string\",\"minLength\":1}},"
                        + "\"additionalProperties\":false}",
                Timestamp.from(Instant.now().plusSeconds(86400)));
        return id;
    }

    public String createExpertClarification(
            String tenantId,
            String projectId,
            String taskId,
            String agentQuestionId,
            String question) {
        String id = "human-" + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO human_request "
                        + "(business_id, task_id, tenant_id, project_id, request_type, question, "
                        + "agent_question_id, "
                        + "allowed_roles, input_schema, status, expires_at) "
                        + "VALUES (?, ?, ?, ?, 'CLARIFICATION', ?, ?, 'OWNER,MEMBER', ?, "
                        + "'PENDING', ?)",
                id, taskId, tenantId, projectId, question, agentQuestionId,
                "{\"type\":\"object\",\"minProperties\":1,"
                        + "\"additionalProperties\":{\"type\":\"string\",\"minLength\":1}}",
                Timestamp.from(Instant.now().plusSeconds(86400)));
        jdbc.update(
                "UPDATE coordinator_task SET status = 'WAITING_HUMAN', "
                        + "updated_at = CURRENT_TIMESTAMP WHERE business_id = ?",
                taskId);
        return id;
    }

    public void resumeTask(String tenantId, String taskId) {
        jdbc.update(
                "UPDATE coordinator_task SET status = 'RUNNING', "
                        + "updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ? AND business_id = ? "
                        + "AND status = 'WAITING_HUMAN'",
                tenantId, taskId);
    }

    public void failTaskAndDispatch(
            String tenantId, String taskId, String status, String error) {
        jdbc.update(
                "UPDATE coordinator_task SET status = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE tenant_id = ? AND business_id = ? AND status = 'WAITING_HUMAN'",
                status, tenantId, taskId);
        List<String> planIds = jdbc.queryForList(
                "SELECT plan_id FROM coordinator_task WHERE tenant_id = ? AND business_id = ?",
                String.class, tenantId, taskId);
        if (planIds.isEmpty()) {
            return;
        }
        jdbc.update(
                "UPDATE coordinator_plan SET status = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE business_id = ?",
                status, planIds.get(0));
        jdbc.update(
                "UPDATE coordinator_dispatch SET status = ?, last_error = ?, "
                        + "lease_owner = NULL, lease_expires_at = NULL, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ? AND message_id = "
                        + "(SELECT message_id FROM coordinator_plan WHERE business_id = ?)",
                status, error, tenantId, planIds.get(0));
    }

    public List<HumanRequestRecord> findExpiredPending() {
        return jdbc.query(
                "SELECT * FROM human_request WHERE status = 'PENDING' "
                        + "AND expires_at <= CURRENT_TIMESTAMP",
                (rs, rowNum) -> map(rs));
    }

    public HumanRequestRecord find(String tenantId, String projectId, String id) {
        List<HumanRequestRecord> rows = jdbc.query(
                "SELECT * FROM human_request WHERE tenant_id = ? AND project_id = ? AND business_id = ?",
                (rs, rowNum) -> map(rs),
                tenantId, projectId, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int resolve(
            String tenantId,
            String id,
            HumanDecision decision,
            JsonNode response,
            String idempotencyKey,
            String userId) {
        return jdbc.update(
                "UPDATE human_request SET status = 'RESOLVED', decision = ?, response_json = ?, "
                        + "response_idempotency_key = ?, responded_by = ?, "
                        + "resolved_at = CURRENT_TIMESTAMP WHERE tenant_id = ? AND business_id = ? "
                        + "AND status = 'PENDING' AND (expires_at IS NULL "
                        + "OR expires_at > CURRENT_TIMESTAMP)",
                decision.name(), write(response), idempotencyKey, userId, tenantId, id);
    }

    public void linkDispatch(String id, String messageId, String dispatchId) {
        jdbc.update(
                "UPDATE human_request SET message_id = ?, dispatch_id = ? WHERE business_id = ?",
                messageId, dispatchId, id);
    }

    public void expire(String tenantId, String id) {
        jdbc.update(
                "UPDATE human_request SET status = 'EXPIRED', resolved_at = CURRENT_TIMESTAMP "
                        + "WHERE tenant_id = ? AND business_id = ? AND status = 'PENDING'",
                tenantId, id);
    }

    public void resumeCoordinatorDispatch(
            String tenantId, String messageId, String dispatchId, String answer) {
        jdbc.update(
                "UPDATE project_message SET message_text = CONCAT(message_text, "
                        + "'\\nHuman clarification: ', ?) WHERE business_id = ? AND tenant_id = ?",
                answer, messageId, tenantId);
        jdbc.update(
                "UPDATE coordinator_dispatch SET status = 'PENDING', "
                        + "available_at = CURRENT_TIMESTAMP, lease_owner = NULL, "
                        + "lease_expires_at = NULL WHERE business_id = ? AND tenant_id = ?",
                dispatchId, tenantId);
    }

    private JsonNode read(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read human response.", ex);
        }
    }

    private HumanRequestRecord map(java.sql.ResultSet rs)
            throws java.sql.SQLException {
        HumanRequestRecord record = new HumanRequestRecord();
        record.id = rs.getString("business_id");
        record.tenantId = rs.getString("tenant_id");
        record.analysisId = rs.getString("analysis_id");
        record.taskId = rs.getString("task_id");
        record.messageId = rs.getString("message_id");
        record.dispatchId = rs.getString("dispatch_id");
        record.projectId = rs.getString("project_id");
        record.type = HumanRequestType.valueOf(rs.getString("request_type"));
        record.question = rs.getString("question");
        record.agentQuestionId = rs.getString("agent_question_id");
        record.allowedRoles = rs.getString("allowed_roles");
        record.inputSchema = rs.getString("input_schema");
        record.status = rs.getString("status");
        String decision = rs.getString("decision");
        record.decision = decision == null ? null : HumanDecision.valueOf(decision);
        record.response = read(rs.getString("response_json"));
        record.responseIdempotencyKey = rs.getString("response_idempotency_key");
        Timestamp expires = rs.getTimestamp("expires_at");
        record.expiresAt = expires == null ? null : expires.toInstant();
        return record;
    }

    private String write(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not write human response.", ex);
        }
    }

    static final class HumanRequestRecord {
        String id;
        String tenantId;
        String analysisId;
        String taskId;
        String messageId;
        String dispatchId;
        String projectId;
        HumanRequestType type;
        String question;
        String agentQuestionId;
        String allowedRoles;
        String inputSchema;
        String status;
        HumanDecision decision;
        JsonNode response;
        String responseIdempotencyKey;
        Instant expiresAt;
    }
}
