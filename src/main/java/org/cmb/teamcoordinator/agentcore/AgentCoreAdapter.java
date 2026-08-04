package org.cmb.teamcoordinator.agentcore;

import java.util.List;
import java.util.Map;

public interface AgentCoreAdapter {

    AgentRunResponse submitRun(String targetAgentId, AgentRunRequest request);

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

    default AgentRunResponse stopSession(String sessionId) {
        AgentRunEvent event = cancelRun(sessionId);
        return event == null ? null : new AgentRunResponse(sessionId, event.getStatus());
    }

    default AgentRunEvent cancelRun(String sessionId, String businessSessionId) {
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
