package org.cmb.teamcoordinator.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import org.cmb.teamcoordinator.agentcore.AgentCoreAdapter;
import org.cmb.teamcoordinator.agentcore.AgentEvent;
import org.cmb.teamcoordinator.agentcore.AgentRunRequest;
import org.cmb.teamcoordinator.agentcore.AgentRunResponse;
import org.cmb.teamcoordinator.config.DigitalTeamProperties;
import org.cmb.teamcoordinator.intent.TaskIntent;
import org.cmb.teamcoordinator.planning.HttpPlanModelClient;
import org.cmb.teamcoordinator.planning.PlanModelClient;
import org.junit.jupiter.api.Test;

class HttpPlanModelClientTest {

    private static final String ORIGINAL_SESSION = "plan-session-1";

    @Test
    void repairFeedsBackInvalidOutputAndReusesSession() {
        RecordingAdapter adapter = new RecordingAdapter();
        HttpPlanModelClient client =
                new HttpPlanModelClient(adapter, new DigitalTeamProperties());

        client.repairPlan("original prompt", new TaskIntent(), "invalid plan json",
                ORIGINAL_SESSION, 7L, 2, 1, "coordinator", "key", null);

        assertEquals(ORIGINAL_SESSION, adapter.lastRequest.getConversationSessionId());
        assertTrue(adapter.lastPrompt.contains("invalid plan json"),
                "repair prompt must include the invalid output: " + adapter.lastPrompt);
        assertTrue(adapter.lastPrompt.contains("plan_version 2"),
                "repair prompt must state the expected plan_version");
        assertEquals(7L, adapter.lastAfterSequence,
                "repair must continue streaming after the last consumed event");
    }

    @Test
    void createPlanUsesFreshSessionAndReportsLastSequence() {
        RecordingAdapter adapter = new RecordingAdapter();
        HttpPlanModelClient client =
                new HttpPlanModelClient(adapter, new DigitalTeamProperties());

        PlanModelClient.PlanCallResult result = client.createPlan(
                "prompt", new TaskIntent(), 2, "coordinator", "key", null);

        assertNull(adapter.lastRequest.getConversationSessionId());
        assertEquals(0L, adapter.lastAfterSequence);
        assertEquals(ORIGINAL_SESSION, result.getSessionId());
        assertEquals(1L, result.getLastSequence());
        assertTrue(result.getOutput().contains("plan_version"));
    }

    private static class RecordingAdapter implements AgentCoreAdapter {

        AgentRunRequest lastRequest;
        String lastPrompt;
        long lastAfterSequence;

        @Override
        public AgentRunResponse submitRun(String targetAgentId, AgentRunRequest request) {
            lastRequest = request;
            lastPrompt = request.getSystemPrompt();
            return new AgentRunResponse(ORIGINAL_SESSION, "ACCEPTED");
        }

        @Override
        public List<AgentEvent> streamEvents(
                String targetAgentId, String sessionId, Long afterSequence) {
            lastAfterSequence = afterSequence == null ? 0L : afterSequence;
            AgentEvent end = AgentEvent.content(
                    "end", "{\"plan_version\":2,\"tasks\":[]}", targetAgentId);
            end.setSequence(lastAfterSequence + 1);
            end.setEventId(sessionId + ":end");
            return Collections.singletonList(end);
        }

        @Override
        public AgentEvent getRunStatus(String targetAgentId, String sessionId) {
            return null;
        }

        @Override
        public AgentEvent cancelRun(String targetAgentId, String sessionId) {
            return null;
        }

        @Override
        public AgentRunResponse resumeRun(
                String targetAgentId, String sessionId,
                String humanResponse, String idempotencyKey) {
            return null;
        }
    }
}
