package org.cmb.teamcoordinator.api;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.cmb.application.service.WorkspaceService;
import org.cmb.teamcoordinator.coordinator.ConversationTaskService;
import org.cmb.application.domain.IdentityProvider;
import org.cmb.teamcoordinator.project.ProjectService;
import org.cmb.application.domain.RequestIdentity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/tasks/{taskId}/workspace")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final ProjectService projectService;
    private final IdentityProvider identityProvider;
    private final ConversationTaskService conversationTasks;

    public WorkspaceController(
            WorkspaceService workspaceService,
            ProjectService projectService,
            IdentityProvider identityProvider,
            ConversationTaskService conversationTasks) {
        this.workspaceService = workspaceService;
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
        return workspaceService.snapshot(identity.getTenantId(), projectId, taskId,
                projectService.get(identity, projectId));
    }
}
