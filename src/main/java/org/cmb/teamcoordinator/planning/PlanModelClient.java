package org.cmb.teamcoordinator.planning;

import java.util.function.Consumer;
import org.cmb.teamcoordinator.agentcore.AgentEvent;
import org.cmb.teamcoordinator.intent.TaskIntent;

public interface PlanModelClient {

    String modelName();

    PlanCallResult createPlan(String prompt, TaskIntent intent, int planVersion,
            String agentId, Consumer<AgentEvent> eventSink);

    PlanCallResult repairPlan(String prompt, TaskIntent intent, String invalidOutput,
            int attempt, String agentId, Consumer<AgentEvent> eventSink);

    class PlanCallResult {
        private final String output;
        private final String sessionId;

        public PlanCallResult(String output, String sessionId) {
            this.output = output;
            this.sessionId = sessionId;
        }

        public String getOutput() { return output; }
        public String getSessionId() { return sessionId; }
    }
}
