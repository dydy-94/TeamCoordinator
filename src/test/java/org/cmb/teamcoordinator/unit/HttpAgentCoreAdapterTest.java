package org.cmb.teamcoordinator.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.cmb.application.domain.AgentEvent;
import org.cmb.application.domain.AgentRunRequest;
import org.cmb.application.domain.AgentRunResponse;
import org.cmb.infrastructure.remoteaccess.HttpAgentCoreAdapter;
import org.cmb.common.config.DigitalTeamProperties;
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

        server.expect(once(), requestTo("http://agentcore.test/api/expert-analysis/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("X-Agent-Token", "secret"))
                .andExpect(content().string(
                        containsString("\"type\":\"userInput\"")))
                .andExpect(content().string(
                        containsString("\"systemPrompt\":\"system instructions\"")))
                .andExpect(content().string(
                        containsString("\"contents\":[{\"type\":\"text\",\"value\":\"analyze\"}]")))
                .andRespond(withSuccess(
                        "{\"returnCode\":\"SUC0000\",\"data\":{\"sessionId\":\"session-1\","
                                + "\"conversationId\":\"conversation-1\","
                                + "\"queuePosition\":0}}",
                        MediaType.APPLICATION_JSON));
        String sse = "data:{\"type\":\"liveStatus\",\"content\":\"thinking\","
                + "\"eventId\":\"event-1\"}\n\n"
                + "data:{\"type\":\"chat\",\"content\":\"done\","
                + "\"eventId\":\"event-2\"}\n\n"
                + "data:{\"type\":\"end\",\"attachments\":[],"
                + "\"eventId\":\"event-3\"}\n\n";
        server.expect(once(), requestTo(
                        "http://agentcore.test/api/expert-analysis/sessions/session-1/stream?afterSequence=0"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(header("X-Session-Id", "business-session-1"))
                .andRespond(withSuccess(sse, MediaType.TEXT_EVENT_STREAM));
        server.expect(once(), requestTo("http://agentcore.test/api/expert-analysis/sessions/session-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Session-Id", "business-session-1"))
                .andRespond(withSuccess(
                        "{\"sessionId\":\"session-1\",\"sequence\":2,"
                                + "\"type\":\"end\",\"content\":\"done\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://agentcore.test/api/_/sessions/missing"))
                .andExpect(header("X-Session-Id", "business-session-1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(once(), requestTo("http://agentcore.test/api/expert-analysis/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"type\":\"stopSession\"")))
                .andExpect(content().string(containsString("\"sessionId\":\"session-1\"")))
                .andRespond(withSuccess(
                        "{\"returnCode\":\"SUC0000\",\"data\":{\"sessionId\":\"session-1\","
                                + "\"conversationId\":\"conversation-1\","
                                + "\"queuePosition\":0}}",
                        MediaType.APPLICATION_JSON));

        AgentRunRequest request = new AgentRunRequest();
        request.setTaskText("analyze");
        request.setSystemPrompt("system instructions");
        AgentRunResponse submitted = adapter.submitRun("expert-analysis", request);
        assertEquals("session-1", submitted.getSessionId());
        List<AgentEvent> events = adapter.streamEvents(
                "expert-analysis", "session-1", 0L, "business-session-1");
        assertEquals(3, events.size());
        assertEquals("liveStatus", events.get(0).getType());
        assertEquals(1L, events.get(0).getSequence());
        assertEquals("event-1", events.get(0).getEventId());
        assertEquals("end", events.get(2).getType());
        assertEquals("done", events.get(2).getContent());
        assertEquals("end", adapter.getRunStatus(
                "expert-analysis", "session-1", "business-session-1").getType());
        assertNull(adapter.getRunStatus(
                "_", "missing", "business-session-1"));
        assertEquals("RUN_CANCELLED", adapter.cancelRun(
                "expert-analysis", "session-1", "business-session-1").getType());
        server.verify();
    }
}
