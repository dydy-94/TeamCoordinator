package org.cmb.teamcoordinator.api;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.cmb.teamcoordinator.intent.CoordinatorDecision;
import org.cmb.teamcoordinator.intent.IntentAnalysisRequest;
import org.cmb.teamcoordinator.intent.IntentAnalysisService;
import org.cmb.teamcoordinator.project.IdentityProvider;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/intent-analysis")
public class IntentAnalysisController {

    private final IntentAnalysisService analysisService;
    private final IdentityProvider identityProvider;

    public IntentAnalysisController(
            IntentAnalysisService analysisService, IdentityProvider identityProvider) {
        this.analysisService = analysisService;
        this.identityProvider = identityProvider;
    }

    @PostMapping
    public CoordinatorDecision analyze(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @Valid @RequestBody IntentAnalysisRequest request) {
        return analysisService.analyze(
                identityProvider.currentIdentity(servletRequest), projectId, request);
    }
}
