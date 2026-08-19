package org.cmb.teamcoordinator.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.cmb.teamcoordinator.agentcore.AgentCoreAdapter;
import org.cmb.teamcoordinator.agentcore.AgentCoreTools;
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
    private static final String TOOL_PLAN_JSON =
            "{\"plan_version\":2,\"tasks\":[{\"task_key\":\"k\",\"objective\":\"o\","
                    + "\"dependencies\":[],\"expected_output\":\"e\","
                    + "\"acceptance_criteria\":\"a\",\"required_capabilities\":[\"c\"]}]}";
    private static final String END_PLAN_JSON = "{\"plan_version\":2,\"tasks\":[]}";

    @Test
    void repairFeedsBackInvalidOutputAndReusesSession() {
        RecordingAdapter adapter = new RecordingAdapter();
        HttpPlanModelClient client = client(adapter);

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
    void createPlanPrefersToolSubmissionOverEndContent() {
        RecordingAdapter adapter = new RecordingAdapter();
        HttpPlanModelClient client = client(adapter);

        PlanModelClient.PlanCallResult result = client.createPlan(
                "prompt", new TaskIntent(), 2, "coordinator", "key", null);

        assertNull(adapter.lastRequest.getConversationSessionId());
        assertEquals(0L, adapter.lastAfterSequence);
        assertEquals(ORIGINAL_SESSION, result.getSessionId());
        // The tool submission carries the real plan; the end event carries
        // different content that must be ignored.
        assertEquals(TOOL_PLAN_JSON, result.getOutput());
        assertEquals(2L, result.getLastSequence());
    }

    @Test
    void createPlanFallsBackToEndContentWhenToolNotCalled() {
        EndOnlyAdapter adapter = new EndOnlyAdapter();
        HttpPlanModelClient client = client(adapter);

        PlanModelClient.PlanCallResult result = client.createPlan(
                "prompt", new TaskIntent(), 2, "coordinator", "key", null);

        assertEquals(END_PLAN_JSON, result.getOutput());
        assertEquals(1L, result.getLastSequence());
    }

    private HttpPlanModelClient client(AgentCoreAdapter adapter) {
        return new HttpPlanModelClient(
                adapter, new DigitalTeamProperties(), new ObjectMapper());
    }

    /** Adapter whose plan run submits via the tool, then ends with different content. */
    private static class RecordingAdapter extends PlanAdapterBase {

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
            List<AgentEvent> events = new ArrayList<>();
            long seq = lastAfterSequence;
            events.add(toolEvent(sessionId, ++seq, targetAgentId, TOOL_PLAN_JSON));
            events.add(endEvent(sessionId, ++seq, targetAgentId, END_PLAN_JSON));
            return events;
        }
    }

    /** Adapter whose plan run never calls the submission tool. */
    private static class EndOnlyAdapter extends PlanAdapterBase {

        @Override
        public AgentRunResponse submitRun(String targetAgentId, AgentRunRequest request) {
            return new AgentRunResponse(ORIGINAL_SESSION, "ACCEPTED");
        }

        @Override
        public List<AgentEvent> streamEvents(
                String targetAgentId, String sessionId, Long afterSequence) {
            return Collections.singletonList(
                    endEvent(sessionId, 1L, targetAgentId, END_PLAN_JSON));
        }
    }

    private abstract static class PlanAdapterBase implements AgentCoreAdapter {

        AgentEvent toolEvent(
                String sessionId, long seq, String agentId, String inputJson) {
            AgentEvent event = AgentEvent.of("toolUsed");
            event.setSessionId(sessionId);
            event.setSequence(seq);
            event.setEventId(sessionId + ":" + seq);
            event.setTool(AgentCoreTools.SUBMIT_COORDINATOR_PLAN);
            event.setInput(new ObjectMapper().convertValue(
                    readTree(inputJson), Map.class));
            return event;
        }

        AgentEvent endEvent(String sessionId, long seq, String agentId, String content) {
            AgentEvent event = AgentEvent.content("end", content, agentId);
            event.setSessionId(sessionId);
            event.setSequence(seq);
            event.setEventId(sessionId + ":" + seq);
            return event;
        }

        private Object readTree(String json) {
            try {
                return new ObjectMapper().readTree(json);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
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
