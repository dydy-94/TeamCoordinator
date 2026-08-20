package org.cmb.teamcoordinator.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.cmb.teamcoordinator.artifact.AgentArtifactUploadContext;
import org.cmb.infrastructure.persistent.ArtifactRepository;
import org.cmb.teamcoordinator.artifact.ArtifactService;
import org.cmb.teamcoordinator.artifact.ArtifactView;
import org.cmb.teamcoordinator.common.ApiException;
import org.cmb.teamcoordinator.config.DigitalTeamProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/agent-tools/projects/{projectId}/tasks/{taskId}/artifacts")
public class AgentArtifactToolController {

    public static final String TOOL_NAME = "upload_artifact";
    private static final String TOOL_TOKEN_HEADER = "X-AgentCore-Tool-Token";
    private static final String BUSINESS_SESSION_HEADER = "X-Session-Id";
    private static final String AGENT_RUN_HEADER = "X-Agent-Run-Id";
    private static final String AGENT_ID_HEADER = "X-Agent-Id";

    private final ArtifactRepository repository;
    private final ArtifactService artifacts;
    private final String expectedToken;

    public AgentArtifactToolController(
            ArtifactRepository repository, ArtifactService artifacts,
            DigitalTeamProperties properties) {
        this.repository = repository;
        this.artifacts = artifacts;
        this.expectedToken = properties.getAgentCore().getArtifactToolToken();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ArtifactView upload(
            @PathVariable String projectId,
            @PathVariable String taskId,
            @RequestHeader(TOOL_TOKEN_HEADER) String toolToken,
            @RequestHeader(BUSINESS_SESSION_HEADER) String businessSessionId,
            @RequestHeader(AGENT_RUN_HEADER) String agentRunId,
            @RequestHeader(AGENT_ID_HEADER) String agentId,
            @RequestPart("file") MultipartFile file) throws Exception {
        authenticate(toolToken);
        AgentArtifactUploadContext context = repository.findAgentUploadContext(
                projectId, taskId, businessSessionId, agentRunId, agentId);
        if (context == null) {
            throw ApiException.forbidden(
                    "AGENT_ARTIFACT_CONTEXT_INVALID",
                    "Agent run does not belong to the supplied project task and session.");
        }
        return artifacts.uploadFromAgent(
                projectId, context, file.getOriginalFilename(),
                file.getContentType(), file.getBytes());
    }

    private void authenticate(String suppliedToken) {
        if (expectedToken == null || expectedToken.trim().isEmpty()) {
            throw ApiException.unauthorized(
                    "AGENT_TOOL_NOT_CONFIGURED", "Agent artifact tool is not configured.");
        }
        boolean matches = MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw ApiException.unauthorized(
                    "AGENT_TOOL_UNAUTHORIZED", "AgentCore tool token is invalid.");
        }
    }
}
