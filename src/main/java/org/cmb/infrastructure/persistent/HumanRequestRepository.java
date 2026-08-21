package org.cmb.infrastructure.persistent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.cmb.infrastructure.persistent.mapper.CoordinatorDispatchMapper;
import org.cmb.infrastructure.persistent.mapper.CoordinatorPlanMapper;
import org.cmb.infrastructure.persistent.mapper.CoordinatorTaskMapper;
import org.cmb.infrastructure.persistent.mapper.HumanRequestMapper;
import org.cmb.infrastructure.persistent.mapper.ProjectMessageMapper;
import org.cmb.common.enums.HumanDecision;
import org.cmb.common.enums.HumanRequestType;
import org.cmb.application.domain.RequestIdentity;
import org.springframework.stereotype.Repository;

/**
 * Human-in-the-loop persistence facade. Owns JSON serialization; all SQL
 * lives in {@link HumanRequestMapper} and the side-effect statements in
 * {@link CoordinatorTaskMapper}, {@link CoordinatorPlanMapper},
 * {@link CoordinatorDispatchMapper}, {@link ProjectMessageMapper}.
 */
@Repository
public class HumanRequestRepository {

    private final HumanRequestMapper mapper;
    private final CoordinatorTaskMapper taskMapper;
    private final CoordinatorPlanMapper planMapper;
    private final CoordinatorDispatchMapper dispatchMapper;
    private final ProjectMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    public HumanRequestRepository(
            HumanRequestMapper mapper,
            CoordinatorTaskMapper taskMapper,
            CoordinatorPlanMapper planMapper,
            CoordinatorDispatchMapper dispatchMapper,
            ProjectMessageMapper messageMapper,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.taskMapper = taskMapper;
        this.planMapper = planMapper;
        this.dispatchMapper = dispatchMapper;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }

    public String createCoordinatorClarification(
            String analysisId,
            RequestIdentity identity,
            String projectId,
            String question) {
        String id = "human-" + UUID.randomUUID();
        mapper.insertCoordinatorClarification(id, analysisId, identity.getTenantId(),
                projectId, question,
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
        mapper.insertExpertClarification(id, taskId, tenantId, projectId, question,
                agentQuestionId,
                "{\"type\":\"object\",\"minProperties\":1,"
                        + "\"additionalProperties\":{\"type\":\"string\",\"minLength\":1}}",
                Timestamp.from(Instant.now().plusSeconds(86400)));
        taskMapper.markTaskWaitingHumanForRequest(taskId);
        return id;
    }

    public void resumeTask(String tenantId, String taskId) {
        taskMapper.resumeTask(tenantId, taskId);
    }

    public void failTaskAndDispatch(
            String tenantId, String taskId, String status, String error) {
        taskMapper.failTask(status, tenantId, taskId);
        List<String> planIds = taskMapper.findPlanIdForTask(tenantId, taskId);
        if (planIds.isEmpty()) {
            return;
        }
        planMapper.failPlan(status, planIds.get(0));
        dispatchMapper.failDispatch(status, error, tenantId, planIds.get(0));
    }

    public List<HumanRequestRecord> findExpiredPending() {
        return mapper.findExpiredPending();
    }

    public HumanRequestRecord find(String tenantId, String projectId, String id) {
        List<HumanRequestRecord> rows = mapper.find(tenantId, projectId, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public HumanRequestRecord findPendingForTask(
            String tenantId, String projectId, String taskId) {
        List<HumanRequestRecord> rows = mapper.findPendingForTask(
                tenantId, projectId, taskId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int resolve(
            String tenantId,
            String id,
            HumanDecision decision,
            JsonNode response,
            String idempotencyKey,
            String userId) {
        return mapper.resolve(tenantId, id, decision.name(), write(response),
                idempotencyKey, userId);
    }

    public void linkDispatch(String id, String messageId, String dispatchId) {
        mapper.linkDispatch(id, messageId, dispatchId);
    }

    public void expire(String tenantId, String id) {
        mapper.expire(tenantId, id);
    }

    public void resumeCoordinatorDispatch(
            String tenantId, String messageId, String dispatchId, String answer) {
        messageMapper.appendMessageText(answer, messageId, tenantId);
        dispatchMapper.resetDispatchPending(dispatchId, tenantId);
    }

    private String write(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not write human response.", ex);
        }
    }

    public static final class HumanRequestRecord {
        public String id;
        public String tenantId;
        public String analysisId;
        public String taskId;
        public String messageId;
        public String dispatchId;
        public String projectId;
        public HumanRequestType type;
        public String question;
        public String agentQuestionId;
        public String allowedRoles;
        public String inputSchema;
        public String status;
        public HumanDecision decision;
        public JsonNode response;
        public String responseIdempotencyKey;
        public Instant expiresAt;
    }
}
