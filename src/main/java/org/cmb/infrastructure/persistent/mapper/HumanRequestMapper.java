package org.cmb.infrastructure.persistent.mapper;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.cmb.infrastructure.persistent.HumanRequestRepository.HumanRequestRecord;

/**
 * SQL access for human-in-the-loop requests (digital_team_human_request).
 * Join queries use this table as the main table. Queries that may match
 * multiple rows return {@code List} so the repository facade keeps its
 * "first row or null" semantics.
 */
@Mapper
public interface HumanRequestMapper {

    int insertCoordinatorClarification(
            @Param("id") String id,
            @Param("analysisId") String analysisId,
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("question") String question,
            @Param("inputSchema") String inputSchema,
            @Param("expiresAt") Timestamp expiresAt);

    int insertExpertClarification(
            @Param("id") String id,
            @Param("taskId") String taskId,
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("question") String question,
            @Param("agentQuestionId") String agentQuestionId,
            @Param("inputSchema") String inputSchema,
            @Param("expiresAt") Timestamp expiresAt);

    List<HumanRequestRecord> findExpiredPending();

    List<HumanRequestRecord> find(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("id") String id);

    List<HumanRequestRecord> findPendingForTask(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("taskId") String taskId);

    int resolve(
            @Param("tenantId") String tenantId,
            @Param("id") String id,
            @Param("decision") String decision,
            @Param("responseJson") String responseJson,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("userId") String userId);

    int linkDispatch(
            @Param("id") String id,
            @Param("messageId") String messageId,
            @Param("dispatchId") String dispatchId);

    int expire(
            @Param("tenantId") String tenantId,
            @Param("id") String id);

    List<Map<String, Object>> findHumanRequests(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("taskId") String taskId);
}
