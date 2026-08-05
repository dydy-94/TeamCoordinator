package org.cmb.teamcoordinator.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cmb.teamcoordinator.TeamCoordinatorApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = TeamCoordinatorApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MockFileFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void uploadsAttachmentAndReceivesDownloadableExpertArtifact() throws Exception {
        String presigned = mockMvc.perform(post("/mock/files/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"input.txt\",\"contentType\":\"text/plain\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode file = objectMapper.readTree(presigned);
        String fileId = file.get("fileId").asText();

        mockMvc.perform(put("/mock/files/" + fileId + "/content")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content("input data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checksum").isNotEmpty());

                String runBody = mockMvc.perform(post("/mock/agentcore/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"userInput\",\"sessionId\":\"\","
                                + "\"systemPrompt\":\"Process the file\","
                                + "\"data\":{\"skillNames\":[],"
                                + "\"skillOrigin\":\"skillDevelop\","
                                + "\"contents\":[{\"type\":\"text\","
                                + "\"value\":\"process file\"}],\"context\":[],"
                                + "\"attachments\":[{\"fileName\":\"input.txt\","
                                + "\"fileDownloadUrl\":\"/mock/files/" + fileId
                                + "/content\"}]}}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String sessionId = objectMapper.readTree(runBody)
                .path("data").path("sessionId").asText();

        String statusBody = mockMvc.perform(get("/mock/agentcore/runs/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments", hasSize(1)))
                .andExpect(jsonPath("$.attachments[0].fileName").value("result.txt"))
                .andExpect(jsonPath("$.attachments[0].path", startsWith("mock-file-")))
                .andReturn().getResponse().getContentAsString();
        String artifactFileId = objectMapper.readTree(statusBody)
                .get("attachments").get(0).get("path").asText();
        String artifactUrl = "/mock/files/" + artifactFileId + "/content";

        mockMvc.perform(get(artifactUrl))
                .andExpect(status().isOk())
                .andExpect(content().string("Task completed: process file\nInput: input data"));
    }
}
