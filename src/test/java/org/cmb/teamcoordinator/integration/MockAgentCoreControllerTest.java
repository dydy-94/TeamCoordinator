package org.cmb.teamcoordinator.integration;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.cmb.teamcoordinator.TeamCoordinatorApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = TeamCoordinatorApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MockAgentCoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void submitRunThenStreamEventsAsSse() throws Exception {
        MvcResult result = mockMvc.perform(post("/mock/agentcore/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"userInput\",\"sessionId\":\"\","
                                + "\"systemPrompt\":\"Analyze the request\","
                                + "\"data\":{\"skillNames\":[],"
                                + "\"skillOrigin\":\"skillDevelop\","
                                + "\"contents\":[{\"type\":\"text\","
                                + "\"value\":\"analyze\"}],\"context\":[],"
                                + "\"attachments\":[]}}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.returnCode").value("SUC0000"))
                .andExpect(jsonPath("$.data.sessionId", startsWith("mock-run-")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String sessionId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(body).path("data").path("sessionId").asText();

        mockMvc.perform(get("/mock/agentcore/runs/" + sessionId + "/streamEvents")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/event-stream")));
    }
}
