package org.cmb.teamcoordinator.agentcore;

import java.util.List;

public interface AgentCoreAdapter {

    AgentRunResponse submitRun(AgentRunRequest request);

    List<AgentRunEvent> streamEvents(String sessionId, Long afterSequence);

    default List<AgentRunEvent> streamEvents(
            String sessionId, Long afterSequence, String businessSessionId) {
        return streamEvents(sessionId, afterSequence);
    }

    AgentRunEvent getRunStatus(String sessionId);

    default AgentRunEvent getRunStatus(String sessionId, String businessSessionId) {
        return getRunStatus(sessionId);
    }

    AgentRunEvent cancelRun(String sessionId);

    default AgentRunEvent cancelRun(String sessionId, String businessSessionId) {
        return cancelRun(sessionId);
    }

    AgentRunResponse resumeRun(
            String sessionId, String humanResponse, String idempotencyKey);

    default AgentRunResponse resumeRun(
            String sessionId, String humanResponse, String idempotencyKey,
            String businessSessionId) {
        return resumeRun(sessionId, humanResponse, idempotencyKey);
    }
}
