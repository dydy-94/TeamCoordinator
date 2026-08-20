package org.cmb.application.domain;

/**
 * Submission tools that AgentCore agents must call to hand structured output
 * to the Coordinator. The Coordinator reads the tool input from
 * {@code toolUsed} events and falls back to the run's {@code end} event
 * content when the tool was not called (e.g. the agent has not been attached
 * the tool yet).
 *
 * <p>Tool definitions for attachment on the AgentCore side live in
 * {@code src/main/resources/agentcore-tools/} and must stay in sync with the
 * JSON Schema files under {@code src/main/resources/coordinator/} (guarded by
 * {@code AgentCoreToolsTest}).
 */
public final class AgentCoreTools {

    /** Coordinator decision: {@code CoordinatorDecision} (task-intent schema). */
    public static final String SUBMIT_COORDINATOR_DECISION =
            "submit_coordinator_decision";

    /** Planner output: {@code CoordinatorPlanSpec} (plan schema). */
    public static final String SUBMIT_COORDINATOR_PLAN = "submit_coordinator_plan";

    /** Semantic review verdict: {@code {"consistent": bool, "reason": "..."}}. */
    public static final String SUBMIT_REVIEW_VERDICT = "submit_review_verdict";

    private AgentCoreTools() {
    }
}
