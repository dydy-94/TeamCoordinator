package org.cmb.teamcoordinator.agentcore;

import java.util.List;
import java.util.Map;

public interface AgentCoreAdapter {

    AgentRunResponse submitRun(String targetAgentId, AgentRunRequest request);

    List<AgentEvent> streamEvents(String sessionId, Long afterSequence);

    default List<AgentEvent> streamEvents(
            String sessionId, Long afterSequence, String businessSessionId) {
        return streamEvents(sessionId, afterSequence);
    }

    AgentEvent getRunStatus(String sessionId);

    default AgentEvent getRunStatus(String sessionId, String businessSessionId) {
        return getRunStatus(sessionId);
    }

    AgentEvent cancelRun(String sessionId);

    default AgentRunResponse stopSession(String sessionId) {
        AgentEvent event = cancelRun(sessionId);
        return event == null ? null : new AgentRunResponse(sessionId, event.getStatus());
    }

    default AgentEvent cancelRun(String sessionId, String businessSessionId) {
        return cancelRun(sessionId);
    }

    AgentRunResponse resumeRun(
            String sessionId, String humanResponse, String idempotencyKey);

    default AgentRunResponse answerQuestion(
            String sessionId, String questionId, Map<String, String> answers) {
        return resumeRun(sessionId, String.valueOf(answers), questionId);
    }

    default AgentRunResponse resumeRun(
            String sessionId, String humanResponse, String idempotencyKey,
            String businessSessionId) {
        return resumeRun(sessionId, humanResponse, idempotencyKey);
    }
}
