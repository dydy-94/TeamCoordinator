package org.cmb.teamcoordinator.semantic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.function.Consumer;
import org.cmb.teamcoordinator.agentcore.AgentCoreAdapter;
import org.cmb.teamcoordinator.agentcore.AgentEvent;
import org.cmb.teamcoordinator.agentcore.AgentRunRequest;
import org.cmb.teamcoordinator.agentcore.AgentRunResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Real review client: submits the rendered review prompt to the Coordinator
 * agent and parses the {@code {"consistent": bool, "reason": "..."}} verdict
 * from the terminal event. Every failure mode returns an inconclusive result
 * so a flaky reviewer never blocks execution.
 */
@Component
@ConditionalOnProperty(
        prefix = "digital-team.agent-core",
        name = "mock-enabled",
        havingValue = "false")
public class HttpSemanticCheckClient implements SemanticCheckClient {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(HttpSemanticCheckClient.class);

    private final AgentCoreAdapter agentCore;
    private final ObjectMapper objectMapper;

    public HttpSemanticCheckClient(
            AgentCoreAdapter agentCore, ObjectMapper objectMapper) {
        this.agentCore = agentCore;
        this.objectMapper = objectMapper;
    }

    @Override
    public SemanticCheckResult check(
            String prompt, String agentId, Consumer<AgentEvent> eventSink) {
        AgentRunRequest request = new AgentRunRequest();
        request.setSystemPrompt(prompt);
        request.setTaskText("Review for consistency and return your verdict.");
        AgentRunResponse response;
        try {
            response = agentCore.submitRun(agentId, request);
        } catch (RuntimeException ex) {
            LOGGER.warn("Semantic review submit failed; treating as inconclusive.", ex);
            return new SemanticCheckResult(false, false, null, null);
        }
        List<AgentEvent> events;
        try {
            events = agentCore.streamEvents(agentId, response.getSessionId(), 0L);
        } catch (RuntimeException ex) {
            LOGGER.warn("Semantic review stream failed; treating as inconclusive.", ex);
            return new SemanticCheckResult(false, false, null, null);
        }
        for (AgentEvent event : events) {
            if (eventSink != null) {
                event.setAgentId(agentId);
                eventSink.accept(event);
            }
            if ("error".equals(event.getType())) {
                LOGGER.warn("Semantic review agent error; treating as inconclusive: {}",
                        event.getContent());
                return new SemanticCheckResult(false, false, null, null);
            }
            if ("end".equals(event.getType())) {
                return parse(event.getContent());
            }
        }
        LOGGER.warn("Semantic review did not complete; treating as inconclusive.");
        return new SemanticCheckResult(false, false, null, null);
    }

    private SemanticCheckResult parse(String output) {
        if (output == null || output.trim().isEmpty()) {
            return new SemanticCheckResult(false, false, null, null);
        }
        try {
            JsonNode node = objectMapper.readTree(output);
            if (!node.hasNonNull("consistent")) {
                LOGGER.warn("Semantic review output missing 'consistent'; "
                        + "treating as inconclusive: {}", output);
                return new SemanticCheckResult(false, false, null, output);
            }
            boolean consistent = node.get("consistent").asBoolean();
            String reason = node.hasNonNull("reason") ? node.get("reason").asText() : "";
            return new SemanticCheckResult(true, consistent, reason, output);
        } catch (Exception ex) {
            LOGGER.warn("Semantic review output was not valid JSON; "
                    + "treating as inconclusive: {}", output);
            return new SemanticCheckResult(false, false, null, output);
        }
    }
}
