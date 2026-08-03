package org.cmb.teamcoordinator.agentcore;

import java.util.List;

public interface AgentCoreAdapter {

    AgentRunResponse submitRun(AgentRunRequest request);

    List<AgentRunEvent> streamEvents(String sessionId, Long afterSequence);

    AgentRunEvent getRunStatus(String sessionId);

    AgentRunEvent cancelRun(String sessionId);

    AgentRunResponse resumeRun(
            String sessionId, String humanResponse, String idempotencyKey);
}
