package org.cmb.teamcoordinator.api;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.cmb.teamcoordinator.coordinator.CoordinatorMessageService;
import org.cmb.teamcoordinator.coordinator.MessageAcceptedResponse;
import org.cmb.teamcoordinator.coordinator.MessageRequest;
import org.cmb.teamcoordinator.coordinator.ProjectEventStreamHub;
import org.cmb.teamcoordinator.project.IdentityProvider;
import org.cmb.teamcoordinator.project.RequestIdentity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/tasks/{taskId}")
public class ProjectMessageController {

    private final CoordinatorMessageService messageService;
    private final ProjectEventStreamHub streamHub;
    private final IdentityProvider identityProvider;

    public ProjectMessageController(
            CoordinatorMessageService messageService,
            ProjectEventStreamHub streamHub,
            IdentityProvider identityProvider) {
        this.messageService = messageService;
        this.streamHub = streamHub;
        this.identityProvider = identityProvider;
    }

    /**
     * 提交任务
     * @param servletRequest
     * @param projectId
     * @param request
     * @return
     */
    @PostMapping("/messages")
    public ResponseEntity<MessageAcceptedResponse> submit(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @PathVariable String taskId,
            @Valid @RequestBody MessageRequest request) {
        MessageAcceptedResponse response =
                messageService.accept(
                        identityProvider.currentIdentity(servletRequest),
                        projectId, taskId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * 返事件返回
     * @param servletRequest
     * @param projectId
     * @param lastEventId
     * @return
     */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @PathVariable String taskId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        RequestIdentity identity = identityProvider.currentIdentity(servletRequest);
        long afterSequence = parseLastEventId(lastEventId);
        messageService.requireEventAccess(identity, projectId, taskId);
        return streamHub.subscribe(
                identity.getTenantId(),
                projectId,
                taskId,
                afterSequence,
                () -> messageService.replayAuthorized(
                        identity, projectId, taskId, afterSequence));
    }

    private long parseLastEventId(String lastEventId) {
        if (lastEventId == null || lastEventId.trim().isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(lastEventId);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
