package org.cmb.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.cmb.application.domain.AgentCoreAdapter;
import org.cmb.application.domain.AgentCoreTools;
import org.cmb.application.domain.AgentEvent;
import org.cmb.application.domain.AgentRunRequest;
import org.cmb.application.domain.AgentRunResponse;
import org.cmb.infrastructure.remoteaccess.HttpSemanticCheckClient;
import org.cmb.application.domain.SemanticCheckClient;
import org.junit.jupiter.api.Test;

class HttpSemanticCheckClientTest {

    @Test
    void parsesConsistentVerdict() {
        HttpSemanticCheckClient client = new HttpSemanticCheckClient(
                new VerdictAdapter("{\"consistent\":true,\"reason\":\"\"}"),
                new ObjectMapper());

        SemanticCheckClient.SemanticCheckResult result =
                client.check("review prompt", "coordinator", null);

        assertTrue(result.isConclusive());
        assertTrue(result.isConsistent());
    }

    @Test
    void parsesInconsistentVerdictWithReason() {
        HttpSemanticCheckClient client = new HttpSemanticCheckClient(
                new VerdictAdapter(
                        "{\"consistent\":false,\"reason\":\"plan ignores the objective\"}"),
                new ObjectMapper());

        SemanticCheckClient.SemanticCheckResult result =
                client.check("review prompt", "coordinator", null);

        assertTrue(result.isConclusive());
        assertFalse(result.isConsistent());
        assertEquals("plan ignores the objective", result.getReason());
    }

    @Test
    void inconclusiveWhenOutputIsNotJson() {
        HttpSemanticCheckClient client = new HttpSemanticCheckClient(
                new VerdictAdapter("the plan looks fine to me"), new ObjectMapper());

        SemanticCheckClient.SemanticCheckResult result =
                client.check("review prompt", "coordinator", null);

        assertFalse(result.isConclusive());
    }

    @Test
    void inconclusiveWhenOutputMissesConsistentField() {
        HttpSemanticCheckClient client = new HttpSemanticCheckClient(
                new VerdictAdapter("{\"reason\":\"no verdict\"}"), new ObjectMapper());

        SemanticCheckClient.SemanticCheckResult result =
                client.check("review prompt", "coordinator", null);

        assertFalse(result.isConclusive());
    }

    @Test
    void inconclusiveWhenAgentErrors() {
        HttpSemanticCheckClient client = new HttpSemanticCheckClient(
                new ErrorAdapter(), new ObjectMapper());

        SemanticCheckClient.SemanticCheckResult result =
                client.check("review prompt", "coordinator", null);

        assertFalse(result.isConclusive());
    }

    @Test
    void inconclusiveWhenSubmitFails() {
        HttpSemanticCheckClient client = new HttpSemanticCheckClient(
                new ThrowingAdapter(), new ObjectMapper());

        SemanticCheckClient.SemanticCheckResult result =
                client.check("review prompt", "coordinator", null);

        assertFalse(result.isConclusive());
    }

    @Test
    void prefersToolVerdictOverEndContent() {
        HttpSemanticCheckClient client = new HttpSemanticCheckClient(
                new VerdictAdapter(
                        "{\"consistent\":true,\"reason\":\"\"}", "garbage end content"),
                new ObjectMapper());

        SemanticCheckClient.SemanticCheckResult result =
                client.check("review prompt", "coordinator", null);

        assertTrue(result.isConclusive());
        assertTrue(result.isConsistent());
    }

    /**
     * Adapter that submits the verdict via the submission tool and ends with
     * the given end content (defaults to the same verdict).
     */
    private static class VerdictAdapter implements AgentCoreAdapter {

        private final String verdict;
        private final String endContent;

        VerdictAdapter(String verdict) {
            this(verdict, verdict);
        }

        VerdictAdapter(String verdict, String endContent) {
            this.verdict = verdict;
            this.endContent = endContent;
        }

        @Override
        public AgentRunResponse submitRun(String targetAgentId, AgentRunRequest request) {
            return new AgentRunResponse("session-1", "ACCEPTED");
        }

        @Override
        public List<AgentEvent> streamEvents(
                String targetAgentId, String sessionId, Long afterSequence) {
            AgentEvent tool = AgentEvent.of("toolUsed");
            tool.setSessionId(sessionId);
            tool.setSequence(1);
            tool.setEventId(sessionId + ":tool");
            tool.setTool(AgentCoreTools.SUBMIT_REVIEW_VERDICT);
            tool.setInput(inputMap(verdict));
            AgentEvent end = AgentEvent.content("end", endContent, targetAgentId);
            end.setSessionId(sessionId);
            end.setSequence(2);
            end.setEventId(sessionId + ":end");
            return Arrays.asList(tool, end);
        }

        private Map<String, Object> inputMap(String json) {
            try {
                return new ObjectMapper().convertValue(
                        new ObjectMapper().readTree(json), Map.class);
            } catch (Exception ex) {
                return Collections.singletonMap("raw", json);
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

    /** Adapter whose run ends with an error event. */
    private static class ErrorAdapter extends VerdictAdapter {

        ErrorAdapter() {
            super("{\"consistent\":true}");
        }

        @Override
        public List<AgentEvent> streamEvents(
                String targetAgentId, String sessionId, Long afterSequence) {
            AgentEvent error = AgentEvent.content("error", "review failed", targetAgentId);
            error.setSequence(1);
            error.setEventId(sessionId + ":error");
            return Collections.singletonList(error);
        }
    }

    /** Adapter that throws on submit. */
    private static class ThrowingAdapter implements AgentCoreAdapter {

        @Override
        public AgentRunResponse submitRun(String targetAgentId, AgentRunRequest request) {
            throw new IllegalStateException("agentcore down");
        }

        @Override
        public List<AgentEvent> streamEvents(
                String targetAgentId, String sessionId, Long afterSequence) {
            return Collections.emptyList();
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
