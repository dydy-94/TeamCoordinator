package org.cmb.teamcoordinator.api;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.cmb.teamcoordinator.project.IdentityProvider;
import org.cmb.teamcoordinator.project.ProjectService;
import org.cmb.teamcoordinator.project.RequestIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/workspace")
public class WorkspaceController {

    private final JdbcTemplate jdbc;
    private final ProjectService projectService;
    private final IdentityProvider identityProvider;

    public WorkspaceController(
            JdbcTemplate jdbc,
            ProjectService projectService,
            IdentityProvider identityProvider) {
        this.jdbc = jdbc;
        this.projectService = projectService;
        this.identityProvider = identityProvider;
    }

    @GetMapping
    public Map<String, Object> snapshot(
            HttpServletRequest request, @PathVariable String projectId) {
        RequestIdentity identity = identityProvider.currentIdentity(request);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", projectService.get(identity, projectId));
        result.put("messages", jdbc.queryForList(
                "SELECT id, user_id, message_text, attachment_refs, status, created_at "
                        + "FROM project_message WHERE tenant_id = ? AND project_id = ? "
                        + "ORDER BY created_at",
                identity.getTenantId(), projectId));
        result.put("events", jdbc.queryForList(
                "SELECT sequence, event_type, payload, created_at FROM project_event "
                        + "WHERE tenant_id = ? AND project_id = ? AND visibility = 'PUBLIC' "
                        + "ORDER BY sequence",
                identity.getTenantId(), projectId));
        result.put("plans", jdbc.queryForList(
                "SELECT id, plan_version, status, created_at FROM coordinator_plan "
                        + "WHERE tenant_id = ? AND project_id = ? ORDER BY plan_version",
                identity.getTenantId(), projectId));
        result.put("tasks", jdbc.queryForList(
                "SELECT id, plan_id, task_key, expert_id, status, objective, dependencies, "
                        + "created_at FROM coordinator_task WHERE tenant_id = ? AND project_id = ? "
                        + "ORDER BY created_at, task_key",
                identity.getTenantId(), projectId));
        result.put("humanRequests", jdbc.queryForList(
                "SELECT id, task_id, request_type, question, status, decision, expires_at "
                        + "FROM human_request WHERE tenant_id = ? AND project_id = ? "
                        + "ORDER BY created_at",
                identity.getTenantId(), projectId));
        result.put("artifacts", jdbc.queryForList(
                "SELECT id, task_id, version, file_name, media_type, size_bytes, sha256, status "
                        + "FROM project_artifact WHERE tenant_id = ? AND project_id = ? "
                        + "ORDER BY created_at",
                identity.getTenantId(), projectId));
        return result;
    }
}
