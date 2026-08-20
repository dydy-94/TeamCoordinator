package org.cmb.presentation.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import javax.validation.Valid;
import org.cmb.application.dto.CliSubmissionRequest;
import org.cmb.application.service.CliSubmissionService;
import org.cmb.common.config.DigitalTeamProperties;
import org.cmb.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoints for the companion CLI. Everything is keyed by task id — the
 * identifier the AgentCore runtime and the CLI reliably share. Coordinator
 * outputs (decision / plan / verdict) use the conversation task id; expert
 * operations (task detail fetch, result write-back, artifact upload) use
 * the coordinator task id.
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

    // ── Coordinator outputs (conversation task id) ─────────────────────

    @PostMapping("/submit-decision")
    @ResponseStatus(HttpStatus.OK)
    public void submitDecision(
            @RequestHeader(TOOL_TOKEN_HEADER) String toolToken,
            @Valid @RequestBody CliSubmissionRequest request) {
        authenticate(toolToken);
        submissions.submitDecision(request.getTaskId(), request.getPayload());
    }

    @PostMapping("/submit-plan")
    @ResponseStatus(HttpStatus.OK)
    public void submitPlan(
            @RequestHeader(TOOL_TOKEN_HEADER) String toolToken,
            @Valid @RequestBody CliSubmissionRequest request) {
        authenticate(toolToken);
        submissions.submitPlan(request.getTaskId(), request.getPayload());
    }

    @PostMapping("/submit-verdict")
    @ResponseStatus(HttpStatus.OK)
    public void submitVerdict(
            @RequestHeader(TOOL_TOKEN_HEADER) String toolToken,
            @Valid @RequestBody CliSubmissionRequest request) {
        authenticate(toolToken);
        submissions.submitVerdict(request.getTaskId(), request.getPayload());
    }

    // ── Expert operations (coordinator task id) ────────────────────────

    /** The task detail an expert agent pulls after a taskId-only dispatch. */
    @GetMapping("/tasks/{taskId}")
    public Map<String, Object> getTaskDetail(
            @RequestHeader(TOOL_TOKEN_HEADER) String toolToken,
            @PathVariable String taskId) {
        authenticate(toolToken);
        return submissions.getTaskDetail(taskId);
    }

    @PostMapping("/tasks/{taskId}/result")
    @ResponseStatus(HttpStatus.OK)
    public void submitResult(
            @RequestHeader(TOOL_TOKEN_HEADER) String toolToken,
            @PathVariable String taskId,
            @Valid @RequestBody CliResultRequest request) {
        authenticate(toolToken);
        submissions.submitResult(taskId, request.getResultText());
    }

    @PostMapping(
            value = "/tasks/{taskId}/artifacts",
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public org.cmb.application.dto.ArtifactView uploadArtifact(
            @RequestHeader(TOOL_TOKEN_HEADER) String toolToken,
            @PathVariable String taskId,
            @RequestPart("file") MultipartFile file) throws Exception {
        authenticate(toolToken);
        return submissions.uploadArtifact(
                taskId, file.getOriginalFilename(),
                file.getContentType(), file.getBytes());
    }

    /** The expert asks the user for input; the answer resumes the run. */
    @PostMapping("/tasks/{taskId}/human-request")
    public Map<String, String> askHuman(
            @RequestHeader(TOOL_TOKEN_HEADER) String toolToken,
            @PathVariable String taskId,
            @Valid @RequestBody CliQuestionRequest request) {
        authenticate(toolToken);
        String questionId = submissions.askHuman(taskId, request.getQuestion());
        Map<String, String> response = new java.util.LinkedHashMap<>();
        response.put("question_id", questionId);
        return response;
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

    /** Request body for the CLI ask-human command. */
    public static class CliQuestionRequest {
        @javax.validation.constraints.NotBlank
        private String question;

        public String getQuestion() { return question; }
        public void setQuestion(String value) { this.question = value; }
    }

    /** Request body for the CLI result write-back. */
    public static class CliResultRequest {
        @javax.validation.constraints.NotBlank
        @com.fasterxml.jackson.annotation.JsonProperty("result_text")
        private String resultText;

        public String getResultText() { return resultText; }
        public void setResultText(String value) { this.resultText = value; }
    }
}
