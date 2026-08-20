package org.cmb.application.domain;

import java.util.List;
import java.util.Map;

public interface AgentCoreAdapter {

    AgentRunResponse submitRun(String targetAgentId, AgentRunRequest request);

    List<AgentEvent> streamEvents(String targetAgentId, String sessionId, Long afterSequence);

    default List<AgentEvent> streamEvents(
            String targetAgentId, String sessionId, Long afterSequence,
            String businessSessionId) {
        return streamEvents(targetAgentId, sessionId, afterSequence);
    }

    AgentEvent getRunStatus(String targetAgentId, String sessionId);

    default AgentEvent getRunStatus(
            String targetAgentId, String sessionId, String businessSessionId) {
        return getRunStatus(targetAgentId, sessionId);
    }

    AgentEvent cancelRun(String targetAgentId, String sessionId);

    default AgentRunResponse stopSession(String targetAgentId, String sessionId) {
        AgentEvent event = cancelRun(targetAgentId, sessionId);
        return event == null ? null : new AgentRunResponse(sessionId, event.getStatus());
    }

    default AgentEvent cancelRun(
            String targetAgentId, String sessionId, String businessSessionId) {
        return cancelRun(targetAgentId, sessionId);
    }

    AgentRunResponse resumeRun(
            String targetAgentId, String sessionId,
            String humanResponse, String idempotencyKey);

    default AgentRunResponse answerQuestion(
            String targetAgentId, String sessionId,
            String questionId, Map<String, String> answers) {
        return resumeRun(targetAgentId, sessionId,
                String.valueOf(answers), questionId);
    }

    default AgentRunResponse resumeRun(
            String targetAgentId, String sessionId,
            String humanResponse, String idempotencyKey,
            String businessSessionId) {
        return resumeRun(targetAgentId, sessionId, humanResponse, idempotencyKey);
    }
}
