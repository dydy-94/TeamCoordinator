package org.cmb.teamcoordinator.planning;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.cmb.teamcoordinator.agentcore.AgentCoreAdapter;
import org.cmb.teamcoordinator.agentcore.AgentEvent;
import org.cmb.teamcoordinator.agentcore.AgentRunRequest;
import org.cmb.teamcoordinator.agentcore.AgentRunResponse;
import org.cmb.teamcoordinator.config.DigitalTeamProperties;
import org.cmb.teamcoordinator.intent.TaskIntent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "digital-team.agent-core",
        name = "mock-enabled",
        havingValue = "false")
public class HttpPlanModelClient implements PlanModelClient {

    private final AgentCoreAdapter agentCore;
    private final DigitalTeamProperties.AgentCore properties;

    public HttpPlanModelClient(
            AgentCoreAdapter agentCore, DigitalTeamProperties properties) {
        this.agentCore = agentCore;
        this.properties = properties.getAgentCore();
    }

    @Override
    public String modelName() {
        return "agentcore-http";
    }

    @Override
    public PlanCallResult createPlan(String prompt, TaskIntent intent, int planVersion,
            String agentId, Consumer<AgentEvent> eventSink) {
        return callAgentCore(prompt, intent, planVersion, agentId, eventSink);
    }

    @Override
    public PlanCallResult repairPlan(String prompt, TaskIntent intent,
            String invalidOutput, int attempt,
            String agentId, Consumer<AgentEvent> eventSink) {
        return callAgentCore(prompt, intent,
                planVersionFromAttempt(attempt), agentId, eventSink);
    }

    private int planVersionFromAttempt(int attempt) {
        return attempt + 1;
    }

    private PlanCallResult callAgentCore(
            String prompt, TaskIntent intent, int planVersion,
            String agentId, Consumer<AgentEvent> eventSink) {
        AgentRunRequest request = new AgentRunRequest();
        request.setSystemPrompt(prompt);
        request.setTaskText("Create an execution plan for: " + intent.getObjective());
        Map<String, Object> structuredInput = new LinkedHashMap<>();
        structuredInput.put("taskIntent", intent);
        structuredInput.put("planVersion", planVersion);
        request.setStructuredInput(structuredInput);
        AgentRunResponse response = agentCore.submitRun(agentId, request);
        String sessionId = response.getSessionId();
        List<AgentEvent> events = agentCore.streamEvents(agentId, sessionId, 0L);
        events.sort(Comparator.comparingLong(AgentEvent::getSequence));
        for (AgentEvent event : events) {
            if (eventSink != null) {
                event.setAgentId(agentId);
                eventSink.accept(event);
            }
            if ("end".equals(event.getType())) {
                return new PlanCallResult(event.getContent(), sessionId);
            }
            if ("error".equals(event.getType())) {
                throw new PlanValidationException(
                        "Plan generation failed: " + event.getContent());
            }
        }
        throw new PlanValidationException("Plan generation did not complete.");
    }
}
