package org.cmb.teamcoordinator.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.cmb.teamcoordinator.agentcore.AgentRunEvent;
import org.cmb.teamcoordinator.agentcore.AgentRunRequest;
import org.cmb.teamcoordinator.agentcore.AgentRunResponse;
import org.cmb.teamcoordinator.agentcore.HttpAgentCoreAdapter;
import org.cmb.teamcoordinator.config.DigitalTeamProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class HttpAgentCoreAdapterTest {

    @Test
    void honorsJsonRequestSseResponseAuthenticationAndAllRunEndpoints() {
        DigitalTeamProperties.AgentCore properties = new DigitalTeamProperties.AgentCore();
        properties.setBaseUrl("http://agentcore.test/api");
        properties.setAuthHeader("X-Agent-Token");
        properties.setAuthValue("secret");
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        HttpAgentCoreAdapter adapter =
                new HttpAgentCoreAdapter(properties, new ObjectMapper(), restTemplate);

        server.expect(once(), requestTo("http://agentcore.test/api/runs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("X-Agent-Token", "secret"))
                .andRespond(withSuccess(
                        "{\"sessionId\":\"session-1\",\"status\":\"ACCEPTED\"}",
                        MediaType.APPLICATION_JSON));
        String sse = "id: 1\n"
                + "event: RUN_ACCEPTED\n"
                + "data: {\"sessionId\":\"session-1\",\"status\":\"ACCEPTED\"}\n\n"
                + "id: 2\n"
                + "event: RUN_SUCCEEDED\n"
                + "data: {\"sessionId\":\"session-1\",\"sequence\":2,"
                + "\"status\":\"SUCCEEDED\",\"payload\":{\"resultText\":\"done\"}}\n\n";
        server.expect(once(), requestTo(
                        "http://agentcore.test/api/runs/session-1/streamEvents?afterSequence=0"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE))
                .andRespond(withSuccess(sse, MediaType.TEXT_EVENT_STREAM));
        server.expect(once(), requestTo("http://agentcore.test/api/runs/session-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"sessionId\":\"session-1\",\"sequence\":2,"
                                + "\"type\":\"RUN_SUCCEEDED\",\"status\":\"SUCCEEDED\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://agentcore.test/api/runs/missing"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(once(), requestTo("http://agentcore.test/api/runs/session-1/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"sessionId\":\"session-1\",\"sequence\":3,"
                                + "\"type\":\"RUN_CANCELLED\",\"status\":\"CANCELLED\"}",
                        MediaType.APPLICATION_JSON));

        AgentRunRequest request = new AgentRunRequest();
        request.setExpertId("expert-analysis");
        request.setTaskText("analyze");
        AgentRunResponse submitted = adapter.submitRun(request);
        assertEquals("session-1", submitted.getSessionId());
        List<AgentRunEvent> events = adapter.streamEvents("session-1", 0L);
        assertEquals(2, events.size());
        assertEquals("RUN_ACCEPTED", events.get(0).getType());
        assertEquals(1L, events.get(0).getSequence());
        assertEquals("session-1:1", events.get(0).getEventId());
        assertEquals("SUCCEEDED", adapter.getRunStatus("session-1").getStatus());
        assertNull(adapter.getRunStatus("missing"));
        assertEquals("CANCELLED", adapter.cancelRun("session-1").getStatus());
        server.verify();
    }
}
