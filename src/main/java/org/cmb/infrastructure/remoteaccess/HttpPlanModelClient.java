package org.cmb.infrastructure.remoteaccess;
import org.cmb.common.exception.PlanValidationException;
import org.cmb.application.domain.PlanModelClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import org.cmb.application.domain.AgentCoreAdapter;
import org.cmb.application.domain.AgentCoreTools;
import org.cmb.application.domain.AgentEvent;
import org.cmb.application.domain.AgentRunRequest;
import org.cmb.application.domain.AgentRunResponse;
import org.cmb.common.config.DigitalTeamProperties;
import org.cmb.application.domain.TaskIntent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "digital-team.agent-core",
        name = "mock-enabled",
        havingValue = "false")
public class HttpPlanModelClient implements PlanModelClient {

    private final AgentCoreAdapter agentCore;
    private final ObjectMapper objectMapper;

    public HttpPlanModelClient(
            AgentCoreAdapter agentCore, DigitalTeamProperties properties,
            ObjectMapper objectMapper) {
        this.agentCore = agentCore;
        this.objectMapper = objectMapper;
    }

    @Override
    public String modelName() {
        return "agentcore-http";
    }

    @Override
    public PlanCallResult createPlan(String prompt, TaskIntent intent, int planVersion,
            String agentId, String invocationKey, Consumer<AgentEvent> eventSink) {
        return callAgentCore(null, 0L, prompt, agentId, eventSink);
    }

    @Override
    public PlanCallResult repairPlan(String prompt, TaskIntent intent,
            String invalidOutput, String sessionId, long lastSequence, int planVersion,
            int attempt, String agentId, String invocationKey,
            Consumer<AgentEvent> eventSink) {
        // Feed the invalid output back to the model within the same
        // conversation and continue streaming after the last consumed event,
        // so the repair is a real correction rather than a blind retry.
        String repairPrompt = prompt
                + "\n\nYour previous output failed validation:\n"
                + invalidOutput
                + "\nCorrect every problem above and return only the required "
                + "CoordinatorPlan JSON with plan_version " + planVersion + ".";
        return callAgentCore(sessionId, lastSequence, repairPrompt, agentId, eventSink);
    }

    private PlanCallResult callAgentCore(
            String conversationSessionId, long afterSequence, String prompt,
            String agentId, Consumer<AgentEvent> eventSink) {
        AgentRunRequest request = new AgentRunRequest();
        request.setSystemPrompt(prompt);
        request.setTaskText("Create an execution plan.");
        if (conversationSessionId != null) {
            request.setConversationSessionId(conversationSessionId);
        }
        AgentRunResponse response = agentCore.submitRun(agentId, request);
        String sessionId = response.getSessionId();
        List<AgentEvent> events = agentCore.streamEvents(agentId, sessionId, afterSequence);
        events.sort(Comparator.comparingLong(AgentEvent::getSequence));
        long lastSequence = afterSequence;
        String toolSubmission = null;
        for (AgentEvent event : events) {
            lastSequence = Math.max(lastSequence, event.getSequence());
            if (eventSink != null) {
                event.setAgentId(agentId);
                eventSink.accept(event);
            }
            // Structured output via submission tool takes priority over the
            // run's end event content.
            if ("toolUsed".equals(event.getType())
                    && AgentCoreTools.SUBMIT_COORDINATOR_PLAN.equals(event.getTool())
                    && event.getInput() != null) {
                toolSubmission = write(event.getInput());
            }
            if ("end".equals(event.getType())) {
                String output = toolSubmission != null
                        ? toolSubmission : event.getContent();
                return new PlanCallResult(output, sessionId, lastSequence);
            }
            if ("error".equals(event.getType())) {
                throw new PlanValidationException(
                        "Plan generation failed: " + event.getContent());
            }
        }
        throw new PlanValidationException("Plan generation did not complete.");
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize tool input.", ex);
        }
    }
}
