package org.cmb.infrastructure.persistent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.cmb.infrastructure.persistent.mapper.CoordinatorDispatchMapper;
import org.cmb.infrastructure.persistent.mapper.CoordinatorPlanMapper;
import org.cmb.infrastructure.persistent.mapper.CoordinatorTaskEventMapper;
import org.cmb.infrastructure.persistent.mapper.CoordinatorTaskMapper;
import org.cmb.infrastructure.persistent.mapper.ProjectConversationExpertSessionMapper;
import org.cmb.infrastructure.persistent.mapper.ProjectConversationMapper;
import org.cmb.application.domain.AgentEvent;
import org.cmb.application.domain.DispatchWork;
import org.cmb.application.domain.TaskRecord;
import org.cmb.application.domain.CoordinatorDecision;
import org.cmb.application.domain.CoordinatorPlanSpec;
import org.cmb.application.domain.PlannedTask;
import org.cmb.application.domain.PlanningResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Execution-engine persistence facade. Owns transaction boundaries and
 * JSON serialization; all SQL lives in the per-table mappers
 * ({@link CoordinatorDispatchMapper}, {@link CoordinatorPlanMapper},
 * {@link CoordinatorTaskMapper}, {@link CoordinatorTaskEventMapper},
 * {@link ProjectConversationMapper},
 * {@link ProjectConversationExpertSessionMapper}).
 */
@Repository
public class ExecutionRepository {

    private final CoordinatorDispatchMapper dispatchMapper;
    private final CoordinatorPlanMapper planMapper;
    private final CoordinatorTaskMapper taskMapper;
    private final CoordinatorTaskEventMapper taskEventMapper;
    private final ProjectConversationMapper conversationMapper;
    private final ProjectConversationExpertSessionMapper expertSessionMapper;
    private final ObjectMapper objectMapper;

    public ExecutionRepository(
            CoordinatorDispatchMapper dispatchMapper,
            CoordinatorPlanMapper planMapper,
            CoordinatorTaskMapper taskMapper,
            CoordinatorTaskEventMapper taskEventMapper,
            ProjectConversationMapper conversationMapper,
            ProjectConversationExpertSessionMapper expertSessionMapper,
            ObjectMapper objectMapper) {
        this.dispatchMapper = dispatchMapper;
        this.planMapper = planMapper;
        this.taskMapper = taskMapper;
        this.taskEventMapper = taskEventMapper;
        this.conversationMapper = conversationMapper;
        this.expertSessionMapper = expertSessionMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DispatchWork claimNext(String owner, int leaseSeconds) {
        List<String> ids = dispatchMapper.selectClaimableDispatchId();
        if (ids.isEmpty()) {
            return null;
        }
        String id = ids.get(0);
        int updated = dispatchMapper.claimDispatch(owner,
                Timestamp.from(Instant.now().plusSeconds(leaseSeconds)), id);
        return updated == 1 ? loadWork(id) : null;
    }

    /**
     * Extend the dispatch lease while {@code process()} is still running.
     * The lease owner check makes a stale heartbeat harmless: once the
     * dispatch is released (lease_owner = NULL) or completed, renewal
     * becomes a no-op and another instance may claim it.
     */
    public int renewLease(String dispatchId, String owner, int leaseSeconds) {
        return dispatchMapper.renewLease(dispatchId, owner,
                Timestamp.from(Instant.now().plusSeconds(leaseSeconds)));
    }

    public DispatchWork loadWork(String dispatchId) {
        List<DispatchWork> rows = dispatchMapper.loadWork(dispatchId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public String createPlan(DispatchWork work, CoordinatorDecision decision) {
        List<String> existing = planMapper.findExistingPlanId(
                work.getTenantId(), work.getProjectId(), work.getMessageId());
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        String id = "plan-" + UUID.randomUUID();
        planMapper.insertPlanSimple(id, work, decision.getAnalysisId(),
                write(decision.getTaskIntent()));
        return id;
    }

    @Transactional
    public String createPlan(
            DispatchWork work,
            CoordinatorDecision decision,
            PlanningResult planning) {
        List<String> existing = planMapper.findLatestPlanId(
                work.getTenantId(), work.getProjectId(), work.getMessageId());
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        CoordinatorPlanSpec plan = planning.getPlan();
        String id = "plan-" + UUID.randomUUID();
        planMapper.insertPlanFull(id, work, decision.getAnalysisId(),
                plan.getPlanVersion(), write(decision.getTaskIntent()),
                planning.getRawJson(), planning.getRepairCount());
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
        planMapper.insertReplan(id, work, decision.getAnalysisId(), plan.getPlanVersion(),
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
        planMapper.supersedePlan(previousPlanId);
        return id;
    }

    public List<TaskRecord> findTasksForMessage(DispatchWork work) {
        return taskMapper.findTasksForMessage(work.getTenantId(), work.getMessageId());
    }

    public boolean assignExpert(String taskId, String expertId) {
        return taskMapper.assignExpert(taskId, expertId) == 1;
    }

    public int activeTaskCount(String expertId) {
        Integer count = taskMapper.countActiveTasks(expertId);
        return count == null ? 0 : count;
    }

    @Transactional
    public TaskRecord createCorrection(
            DispatchWork work, TaskRecord original, String reason) {
        if (original.getCorrectionCount() >= 1) {
            return null;
        }
        int updated = taskMapper.markCorrection(original.getId());
        if (updated != 1) {
            return null;
        }
        String id = "task-" + UUID.randomUUID();
        String key = original.getTaskKey() + "-correction";
        String requestId = original.getRequestId() + ":correction-1";
        taskMapper.insertCorrectionTask(id, work, original.getPlanId(), key, requestId,
                "Correct the previous result. Return a non-empty resultText for: "
                        + original.getExpectedOutput(),
                write(work.getAttachmentRefs()),
                write(original.getDependencies()),
                write(original.getRequiredCapabilities()),
                original.getExpectedOutput(),
                correctionCriteria(original, reason),
                original.getId());
        return findTask(work.getTenantId(), work.getProjectId(), id);
    }

    /**
     * The correction task's acceptance criteria carry the original criteria
     * plus the failure reason, so the expert sees why the result was rejected
     * without the free-text reason leaking into the objective (task text).
     */
    private String correctionCriteria(TaskRecord original, String reason) {
        String criteria = original.getAcceptanceCriteria() == null
                ? "" : original.getAcceptanceCriteria();
        if (reason != null && !reason.trim().isEmpty()) {
            criteria = criteria.trim().isEmpty()
                    ? "Correction reason: " + reason
                    : criteria + "\nCorrection reason: " + reason;
        }
        return criteria;
    }

    public void acceptCorrection(TaskRecord correction, String resultJson) {
        taskMapper.acceptCorrection(correction.getCorrectionOf(), resultJson);
    }

    public TaskRecord createOrLoadTask(
            DispatchWork work, String planId, String expertId, String objective) {
        String requestId = work.getMessageId() + ":single-expert";
        List<TaskRecord> existing = taskMapper.findTaskByRequestId(
                work.getTenantId(), requestId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        String taskId = "task-" + UUID.randomUUID();
        taskMapper.insertSingleExpertTask(taskId, work, planId, requestId, expertId,
                objective, write(work.getAttachmentRefs()));
        TaskRecord task = new TaskRecord();
        task.setId(taskId);
        task.setPlanId(planId);
        task.setRequestId(requestId);
        task.setExpertId(expertId);
        task.setStatus("PENDING");
        return task;
    }

    public TaskRecord findTaskForMessage(DispatchWork work) {
        List<TaskRecord> rows = taskMapper.findTaskForMessage(
                work.getTenantId(), work.getMessageId());
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void saveSession(String taskId, String sessionId) {
        taskMapper.saveSession(taskId, sessionId);
    }

    public void replaceSession(String taskId, String sessionId) {
        taskMapper.replaceSession(taskId, sessionId);
    }

    public String findExpertSession(
            String tenantId, String projectId, String conversationId,
            String expertId, String currentMessageId) {
        // Only return session from a DIFFERENT message to avoid
        // parallel tasks within the same plan sharing an expert session.
        List<String> rows = expertSessionMapper.findExpertSession(
                tenantId, projectId, conversationId, expertId, currentMessageId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void saveExpertSession(
            String tenantId, String projectId, String conversationId,
            String expertId, String sessionId, String messageId) {
        expertSessionMapper.upsertExpertSession("exp-session-" + UUID.randomUUID(), tenantId,
                projectId, conversationId, expertId, sessionId, messageId);
    }

    public void saveCoordinatorSession(
            String conversationId, String sessionId, String agentId) {
        conversationMapper.saveCoordinatorSession(conversationId, sessionId, agentId);
    }

    public String loadCoordinatorAgent(String conversationId) {
        List<String> rows = conversationMapper.loadCoordinatorAgent(conversationId);
        return rows.isEmpty() || rows.get(0) == null ? "" : rows.get(0);
    }

    public List<String> findDispatchForConversation(String conversationId) {
        return dispatchMapper.findDispatchForConversation(conversationId);
    }

    public TaskRecord findTaskByBusinessId(String taskId) {
        List<TaskRecord> rows = taskMapper.findTaskByBusinessId(taskId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public java.util.Map<String, Object> findTaskDetail(String taskId) {
        List<java.util.Map<String, Object>> rows = taskMapper.findTaskDetail(taskId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Unguarded terminal transition for CLI-submitted expert results. */
    public boolean markTaskSucceeded(String taskId, String resultJson) {
        return taskMapper.markTaskSucceeded(taskId, resultJson) == 1;
    }

    /** CLI ask-human: RUNNING → WAITING_HUMAN. */
    public boolean markTaskWaitingHuman(String taskId) {
        return taskMapper.markTaskWaitingHuman(taskId) == 1;
    }

    /** Advance the event cursor only while the task stays RUNNING. */
    public void advanceRunningTask(String taskId, long sequence) {
        taskMapper.advanceRunningTask(taskId, sequence);
    }

    /** 同一专家 session 已消费到的最大事件序列（含所有任务）。 */
    public long findMaxLastSequenceBySession(String sessionId) {
        Long watermark = taskMapper.findMaxLastSequenceBySession(sessionId);
        return watermark == null ? 0L : watermark;
    }

    /** 同一专家 session 内、除当前任务外已消费到的最大事件序列。 */
    public long findLastSequenceBySessionExcludingTask(
            String sessionId, String taskId) {
        Long watermark = taskMapper.findLastSequenceBySessionExcludingTask(
                sessionId, taskId);
        return watermark == null ? 0L : watermark;
    }

    @Transactional
    public boolean recordEvent(String tenantId, String taskId, AgentEvent event) {
        try {
            taskEventMapper.insertTaskEvent("task-event-" + UUID.randomUUID(),
                    tenantId, taskId, event.getEventId(), event.getSequence(),
                    event.getType(), write(event));
        } catch (DuplicateKeyException ex) {
            return false;
        }
        return true;
    }

    public boolean advanceTask(
            String taskId, long sequence, String status, String resultJson) {
        return taskMapper.advanceTask(taskId, status, sequence, resultJson,
                "SUCCEEDED".equals(status)) == 1;
    }

    public void completePlanAndDispatch(
            String planId, String dispatchId, String status, String error) {
        planMapper.updatePlanStatus(planId, status);
        completeDispatch(dispatchId, status, error);
    }

    @Transactional
    public int failTasksForMessage(String tenantId, String messageId) {
        // Keep plan and task states consistent: the plan must not stay
        // RUNNING when all its tasks were force-failed.
        planMapper.failPlansForMessage(tenantId, messageId);
        return taskMapper.failTasksForMessage(tenantId, messageId);
    }

    /**
     * Reset tasks stranded in STARTING (process died between submitRun and
     * saveSession) back to PENDING so they get re-dispatched. The cutoff
     * makes sure a task being started right now by the lease holder is
     * never touched. last_sequence is reset because the replacement
     * AgentCore session starts its event sequence from 1 again.
     */
    public int recoverStaleStartingTasks(
            String tenantId, String messageId, Timestamp cutoff) {
        return taskMapper.recoverStaleStartingTasks(tenantId, messageId, cutoff);
    }

    /**
     * Force a task status without the {@code last_sequence} guard. Used by
     * cancel(): the synthetic cancel event's sequence can collide with a
     * concurrently consumed real event, and the sequence guard would then
     * silently drop the cancel after the AgentCore session was already
     * stopped, leaving the task RUNNING against a dead session.
     */
    public boolean cancelTask(String taskId, String status, String resultJson) {
        return taskMapper.cancelTask(taskId, status, resultJson) == 1;
    }

    /** Count one AgentCore failure tick for a task; returns the new count. */
    public int incrementConsecutiveFailures(String taskId) {
        taskMapper.incrementConsecutiveFailures(taskId);
        Integer count = taskMapper.selectConsecutiveFailures(taskId);
        return count == null ? 0 : count;
    }

    public void resetConsecutiveFailures(String taskId) {
        taskMapper.resetConsecutiveFailures(taskId);
    }

    public void completeDispatch(String dispatchId, String status, String error) {
        dispatchMapper.completeDispatch(dispatchId, status, error);
    }

    public void releaseDispatch(String dispatchId) {
        dispatchMapper.releaseDispatch(dispatchId);
    }

    public TaskRecord findTask(String tenantId, String projectId, String taskId) {
        List<TaskRecord> rows = taskMapper.findTask(tenantId, projectId, taskId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public DispatchWork loadWorkForTask(String tenantId, String projectId, String taskId) {
        List<String> dispatchIds = taskMapper.findDispatchIdForTask(tenantId, projectId, taskId);
        return dispatchIds.isEmpty() ? null : loadWork(dispatchIds.get(0));
    }

    private void insertTask(DispatchWork work, String planId, PlannedTask task) {
        String taskId = "task-" + UUID.randomUUID();
        String requestId = work.getMessageId() + ":" + task.getTaskKey();
        taskMapper.insertTask(taskId, work, planId, task.getTaskKey(), requestId,
                task.getObjective(), write(work.getAttachmentRefs()),
                write(task.getDependencies()), write(task.getRequiredCapabilities()),
                task.getExpectedOutput(), task.getAcceptanceCriteria());
    }

    private TaskRecord findReusableTask(String planId, String taskKey) {
        List<TaskRecord> rows = taskMapper.findReusableTask(planId, taskKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void insertReusedTask(
            DispatchWork work,
            String planId,
            PlannedTask task,
            TaskRecord reusable) {
        taskMapper.insertReusedTask("task-" + UUID.randomUUID(), work, planId,
                task.getTaskKey(), work.getMessageId() + ":v2:" + task.getTaskKey(),
                reusable.getExpertId(), reusable.getSessionId(), task.getObjective(),
                write(work.getAttachmentRefs()), reusable.getResultJson(),
                reusable.getLastSequence(), write(task.getDependencies()),
                write(task.getRequiredCapabilities()), task.getExpectedOutput(),
                task.getAcceptanceCriteria(), reusable.getId());
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize execution state.", ex);
        }
    }
}
