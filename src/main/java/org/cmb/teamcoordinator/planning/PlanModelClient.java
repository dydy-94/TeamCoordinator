package org.cmb.teamcoordinator.planning;

import java.util.function.Consumer;
import org.cmb.teamcoordinator.agentcore.AgentEvent;
import org.cmb.teamcoordinator.intent.TaskIntent;

public interface PlanModelClient {

    String modelName();

    /** @param invocationKey unique key for this planning call, used for session reuse across repairs */
    PlanCallResult createPlan(String prompt, TaskIntent intent, int planVersion,
            String agentId, String invocationKey, Consumer<AgentEvent> eventSink);

    /** @param invocationKey same key as the original createPlan call to reuse its session */
    PlanCallResult repairPlan(String prompt, TaskIntent intent, String invalidOutput,
            int attempt, String agentId, String invocationKey, Consumer<AgentEvent> eventSink);

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
