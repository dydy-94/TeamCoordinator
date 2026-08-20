package org.cmb.application.domain;

import java.util.function.Consumer;
import org.cmb.application.domain.AgentEvent;
import org.cmb.application.domain.TaskIntent;

public interface PlanModelClient {

    String modelName();

    /** @param invocationKey unique key for this planning call, used for session reuse across repairs */
    PlanCallResult createPlan(String prompt, TaskIntent intent, int planVersion,
            String agentId, String invocationKey, Consumer<AgentEvent> eventSink);

    /**
     * Repair a failed plan. The implementation must feed {@code invalidOutput}
     * back to the model and reuse {@code sessionId} / {@code lastSequence} so
     * the model continues the same conversation and only new events are read.
     */
    PlanCallResult repairPlan(String prompt, TaskIntent intent, String invalidOutput,
            String sessionId, long lastSequence, int planVersion, int attempt,
            String agentId, String invocationKey, Consumer<AgentEvent> eventSink);

    class PlanCallResult {
        private final String output;
        private final String sessionId;
        private final long lastSequence;

        public PlanCallResult(String output, String sessionId, long lastSequence) {
            this.output = output;
            this.sessionId = sessionId;
            this.lastSequence = lastSequence;
        }

        public String getOutput() { return output; }
        public String getSessionId() { return sessionId; }
        public long getLastSequence() { return lastSequence; }
    }
}
