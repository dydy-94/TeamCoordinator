package org.cmb.teamcoordinator.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.cmb.application.domain.AgentEvent;
import org.cmb.application.domain.AgentRunRequest;
import org.cmb.application.domain.AgentRunResponse;
import org.cmb.teamcoordinator.agentcore.HttpAgentCoreAdapter;
import org.cmb.common.config.DigitalTeamProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestTemplate;

@EnabledIfEnvironmentVariable(named = "AGENTCORE_REAL_TEST_ENABLED", matches = "true")
class RealAgentCoreAcceptanceIT {

    @Test
    void completesTwentyRealSingleExpertRuns() {
        DigitalTeamProperties.AgentCore properties = new DigitalTeamProperties.AgentCore();
        properties.setBaseUrl(required("AGENTCORE_BASE_URL"));
        properties.setAuthHeader(value("AGENTCORE_AUTH_HEADER", "Authorization"));
        properties.setAuthValue(System.getenv("AGENTCORE_AUTH_VALUE"));
        properties.setSubmitPath(value("AGENTCORE_SUBMIT_PATH", "/{agentId}/chat"));
        properties.setStatusPath(value(
                "AGENTCORE_STATUS_PATH", "/{agentId}/sessions/{sessionId}"));
        properties.setStreamPath(value(
                "AGENTCORE_STREAM_PATH", "/{agentId}/sessions/{sessionId}/stream"));
        properties.setCancelPath(value(
                "AGENTCORE_CANCEL_PATH", "/{agentId}/sessions/{sessionId}/cancel"));
        HttpAgentCoreAdapter adapter =
                new HttpAgentCoreAdapter(properties, new ObjectMapper(), new RestTemplate());

        for (int run = 0; run < 20; run++) {
            AgentRunRequest request = new AgentRunRequest();
            request.setTaskText("AgentCore acceptance run " + (run + 1));
            AgentRunResponse response = adapter.submitRun(
                    required("AGENTCORE_TEST_EXPERT_ID"), request);
            assertNotNull(response);
            assertNotNull(response.getSessionId());
            List<AgentEvent> events =
                    adapter.streamEvents(
                            required("AGENTCORE_TEST_EXPERT_ID"),
                            response.getSessionId(), 0L);
            assertFalse(events.isEmpty());
            AgentEvent terminal = events.get(events.size() - 1);
            assertNotNull(terminal.getType());
        }
    }

    private String required(String name) {
        String result = System.getenv(name);
        if (result == null || result.trim().isEmpty()) {
            throw new IllegalStateException(name + " is required.");
        }
        return result;
    }

    private String value(String name, String defaultValue) {
        String result = System.getenv(name);
        return result == null || result.trim().isEmpty() ? defaultValue : result;
    }
}
