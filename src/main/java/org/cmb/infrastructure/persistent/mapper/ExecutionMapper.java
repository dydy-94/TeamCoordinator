package org.cmb.infrastructure.persistent.mapper;

import java.sql.Timestamp;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.cmb.application.domain.DispatchWork;
import org.cmb.application.domain.TaskRecord;

/**
 * SQL access for the execution engine (coordinator_dispatch,
 * coordinator_plan, coordinator_task, coordinator_task_event,
 * project_conversation expert sessions). Queries that may match multiple
 * rows return {@code List} so the repository facade keeps its
 * "first row or null" semantics.
 */
@Mapper
public interface ExecutionMapper {

    List<String> selectClaimableDispatchId();

    int claimDispatch(
            @Param("owner") String owner,
            @Param("leaseExpiresAt") Timestamp leaseExpiresAt,
            @Param("dispatchId") String dispatchId);

    int renewLease(
            @Param("dispatchId") String dispatchId,
            @Param("owner") String owner,
            @Param("leaseExpiresAt") Timestamp leaseExpiresAt);

    List<DispatchWork> loadWork(@Param("dispatchId") String dispatchId);

    List<String> findExistingPlanId(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("messageId") String messageId);

    List<String> findLatestPlanId(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("messageId") String messageId);

    int insertPlanSimple(
            @Param("id") String id,
            @Param("work") DispatchWork work,
            @Param("analysisId") String analysisId,
            @Param("intentJson") String intentJson);

    int insertPlanFull(
            @Param("id") String id,
            @Param("work") DispatchWork work,
            @Param("analysisId") String analysisId,
            @Param("planVersion") int planVersion,
            @Param("intentJson") String intentJson,
            @Param("planJson") String planJson,
            @Param("repairCount") int repairCount);

    int insertReplan(
            @Param("id") String id,
            @Param("work") DispatchWork work,
            @Param("analysisId") String analysisId,
            @Param("planVersion") int planVersion,
            @Param("intentJson") String intentJson,
            @Param("planJson") String planJson,
            @Param("repairCount") int repairCount,
            @Param("supersedesPlanId") String supersedesPlanId);

    int supersedePlan(@Param("planId") String planId);

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

    List<String> findExpertSession(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("conversationId") String conversationId,
            @Param("expertId") String expertId,
            @Param("currentMessageId") String currentMessageId);

    int upsertExpertSession(
            @Param("id") String id,
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("conversationId") String conversationId,
            @Param("expertId") String expertId,
            @Param("sessionId") String sessionId,
            @Param("messageId") String messageId);

    int saveCoordinatorSession(
            @Param("conversationId") String conversationId,
            @Param("sessionId") String sessionId,
            @Param("agentId") String agentId);

    List<String> loadCoordinatorAgent(@Param("conversationId") String conversationId);

    List<String> findConversationByCoordinatorSession(@Param("sessionId") String sessionId);

    List<String> findDispatchForConversation(@Param("conversationId") String conversationId);

    int insertTaskEvent(
            @Param("id") String id,
            @Param("tenantId") String tenantId,
            @Param("taskId") String taskId,
            @Param("eventId") String eventId,
            @Param("sequence") long sequence,
            @Param("eventType") String eventType,
            @Param("payload") String payload);

    int advanceTask(
            @Param("taskId") String taskId,
            @Param("status") String status,
            @Param("sequence") long sequence,
            @Param("resultJson") String resultJson,
            @Param("resultAccepted") boolean resultAccepted);

    int updatePlanStatus(
            @Param("planId") String planId,
            @Param("status") String status);

    int failPlansForMessage(
            @Param("tenantId") String tenantId,
            @Param("messageId") String messageId);

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

    int completeDispatch(
            @Param("dispatchId") String dispatchId,
            @Param("status") String status,
            @Param("error") String error);

    int releaseDispatch(@Param("dispatchId") String dispatchId);

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
}
