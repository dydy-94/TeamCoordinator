package org.cmb.teamcoordinator.api;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.cmb.teamcoordinator.project.IdentityProvider;
import org.cmb.teamcoordinator.coordinator.ConversationTaskService;
import org.cmb.teamcoordinator.project.ProjectService;
import org.cmb.teamcoordinator.project.RequestIdentity;
import org.cmb.teamcoordinator.persistence.MyBatisExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/tasks/{taskId}/workspace")
public class WorkspaceController {

    private final MyBatisExecutor jdbc;
    private final ProjectService projectService;
    private final IdentityProvider identityProvider;
    private final ConversationTaskService conversationTasks;

    public WorkspaceController(
            MyBatisExecutor jdbc,
            ProjectService projectService,
            IdentityProvider identityProvider,
            ConversationTaskService conversationTasks) {
        this.jdbc = jdbc;
        this.projectService = projectService;
        this.identityProvider = identityProvider;
        this.conversationTasks = conversationTasks;
    }

    /**
     * 返回工作区快照
     * @param request
     * @param projectId
     * @param taskId
     * @return
     */
    @GetMapping
    public Map<String, Object> snapshot(
            HttpServletRequest request,
            @PathVariable String projectId,
            @PathVariable String taskId) {
        RequestIdentity identity = identityProvider.currentIdentity(request);
        conversationTasks.require(identity, projectId, taskId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", projectService.get(identity, projectId));
        result.put("task", jdbc.queryForMap(
                "SELECT business_id AS taskId, session_id AS sessionId, title, status, "
                        + "created_at AS createdAt "
                        + "FROM project_conversation WHERE tenant_id = ? "
                        + "AND project_id = ? AND business_id = ?",
                identity.getTenantId(), projectId, taskId));
        result.put("messages", jdbc.queryForList(
                "SELECT business_id AS id, user_id, message_text, attachment_refs, status, created_at "
                        + "FROM project_message WHERE tenant_id = ? AND project_id = ? "
                        + "AND conversation_id = ? ORDER BY created_at",
                identity.getTenantId(), projectId, taskId));
        result.put("events", jdbc.queryForList(
                "SELECT sequence, event_type, payload, created_at FROM project_event "
                        + "WHERE tenant_id = ? AND project_id = ? AND visibility = 'PUBLIC' "
                        + "AND conversation_id = ? ORDER BY sequence",
                identity.getTenantId(), projectId, taskId));
        result.put("plans", jdbc.queryForList(
                "SELECT business_id AS id, plan_version, status, created_at FROM coordinator_plan "
                        + "WHERE tenant_id = ? AND project_id = ? "
                        + "AND conversation_id = ? ORDER BY plan_version",
                identity.getTenantId(), projectId, taskId));
        result.put("tasks", jdbc.queryForList(
                "SELECT t.business_id AS id, t.plan_id, t.task_key, t.expert_id, t.status, "
                        + "t.objective, t.dependencies, "
                        + "t.created_at FROM coordinator_task t "
                        + "JOIN coordinator_plan p ON p.business_id = t.plan_id "
                        + "WHERE t.tenant_id = ? AND t.project_id = ? "
                        + "AND p.conversation_id = ? ORDER BY t.created_at, t.task_key",
                identity.getTenantId(), projectId, taskId));
        result.put("humanRequests", jdbc.queryForList(
                "SELECT h.business_id AS id, h.task_id, h.request_type, h.question, h.status, "
                        + "h.decision, h.expires_at FROM human_request h "
                        + "JOIN project_message m ON m.business_id = h.message_id "
                        + "WHERE h.tenant_id = ? AND h.project_id = ? "
                        + "AND m.conversation_id = ? ORDER BY h.created_at",
                identity.getTenantId(), projectId, taskId));
        result.put("artifacts", jdbc.queryForList(
                "SELECT business_id AS id, task_id, version, file_name, media_type, size_bytes, sha256, status "
                        + "FROM project_artifact WHERE tenant_id = ? AND project_id = ? "
                        + "ORDER BY created_at",
                identity.getTenantId(), projectId));
        return result;
    }
}
