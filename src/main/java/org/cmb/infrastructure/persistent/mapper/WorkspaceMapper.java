package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * SQL access for the task workspace snapshot (project_conversation,
 * project_message, project_event, coordinator_plan, coordinator_task,
 * human_request, project_artifact). Rows are returned as raw maps so the
 * snapshot keys (column aliases) stay an exact front-end contract; queries
 * return {@code List} so the service facade keeps its "first row or empty"
 * semantics.
 */
@Mapper
public interface WorkspaceMapper {

    List<Map<String, Object>> findConversation(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("taskId") String taskId);

    List<Map<String, Object>> findMessages(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("taskId") String taskId);

    List<Map<String, Object>> findEvents(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("taskId") String taskId);

    List<Map<String, Object>> findPlans(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("taskId") String taskId);

    List<Map<String, Object>> findTasks(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("taskId") String taskId);

    List<Map<String, Object>> findHumanRequests(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("taskId") String taskId);

    List<Map<String, Object>> findArtifacts(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId);
}
