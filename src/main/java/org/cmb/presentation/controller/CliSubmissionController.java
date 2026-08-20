package org.cmb.presentation.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.validation.Valid;
import org.cmb.application.service.CliSubmissionService;
import org.cmb.application.dto.CliSubmissionRequest;
import org.cmb.common.exception.ApiException;
import org.cmb.common.config.DigitalTeamProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for the companion CLI: the agent submits its structured
 * Coordinator output (decision / plan / verdict) directly, and the payload
 * is validated and stored so the execution engine can act on it without
 * parsing the run's streamed text.
 */
@RestController
@RequestMapping("/api/v1/agent-tools/cli")
public class CliSubmissionController {

    private static final String TOOL_TOKEN_HEADER = "X-AgentCore-Tool-Token";

    private final CliSubmissionService submissions;
    private final String expectedToken;

    public CliSubmissionController(
            CliSubmissionService submissions,
            DigitalTeamProperties properties) {
        this.submissions = submissions;
        this.expectedToken = properties.getAgentCore().getArtifactToolToken();
    }

    @PostMapping("/submit-decision")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.OK)
    public void submitDecision(
            @RequestHeader(TOOL_TOKEN_HEADER) String toolToken,
            @Valid @RequestBody CliSubmissionRequest request) {
        authenticate(toolToken);
        submissions.submitDecision(request.getSessionId(), request.getPayload());
    }

    @PostMapping("/submit-plan")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.OK)
    public void submitPlan(
            @RequestHeader(TOOL_TOKEN_HEADER) String toolToken,
            @Valid @RequestBody CliSubmissionRequest request) {
        authenticate(toolToken);
        submissions.submitPlan(request.getSessionId(), request.getPayload());
    }

    @PostMapping("/submit-verdict")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.OK)
    public void submitVerdict(
            @RequestHeader(TOOL_TOKEN_HEADER) String toolToken,
            @Valid @RequestBody CliSubmissionRequest request) {
        authenticate(toolToken);
        submissions.submitVerdict(request.getSessionId(), request.getPayload());
    }

    private void authenticate(String suppliedToken) {
        if (expectedToken == null || expectedToken.trim().isEmpty()) {
            throw ApiException.unauthorized(
                    "AGENT_TOOL_NOT_CONFIGURED", "Agent tool token is not configured.");
        }
        boolean matches = MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw ApiException.unauthorized(
                    "AGENT_TOOL_TOKEN_INVALID", "Agent tool token does not match.");
        }
    }
}
