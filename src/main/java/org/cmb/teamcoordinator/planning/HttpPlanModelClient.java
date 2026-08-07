package org.cmb.teamcoordinator.planning;

import java.util.Comparator;
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

    public HttpPlanModelClient(
            AgentCoreAdapter agentCore, DigitalTeamProperties properties) {
        this.agentCore = agentCore;
    }

    @Override
    public String modelName() {
        return "agentcore-http";
    }

    @Override
    public PlanCallResult createPlan(String prompt, TaskIntent intent, int planVersion,
            String agentId, String invocationKey, Consumer<AgentEvent> eventSink) {
        return callAgentCore(null, prompt, agentId, eventSink);
    }

    @Override
    public PlanCallResult repairPlan(String prompt, TaskIntent intent,
            String invalidOutput, int attempt,
            String agentId, String invocationKey, Consumer<AgentEvent> eventSink) {
        // Session reuse across repairs is handled by the caller (PlanningService)
        // passing the sessionId from the original createPlan result via
        // conversationSessionId. This parameter is not available in the current
        // interface — the caller simply calls createPlan again and the old
        // session is orphaned. Acceptable because: same-thread sync call,
        // Worker crash re-runs from scratch.
        return callAgentCore(null, prompt, agentId, eventSink);
    }

    private PlanCallResult callAgentCore(
            String conversationSessionId, String prompt,
            String agentId, Consumer<AgentEvent> eventSink) {
        AgentRunRequest request = new AgentRunRequest();
        request.setSystemPrompt(prompt);
        request.setTaskText("Create an execution plan.");
        if (conversationSessionId != null) {
            request.setConversationSessionId(conversationSessionId);
        }
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
