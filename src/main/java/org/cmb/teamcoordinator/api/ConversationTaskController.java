package org.cmb.teamcoordinator.api;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.cmb.teamcoordinator.coordinator.ConversationTaskService;
import org.cmb.teamcoordinator.coordinator.ConversationTaskView;
import org.cmb.teamcoordinator.coordinator.CreateConversationTaskRequest;
import org.cmb.teamcoordinator.project.IdentityProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/tasks")
public class ConversationTaskController {

    private final ConversationTaskService tasks;
    private final IdentityProvider identities;

    public ConversationTaskController(
            ConversationTaskService tasks, IdentityProvider identities) {
        this.tasks = tasks;
        this.identities = identities;
    }

    /**
     * 创建任务
     * @param servletRequest
     * @param projectId
     * @param request
     * @return
     */
    @PostMapping
    public ResponseEntity<ConversationTaskView> create(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @Valid @RequestBody CreateConversationTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tasks.create(
                identities.currentIdentity(servletRequest), projectId, request));
    }

    /**
     * 查询任务详情
     * @param servletRequest
     * @param projectId
     * @param taskId
     * @return
     */
    @GetMapping("/{taskId}")
    public ConversationTaskView get(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @PathVariable String taskId) {
        return tasks.require(
                identities.currentIdentity(servletRequest), projectId, taskId);
    }
}
