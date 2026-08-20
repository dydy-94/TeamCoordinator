package org.cmb.application.domain;

import java.util.function.Consumer;
import org.cmb.application.domain.AgentEvent;

/**
 * Runs semantic reviews (second-pass model judgments) over Coordinator
 * outputs. Callers must fail open: an {@code inconclusive} result means the
 * review could not be completed and the original output stands — semantic
 * checks are best-effort, structural validation remains mandatory.
 */
public interface SemanticCheckClient {

    SemanticCheckResult check(String prompt, String agentId, Consumer<AgentEvent> eventSink);

    class SemanticCheckResult {
        private final boolean conclusive;
        private final boolean consistent;
        private final String reason;
        private final String rawOutput;

        public SemanticCheckResult(
                boolean conclusive, boolean consistent, String reason, String rawOutput) {
            this.conclusive = conclusive;
            this.consistent = consistent;
            this.reason = reason;
            this.rawOutput = rawOutput;
        }

        /** False when the review run failed or its output could not be parsed. */
        public boolean isConclusive() { return conclusive; }
        public boolean isConsistent() { return consistent; }
        public String getReason() { return reason; }
        public String getRawOutput() { return rawOutput; }
    }
}
