package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.cmb.teamcoordinator.intent.CoordinatorAgentRun;

/**
 * SQL access for coordinator agent runs (coordinator_agent_run). Queries
 * that may match multiple rows return {@code List} so the repository
 * facade keeps its "first row or null" semantics.
 */
@Mapper
public interface CoordinatorAgentRunMapper {

    int insertRun(
            @Param("id") String id,
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("messageId") String messageId,
            @Param("runKey") String runKey,
            @Param("contextJson") String contextJson,
            @Param("businessSessionId") String businessSessionId);

    List<CoordinatorAgentRun> findByRunKey(
            @Param("tenantId") String tenantId,
            @Param("runKey") String runKey);

    int saveSession(
            @Param("id") String id,
            @Param("stage") String stage,
            @Param("sessionId") String sessionId);

    int saveSessionExisting(
            @Param("id") String id,
            @Param("stage") String stage,
            @Param("sessionId") String sessionId);

    int advance(
            @Param("id") String id,
            @Param("sequence") long sequence,
            @Param("status") String status);

    int complete(
            @Param("id") String id,
            @Param("sequence") long sequence,
            @Param("output") String output);

    int prepareRepair(
            @Param("id") String id,
            @Param("invalidOutput") String invalidOutput);

    int fail(
            @Param("id") String id,
            @Param("output") String output);
}
