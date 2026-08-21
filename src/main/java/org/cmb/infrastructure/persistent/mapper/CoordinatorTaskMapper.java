package org.cmb.infrastructure.persistent.mapper;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.cmb.application.domain.AgentArtifactUploadContext;
import org.cmb.application.domain.DispatchWork;
import org.cmb.application.domain.TaskRecord;

/**
 * SQL access for expert tasks (digital_team_coordinator_task). Join
 * queries use this table as the main table. Queries that may match
 * multiple rows return {@code List} so the repository facade keeps its
 * "first row or null" semantics.
 */
@Mapper
public interface CoordinatorTaskMapper {

    List<TaskRecord> findTasksForMessage(
            @Param("tenantId") String tenantId,
            @Param("messageId") String messageId);

    int assignExpert(
            @Param("taskId") String taskId,
            @Param("expertId") String expertId);

    Integer countActiveTasks(@Param("expertId") String expertId);

    int markCorrection(@Param("taskId") String taskId);

    int insertCorrectionTask(
            @Param("id") String id,
            @Param("work") DispatchWork work,
            @Param("planId") String planId,
            @Param("taskKey") String taskKey,
            @Param("requestId") String requestId,
            @Param("objective") String objective,
            @Param("attachmentRefs") String attachmentRefs,
            @Param("dependencies") String dependencies,
            @Param("requiredCapabilities") String requiredCapabilities,
            @Param("expectedOutput") String expectedOutput,
            @Param("acceptanceCriteria") String acceptanceCriteria,
            @Param("correctionOf") String correctionOf);

    int acceptCorrection(
            @Param("correctionOf") String correctionOf,
            @Param("resultJson") String resultJson);

    List<TaskRecord> findTaskByRequestId(
            @Param("tenantId") String tenantId,
            @Param("requestId") String requestId);

    int insertSingleExpertTask(
            @Param("id") String id,
            @Param("work") DispatchWork work,
            @Param("planId") String planId,
            @Param("requestId") String requestId,
            @Param("expertId") String expertId,
            @Param("objective") String objective,
            @Param("attachmentRefs") String attachmentRefs);

    List<TaskRecord> findTaskForMessage(
            @Param("tenantId") String tenantId,
            @Param("messageId") String messageId);

    int saveSession(
            @Param("taskId") String taskId,
            @Param("sessionId") String sessionId);

    int replaceSession(
            @Param("taskId") String taskId,
            @Param("sessionId") String sessionId);

    List<TaskRecord> findTaskByBusinessId(@Param("taskId") String taskId);

    List<Map<String, Object>> findTaskDetail(@Param("taskId") String taskId);

    int markTaskSucceeded(
            @Param("taskId") String taskId,
            @Param("resultJson") String resultJson);

    int markTaskWaitingHuman(@Param("taskId") String taskId);

    /** HITL variant without the {@code RUNNING} guard (CLI ask-human path). */
    int markTaskWaitingHumanForRequest(@Param("taskId") String taskId);

    int resumeTask(
            @Param("tenantId") String tenantId,
            @Param("taskId") String taskId);

    int failTask(
            @Param("status") String status,
            @Param("tenantId") String tenantId,
            @Param("taskId") String taskId);

    List<String> findPlanIdForTask(
            @Param("tenantId") String tenantId,
            @Param("taskId") String taskId);

    java.lang.Long findMaxLastSequenceBySession(
            @Param("sessionId") String sessionId);

    java.lang.Long findLastSequenceBySessionExcludingTask(
            @Param("sessionId") String sessionId,
            @Param("taskId") String taskId);

    /** Advance the event cursor only while the task stays RUNNING. */
    int advanceRunningTask(
            @Param("taskId") String taskId,
            @Param("sequence") long sequence);

    int advanceTask(
            @Param("taskId") String taskId,
            @Param("status") String status,
            @Param("sequence") long sequence,
            @Param("resultJson") String resultJson,
            @Param("resultAccepted") boolean resultAccepted);

    int failTasksForMessage(
            @Param("tenantId") String tenantId,
            @Param("messageId") String messageId);

    int recoverStaleStartingTasks(
            @Param("tenantId") String tenantId,
            @Param("messageId") String messageId,
            @Param("cutoff") Timestamp cutoff);

    int cancelTask(
            @Param("taskId") String taskId,
            @Param("status") String status,
            @Param("resultJson") String resultJson);

    int incrementConsecutiveFailures(@Param("taskId") String taskId);

    Integer selectConsecutiveFailures(@Param("taskId") String taskId);

    int resetConsecutiveFailures(@Param("taskId") String taskId);

    List<TaskRecord> findTask(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("taskId") String taskId);

    List<String> findDispatchIdForTask(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("taskId") String taskId);

    int insertTask(
            @Param("id") String id,
            @Param("work") DispatchWork work,
            @Param("planId") String planId,
            @Param("taskKey") String taskKey,
            @Param("requestId") String requestId,
            @Param("objective") String objective,
            @Param("attachmentRefs") String attachmentRefs,
            @Param("dependencies") String dependencies,
            @Param("requiredCapabilities") String requiredCapabilities,
            @Param("expectedOutput") String expectedOutput,
            @Param("acceptanceCriteria") String acceptanceCriteria);

    List<TaskRecord> findReusableTask(
            @Param("planId") String planId,
            @Param("taskKey") String taskKey);

    int insertReusedTask(
            @Param("id") String id,
            @Param("work") DispatchWork work,
            @Param("planId") String planId,
            @Param("taskKey") String taskKey,
            @Param("requestId") String requestId,
            @Param("expertId") String expertId,
            @Param("sessionId") String sessionId,
            @Param("objective") String objective,
            @Param("attachmentRefs") String attachmentRefs,
            @Param("resultJson") String resultJson,
            @Param("lastSequence") long lastSequence,
            @Param("dependencies") String dependencies,
            @Param("requiredCapabilities") String requiredCapabilities,
            @Param("expectedOutput") String expectedOutput,
            @Param("acceptanceCriteria") String acceptanceCriteria,
            @Param("reusedFromTaskId") String reusedFromTaskId);

    List<AgentArtifactUploadContext> findUploadContextByTaskId(@Param("taskId") String taskId);

    String findProjectIdByTaskId(@Param("taskId") String taskId);

    List<AgentArtifactUploadContext> findAgentUploadContext(
            @Param("projectId") String projectId,
            @Param("conversationId") String conversationId,
            @Param("businessSessionId") String businessSessionId,
            @Param("agentRunId") String agentRunId,
            @Param("agentId") String agentId);

    List<Map<String, Object>> findTasks(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("taskId") String taskId);
}
