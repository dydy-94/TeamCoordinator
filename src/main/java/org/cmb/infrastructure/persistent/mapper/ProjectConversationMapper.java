package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.cmb.application.domain.entity.ConversationDO;

/**
 * SQL access for conversation tasks (digital_team_project_conversation).
 * Queries that may match multiple rows return {@code List} so the
 * repository facade keeps its "first row or null" semantics.
 */
@Mapper
public interface ProjectConversationMapper {

    int insertConversation(
            @Param("id") String id,
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("sessionId") String sessionId,
            @Param("title") String title);

    List<ConversationDO> listByProject(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId);

    int delete(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("taskId") String taskId);

    List<ConversationDO> get(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("taskId") String taskId);

    int saveCoordinatorSession(
            @Param("conversationId") String conversationId,
            @Param("sessionId") String sessionId,
            @Param("agentId") String agentId);

    List<String> loadCoordinatorAgent(@Param("conversationId") String conversationId);

    List<Map<String, Object>> findConversation(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("taskId") String taskId);

    List<Map<String, Object>> listExpertSessions(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("taskId") String taskId);

    // ── 会话级联删除（顺序与外键依赖一致，见 XML 注释）──────────────────────

    int deleteArtifactLineageForConversation(@Param("taskId") String taskId);

    int deleteArtifactsForConversation(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId);

    int deleteHumanRequestsForConversation(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("taskId") String taskId);

    int deleteTasksForConversation(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId);

    int deletePlansForConversation(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId);

    int deleteAgentRunsForConversation(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId);

    int deleteAnalysesForConversation(
            @Param("projectId") String projectId, @Param("taskId") String taskId);

    int deleteDispatchesForConversation(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId);

    int deleteEventsForConversation(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId);

    int deleteMessagesForConversation(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId);

    int deleteEventSequenceForConversation(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId);

    int deleteExpertSessionsForConversation(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId);

    int deleteCliSubmissionsForConversation(@Param("taskId") String taskId);

    int deletePromptExecutionsForConversation(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId);
}
