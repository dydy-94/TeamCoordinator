package org.cmb.teamcoordinator.api;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.cmb.teamcoordinator.human.HumanRequestService;
import org.cmb.application.dto.HumanRequestView;
import org.cmb.application.dto.HumanResponseRequest;
import org.cmb.application.domain.IdentityProvider;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/human-requests")
public class HumanRequestController {

    private final HumanRequestService service;
    private final IdentityProvider identityProvider;

    public HumanRequestController(
            HumanRequestService service, IdentityProvider identityProvider) {
        this.service = service;
        this.identityProvider = identityProvider;
    }

    /**
     * HITL 用户输入消息
     * @param servletRequest
     * @param projectId
     * @param requestId
     * @param request
     * @return
     */
    @PostMapping("/{requestId}/responses")
    public HumanRequestView respond(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @PathVariable String requestId,
            @Valid @RequestBody HumanResponseRequest request) {
        return service.respond(
                identityProvider.currentIdentity(servletRequest),
                projectId, requestId, request);
    }
}
