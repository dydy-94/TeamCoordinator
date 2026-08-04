package org.cmb.teamcoordinator.api;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.cmb.teamcoordinator.project.IdentityProvider;
import org.cmb.teamcoordinator.prompt.CreatePromptTemplateRequest;
import org.cmb.teamcoordinator.prompt.PromptService;
import org.cmb.teamcoordinator.prompt.PromptTemplateView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/prompts")
public class PromptAdminController {

    private final PromptService prompts;
    private final IdentityProvider identities;

    public PromptAdminController(PromptService prompts, IdentityProvider identities) {
        this.prompts = prompts;
        this.identities = identities;
    }

    /**
     * 列出所有prompt
     * @param request
     * @param promptKey
     * @return
     */
    @GetMapping
    public List<PromptTemplateView> list(
            HttpServletRequest request,
            @RequestParam(required = false) String promptKey) {
        return prompts.list(identities.currentIdentity(request), promptKey);
    }

    /**
     * 创建prompt模板
     * @param servletRequest
     * @param request
     * @return
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromptTemplateView create(
            HttpServletRequest servletRequest,
            @Valid @RequestBody CreatePromptTemplateRequest request) {
        return prompts.create(identities.currentIdentity(servletRequest), request);
    }

    /**
     * 发布模板
     * @param request
     * @param promptId
     * @return
     */
    @PostMapping("/{promptId}/publish")
    public PromptTemplateView publish(
            HttpServletRequest request, @PathVariable String promptId) {
        return prompts.publish(identities.currentIdentity(request), promptId);
    }
}
