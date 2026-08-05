package org.cmb.teamcoordinator.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.cmb.teamcoordinator.agentcore.AgentEvent;
import org.cmb.teamcoordinator.intent.CoordinatorDecision;
import org.cmb.teamcoordinator.planning.CoordinatorPlanSpec;
import org.cmb.teamcoordinator.planning.PlannedTask;
import org.cmb.teamcoordinator.planning.PlanningResult;
import org.springframework.dao.DuplicateKeyException;
import org.cmb.teamcoordinator.persistence.MyBatisExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ExecutionRepository {

    private final MyBatisExecutor jdbc;
    private final ObjectMapper objectMapper;

    public ExecutionRepository(MyBatisExecutor jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DispatchWork claimNext(String owner, int leaseSeconds) {
        List<String> ids = jdbc.queryForList(
                "SELECT d.business_id FROM coordinator_dispatch d "
                        + "WHERE d.status IN ('PENDING', 'RUNNING') "
                        + "AND d.available_at <= CURRENT_TIMESTAMP "
                        + "AND (d.lease_expires_at IS NULL "
                        + "OR d.lease_expires_at < CURRENT_TIMESTAMP) "
                        + "AND NOT EXISTS (SELECT 1 FROM coordinator_dispatch older "
                        + "WHERE older.tenant_id = d.tenant_id "
                        + "AND older.project_id = d.project_id "
                        + "AND older.conversation_id = d.conversation_id "
                        + "AND older.status IN ('PENDING', 'RUNNING') "
                        + "AND (older.created_at < d.created_at "
                        + "OR (older.created_at = d.created_at AND older.id < d.id))) "
                        + "ORDER BY d.created_at LIMIT 1",
                String.class);
        if (ids.isEmpty()) {
            return null;
        }
        String id = ids.get(0);
        int updated = jdbc.update(
                "UPDATE coordinator_dispatch SET status = 'RUNNING', lease_owner = ?, "
                        + "lease_expires_at = ?, attempt_count = attempt_count + 1, "
                        + "updated_at = CURRENT_TIMESTAMP "
                        + "WHERE business_id = ? AND (lease_expires_at IS NULL "
                        + "OR lease_expires_at < CURRENT_TIMESTAMP)",
                owner,
                Timestamp.from(Instant.now().plusSeconds(leaseSeconds)),
                id);
        return updated == 1 ? loadWork(id) : null;
    }

    public DispatchWork loadWork(String dispatchId) {
        return jdbc.queryForObject(
                "SELECT d.business_id dispatch_id, d.tenant_id, d.project_id, d.conversation_id, "
                        + "d.message_id, m.user_id, m.message_text, m.attachment_refs, "
                        + "c.session_id business_session_id, "
                        + "c.coordinator_session_id "
                        + "FROM coordinator_dispatch d JOIN project_message m ON m.business_id = d.message_id "
                        + "JOIN project_conversation c ON c.business_id = d.conversation_id "
                        + "WHERE d.business_id = ?",
                (rs, rowNum) -> {
                    DispatchWork work = new DispatchWork();
                    work.setDispatchId(rs.getString("dispatch_id"));
                    work.setTenantId(rs.getString("tenant_id"));
                    work.setProjectId(rs.getString("project_id"));
                    work.setConversationId(rs.getString("conversation_id"));
                    work.setBusinessSessionId(rs.getString("business_session_id"));
                    work.setCoordinatorSessionId(rs.getString("coordinator_session_id"));
                    work.setMessageId(rs.getString("message_id"));
                    work.setUserId(rs.getString("user_id"));
                    work.setText(rs.getString("message_text"));
                    work.setAttachmentRefs(readList(rs.getString("attachment_refs")));
                    return work;
                },
                dispatchId);
    }

    public String createPlan(DispatchWork work, CoordinatorDecision decision) {
        List<String> existing = jdbc.queryForList(
                "SELECT business_id AS id FROM coordinator_plan WHERE tenant_id = ? AND project_id = ? "
                        + "AND message_id = ?",
                String.class,
                work.getTenantId(),
                work.getProjectId(),
                work.getMessageId());
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        String id = "plan-" + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO coordinator_plan "
                        + "(business_id, tenant_id, project_id, conversation_id, message_id, analysis_id, "
                        + "status, intent_json) VALUES (?, ?, ?, ?, ?, ?, 'RUNNING', ?)",
                id,
                work.getTenantId(),
                work.getProjectId(),
                work.getConversationId(),
                work.getMessageId(),
                decision.getAnalysisId(),
                write(decision.getTaskIntent()));
        return id;
    }

    @Transactional
    public String createPlan(
            DispatchWork work,
            CoordinatorDecision decision,
            PlanningResult planning) {
        List<String> existing = jdbc.queryForList(
                "SELECT business_id AS id FROM coordinator_plan WHERE tenant_id = ? AND project_id = ? "
                        + "AND message_id = ? ORDER BY plan_version DESC",
                String.class,
                work.getTenantId(),
                work.getProjectId(),
                work.getMessageId());
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        CoordinatorPlanSpec plan = planning.getPlan();
        String id = "plan-" + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO coordinator_plan "
                        + "(business_id, tenant_id, project_id, conversation_id, message_id, analysis_id, "
                        + "plan_version, status, intent_json, plan_json, repair_count) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'RUNNING', ?, ?, ?)",
                id,
                work.getTenantId(),
                work.getProjectId(),
                work.getConversationId(),
                work.getMessageId(),
                decision.getAnalysisId(),
                plan.getPlanVersion(),
                write(decision.getTaskIntent()),
                planning.getRawJson(),
                planning.getRepairCount());
        for (PlannedTask task : plan.getTasks()) {
            insertTask(work, id, task);
        }
        return id;
    }

    @Transactional
    public String createReplan(
            DispatchWork work,
            CoordinatorDecision decision,
            PlanningResult planning,
            String previousPlanId) {
        CoordinatorPlanSpec plan = planning.getPlan();
        String id = "plan-" + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO coordinator_plan "
                        + "(business_id, tenant_id, project_id, conversation_id, message_id, analysis_id, "
                        + "plan_version, status, intent_json, plan_json, repair_count, "
                        + "supersedes_plan_id) VALUES (?, ?, ?, ?, ?, ?, ?, 'RUNNING', ?, ?, ?, ?)",
                id, work.getTenantId(), work.getProjectId(), work.getConversationId(),
                work.getMessageId(), decision.getAnalysisId(), plan.getPlanVersion(),
                write(decision.getTaskIntent()), planning.getRawJson(),
                planning.getRepairCount(), previousPlanId);
        for (PlannedTask task : plan.getTasks()) {
            TaskRecord reusable = findReusableTask(previousPlanId, task.getTaskKey());
            if (reusable == null) {
                insertTask(work, id, task);
            } else {
                insertReusedTask(work, id, task, reusable);
            }
        }
        jdbc.update(
                "UPDATE coordinator_plan SET status = 'SUPERSEDED', "
                        + "updated_at = CURRENT_TIMESTAMP WHERE business_id = ?",
                previousPlanId);
        return id;
    }

    public List<TaskRecord> findTasksForMessage(DispatchWork work) {
        return jdbc.query(
                taskSelect() + " FROM coordinator_task t "
                        + "JOIN coordinator_plan p ON p.business_id = t.plan_id "
                        + "WHERE p.tenant_id = ? AND p.message_id = ? "
                        + "AND p.status = 'RUNNING' ORDER BY t.created_at, t.task_key",
                (rs, rowNum) -> mapTask(rs),
                work.getTenantId(),
                work.getMessageId());
    }

    public boolean assignExpert(String taskId, String expertId) {
        return jdbc.update(
                "UPDATE coordinator_task SET expert_id = ?, status = 'STARTING', "
                        + "updated_at = CURRENT_TIMESTAMP "
                        + "WHERE business_id = ? AND status = 'PENDING'",
                expertId,
                taskId) == 1;
    }

    public int activeTaskCount(String expertId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM coordinator_task WHERE expert_id = ? "
                        + "AND status IN ('STARTING', 'RUNNING')",
                Integer.class,
                expertId);
        return count == null ? 0 : count;
    }

    @Transactional
    public TaskRecord createCorrection(DispatchWork work, TaskRecord original) {
        if (original.getCorrectionCount() >= 1) {
            return null;
        }
        int updated = jdbc.update(
                "UPDATE coordinator_task SET status = 'CORRECTING', correction_count = 1, "
                        + "result_accepted = FALSE, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE business_id = ? AND correction_count = 0",
                original.getId());
        if (updated != 1) {
            return null;
        }
        String id = "task-" + UUID.randomUUID();
        String key = original.getTaskKey() + "-correction";
        String requestId = original.getRequestId() + ":correction-1";
        jdbc.update(
                "INSERT INTO coordinator_task "
                        + "(business_id, tenant_id, project_id, plan_id, task_key, request_id, expert_id, "
                        + "status, objective, attachment_refs, dependencies, "
                        + "required_capabilities, expected_output, acceptance_criteria, "
                        + "correction_of, correction_count) "
                        + "VALUES (?, ?, ?, ?, ?, ?, '', 'PENDING', ?, ?, ?, ?, ?, ?, ?, 1)",
                id,
                work.getTenantId(),
                work.getProjectId(),
                original.getPlanId(),
                key,
                requestId,
                "Correct the previous result. Return a non-empty resultText for: "
                        + original.getExpectedOutput(),
                write(work.getAttachmentRefs()),
                write(original.getDependencies()),
                write(original.getRequiredCapabilities()),
                original.getExpectedOutput(),
                original.getAcceptanceCriteria(),
                original.getId());
        return findTask(work.getTenantId(), work.getProjectId(), id);
    }

    public void acceptCorrection(TaskRecord correction, String resultJson) {
        jdbc.update(
                "UPDATE coordinator_task SET status = 'SUCCEEDED', result_json = ?, "
                        + "result_accepted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE business_id = ?",
                resultJson,
                correction.getCorrectionOf());
    }

    public TaskRecord createOrLoadTask(
            DispatchWork work, String planId, String expertId, String objective) {
        String requestId = work.getMessageId() + ":single-expert";
        List<TaskRecord> existing = jdbc.query(
                "SELECT id AS database_id, business_id, plan_id, request_id, expert_id, session_id, status, last_sequence "
                        + "FROM coordinator_task WHERE tenant_id = ? AND request_id = ?",
                (rs, rowNum) -> mapTask(rs),
                work.getTenantId(),
                requestId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        String taskId = "task-" + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO coordinator_task "
                        + "(business_id, tenant_id, project_id, plan_id, task_key, request_id, expert_id, "
                        + "status, objective, attachment_refs) "
                        + "VALUES (?, ?, ?, ?, 'single-task', ?, ?, 'PENDING', ?, ?)",
                taskId,
                work.getTenantId(),
                work.getProjectId(),
                planId,
                requestId,
                expertId,
                objective,
                write(work.getAttachmentRefs()));
        TaskRecord task = new TaskRecord();
        task.setId(taskId);
        task.setPlanId(planId);
        task.setRequestId(requestId);
        task.setExpertId(expertId);
        task.setStatus("PENDING");
        return task;
    }

    public TaskRecord findTaskForMessage(DispatchWork work) {
        List<TaskRecord> rows = jdbc.query(
                "SELECT t.id AS database_id, t.business_id, t.plan_id, t.request_id, t.expert_id, t.session_id, "
                        + "t.status, t.last_sequence FROM coordinator_task t "
                        + "JOIN coordinator_plan p ON p.business_id = t.plan_id "
                        + "WHERE p.tenant_id = ? AND p.message_id = ?",
                (rs, rowNum) -> mapTask(rs),
                work.getTenantId(),
                work.getMessageId());
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void saveSession(String taskId, String sessionId) {
        jdbc.update(
                "UPDATE coordinator_task SET session_id = ?, status = 'RUNNING', "
                        + "updated_at = CURRENT_TIMESTAMP WHERE business_id = ? AND session_id IS NULL",
                sessionId,
                taskId);
    }

    public void replaceSession(String taskId, String sessionId) {
        jdbc.update(
                "UPDATE coordinator_task SET session_id = ?, status = 'RUNNING', "
                        + "last_sequence = 0, updated_at = CURRENT_TIMESTAMP WHERE business_id = ? "
                        + "AND status = 'WAITING_HUMAN'",
                sessionId, taskId);
    }

    public String findExpertSession(
            String tenantId, String projectId, String conversationId,
            String expertId, String currentMessageId) {
        // Only return session from a DIFFERENT message to avoid
        // parallel tasks within the same plan sharing an expert session.
        List<String> rows = jdbc.queryForList(
                "SELECT session_id FROM project_conversation_expert_session "
                        + "WHERE tenant_id = ? AND project_id = ? "
                        + "AND conversation_id = ? AND expert_id = ? "
                        + "AND message_id <> ?",
                String.class, tenantId, projectId, conversationId,
                expertId, currentMessageId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void saveExpertSession(
            String tenantId, String projectId, String conversationId,
            String expertId, String sessionId, String messageId) {
        jdbc.update(
                "INSERT INTO project_conversation_expert_session "
                        + "(id, tenant_id, project_id, conversation_id, expert_id, "
                        + "session_id, message_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE session_id = VALUES(session_id), "
                        + "message_id = VALUES(message_id)",
                "exp-session-" + UUID.randomUUID(), tenantId, projectId,
                conversationId, expertId, sessionId, messageId);
    }

    public void saveCoordinatorSession(String conversationId, String sessionId) {
        jdbc.update(
                "UPDATE project_conversation SET coordinator_session_id = ? "
                        + "WHERE business_id = ? "
                        + "AND coordinator_session_id IS NULL",
                sessionId, conversationId);
    }

    @Transactional
    public boolean recordEvent(String tenantId, String taskId, AgentEvent event) {
        try {
            jdbc.update(
                    "INSERT INTO coordinator_task_event "
                            + "(business_id, tenant_id, task_id, event_id, sequence, "
                            + "event_type, payload) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    "task-event-" + UUID.randomUUID(),
                    tenantId,
                    taskId,
                    event.getEventId(),
                    event.getSequence(),
                    event.getType(),
                    write(event));
        } catch (DuplicateKeyException ex) {
            return false;
        }
        return true;
    }

    public boolean advanceTask(
            String taskId, long sequence, String status, String resultJson) {
        return jdbc.update(
                "UPDATE coordinator_task SET status = ?, last_sequence = ?, result_json = ?, "
                        + "result_accepted = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE business_id = ? AND last_sequence < ? "
                        + "AND status NOT IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT')",
                status,
                sequence,
                resultJson,
                "SUCCEEDED".equals(status),
                taskId,
                sequence) == 1;
    }

    public void completePlanAndDispatch(
            String planId, String dispatchId, String status, String error) {
        jdbc.update(
                "UPDATE coordinator_plan SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE business_id = ?",
                status,
                planId);
        completeDispatch(dispatchId, status, error);
    }

    public int failTasksForMessage(String tenantId, String messageId) {
        return jdbc.update(
                "UPDATE coordinator_task t JOIN coordinator_plan p "
                        + "ON p.business_id = t.plan_id "
                        + "SET t.status = 'FAILED', t.updated_at = CURRENT_TIMESTAMP "
                        + "WHERE p.tenant_id = ? AND p.message_id = ? "
                        + "AND t.status IN ('RUNNING', 'STARTING', 'PENDING')",
                tenantId, messageId);
    }

    public void completeDispatch(String dispatchId, String status, String error) {
        jdbc.update(
                "UPDATE coordinator_dispatch SET status = ?, last_error = ?, lease_owner = NULL, "
                        + "lease_expires_at = NULL, updated_at = CURRENT_TIMESTAMP WHERE business_id = ?",
                status,
                error,
                dispatchId);
    }

    public void releaseDispatch(String dispatchId) {
        jdbc.update(
                "UPDATE coordinator_dispatch SET lease_owner = NULL, lease_expires_at = NULL, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE business_id = ? AND status = 'RUNNING'",
                dispatchId);
    }

    public TaskRecord findTask(String tenantId, String projectId, String taskId) {
        List<TaskRecord> rows = jdbc.query(
                "SELECT id AS database_id, business_id, plan_id, request_id, expert_id, session_id, status, last_sequence "
                        + "FROM coordinator_task WHERE tenant_id = ? AND project_id = ? AND business_id = ?",
                (rs, rowNum) -> mapTask(rs),
                tenantId,
                projectId,
                taskId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public DispatchWork loadWorkForTask(String tenantId, String projectId, String taskId) {
        List<String> dispatchIds = jdbc.queryForList(
                "SELECT d.business_id FROM coordinator_task t "
                        + "JOIN coordinator_plan p ON p.business_id = t.plan_id "
                        + "JOIN coordinator_dispatch d ON d.message_id = p.message_id "
                        + "AND d.tenant_id = p.tenant_id "
                        + "WHERE t.tenant_id = ? AND t.project_id = ? AND t.business_id = ?",
                String.class,
                tenantId,
                projectId,
                taskId);
        return dispatchIds.isEmpty() ? null : loadWork(dispatchIds.get(0));
    }

    private TaskRecord mapTask(org.cmb.teamcoordinator.persistence.MyBatisRow rs) {
        TaskRecord task = new TaskRecord();
        task.setDatabaseId(rs.getLong("database_id"));
        task.setBusinessId(rs.getString("business_id"));
        task.setPlanId(rs.getString("plan_id"));
        task.setTaskKey(column(rs, "task_key"));
        task.setRequestId(rs.getString("request_id"));
        task.setExpertId(rs.getString("expert_id"));
        task.setSessionId(rs.getString("session_id"));
        task.setStatus(rs.getString("status"));
        task.setObjective(column(rs, "objective"));
        task.setExpectedOutput(column(rs, "expected_output"));
        task.setAcceptanceCriteria(column(rs, "acceptance_criteria"));
        task.setResultJson(column(rs, "result_json"));
        task.setCorrectionOf(column(rs, "correction_of"));
        task.setCorrectionCount(intColumn(rs, "correction_count"));
        task.setDependencies(readList(column(rs, "dependencies")));
        task.setRequiredCapabilities(readList(column(rs, "required_capabilities")));
        task.setLastSequence(rs.getLong("last_sequence"));
        return task;
    }

    private void insertTask(DispatchWork work, String planId, PlannedTask task) {
        String taskId = "task-" + UUID.randomUUID();
        String requestId = work.getMessageId() + ":" + task.getTaskKey();
        jdbc.update(
                "INSERT INTO coordinator_task "
                        + "(business_id, tenant_id, project_id, plan_id, task_key, request_id, expert_id, status, "
                        + "objective, attachment_refs, dependencies, required_capabilities, "
                        + "expected_output, acceptance_criteria) "
                        + "VALUES (?, ?, ?, ?, ?, ?, '', 'PENDING', ?, ?, ?, ?, ?, ?)",
                taskId,
                work.getTenantId(),
                work.getProjectId(),
                planId,
                task.getTaskKey(),
                requestId,
                task.getObjective(),
                write(work.getAttachmentRefs()),
                write(task.getDependencies()),
                write(task.getRequiredCapabilities()),
                task.getExpectedOutput(),
                task.getAcceptanceCriteria());
    }

    private TaskRecord findReusableTask(String planId, String taskKey) {
        List<TaskRecord> rows = jdbc.query(
                taskSelect() + " FROM coordinator_task t WHERE t.plan_id = ? "
                        + "AND t.task_key = ? AND t.status = 'SUCCEEDED'",
                (rs, rowNum) -> mapTask(rs), planId, taskKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void insertReusedTask(
            DispatchWork work,
            String planId,
            PlannedTask task,
            TaskRecord reusable) {
        jdbc.update(
                "INSERT INTO coordinator_task "
                        + "(business_id, tenant_id, project_id, plan_id, task_key, request_id, expert_id, "
                        + "session_id, status, objective, attachment_refs, result_json, "
                        + "last_sequence, dependencies, required_capabilities, expected_output, "
                        + "acceptance_criteria, result_accepted, reused_from_task_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SUCCEEDED', ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "TRUE, ?)",
                "task-" + UUID.randomUUID(), work.getTenantId(), work.getProjectId(), planId,
                task.getTaskKey(), work.getMessageId() + ":v2:" + task.getTaskKey(),
                reusable.getExpertId(), reusable.getSessionId(), task.getObjective(),
                write(work.getAttachmentRefs()), reusable.getResultJson(),
                reusable.getLastSequence(), write(task.getDependencies()),
                write(task.getRequiredCapabilities()), task.getExpectedOutput(),
                task.getAcceptanceCriteria(), reusable.getId());
    }

    private String taskSelect() {
        return "SELECT t.id AS database_id, t.business_id, t.plan_id, t.task_key, t.request_id, t.expert_id, "
                + "t.session_id, t.status, t.objective, t.expected_output, "
                + "t.acceptance_criteria, t.result_json, t.correction_of, "
                + "t.correction_count, t.dependencies, t.required_capabilities, "
                + "t.last_sequence";
    }

    private String column(org.cmb.teamcoordinator.persistence.MyBatisRow rs, String name) {
        return rs.getString(name);
    }

    private int intColumn(org.cmb.teamcoordinator.persistence.MyBatisRow rs, String name) {
        return rs.getInt(name);
    }

    private List<String> readList(String json) {
        if (json == null) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("Could not parse attachment references.", ex);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize execution state.", ex);
        }
    }
}
