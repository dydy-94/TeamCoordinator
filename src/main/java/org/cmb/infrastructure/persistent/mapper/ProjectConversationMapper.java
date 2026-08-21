package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.cmb.application.dto.ConversationTaskView;

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

    List<ConversationTaskView> listByProject(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId);

    int delete(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("taskId") String taskId);

    List<ConversationTaskView> get(
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
}
