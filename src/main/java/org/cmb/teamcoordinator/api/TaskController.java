package org.cmb.teamcoordinator.api;

import javax.servlet.http.HttpServletRequest;
import org.cmb.infrastructure.worker.SingleExpertWorker;
import org.cmb.application.domain.TaskRecord;
import org.cmb.application.domain.IdentityProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/expert-tasks")
public class TaskController {

    private final SingleExpertWorker worker;
    private final IdentityProvider identityProvider;

    public TaskController(SingleExpertWorker worker, IdentityProvider identityProvider) {
        this.worker = worker;
        this.identityProvider = identityProvider;
    }

    /**
     * 取消任务
     * @param servletRequest
     * @param projectId
     * @param taskId
     * @return
     */
    @DeleteMapping("/{taskId}")
    public TaskRecord cancel(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @PathVariable String taskId) {
        return worker.cancel(
                identityProvider.currentIdentity(servletRequest), projectId, taskId);
    }
}
