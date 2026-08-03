package org.cmb.teamcoordinator.api;

import javax.servlet.http.HttpServletRequest;
import org.cmb.teamcoordinator.execution.SingleExpertWorker;
import org.cmb.teamcoordinator.execution.TaskRecord;
import org.cmb.teamcoordinator.project.IdentityProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/tasks")
public class TaskController {

    private final SingleExpertWorker worker;
    private final IdentityProvider identityProvider;

    public TaskController(SingleExpertWorker worker, IdentityProvider identityProvider) {
        this.worker = worker;
        this.identityProvider = identityProvider;
    }

    @DeleteMapping("/{taskId}")
    public TaskRecord cancel(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @PathVariable String taskId) {
        return worker.cancel(
                identityProvider.currentIdentity(servletRequest), projectId, taskId);
    }
}
