package org.cmb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.cmb.infrastructure.worker.SingleExpertWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArtifactIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SingleExpertWorker worker;

    @Test
    void reservesCompletesVersionsAndEnforcesProjectAccess() throws Exception {
        String projectId = createProject();
        JsonNode first = reserve(projectId, "report.txt");
        String artifactId = first.get("artifactId").asText();

        mockMvc.perform(post("/api/v1/projects/" + projectId
                        + "/artifacts/" + artifactId + "/complete")
                        .headers(identity("artifact-owner")))
                .andExpect(status().isConflict());
        mockMvc.perform(put(first.get("uploadUrl").asText())
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content("verified artifact".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/projects/" + projectId
                        + "/artifacts/" + artifactId + "/complete")
                        .headers(identity("artifact-owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.size").value(17))
                .andExpect(jsonPath("$.sha256").isNotEmpty())
                .andExpect(jsonPath("$.downloadUrl").isNotEmpty());

        JsonNode second = reserve(projectId, "report.txt");
        assertEquals(2, second.get("version").asInt());
        assertTrue(jdbc.queryForObject(
                "SELECT COUNT(*) FROM digital_team_project_artifact WHERE project_id = ?",
                Integer.class, projectId) == 2);
        mockMvc.perform(get("/api/v1/projects/" + projectId
                        + "/artifacts/" + artifactId)
                        .headers(identity("outsider")))
                .andExpect(status().isNotFound());
    }

    @Test
    void agentCoreToolUploadsGeneratedFileWithoutStorageCredentials() throws Exception {
        String projectId = createProject();
        JsonNode conversation = createConversation(projectId);
        submitMessage(projectId, conversation.get("taskId").asText());
        Map<String, Object> run = awaitExpertRun(projectId);

        MockMultipartFile file = new MockMultipartFile(
                "file", "analysis.md", "text/markdown",
                "# Agent result".getBytes(StandardCharsets.UTF_8));
        String endpoint = "/api/v1/agent-tools/projects/" + projectId
                + "/tasks/" + conversation.get("taskId").asText() + "/artifacts";

        mockMvc.perform(multipart(endpoint)
                        .file(file)
                        .header("X-AgentCore-Tool-Token", "wrong-token")
                        .header("X-Session-Id", conversation.get("sessionId").asText())
                        .header("X-Agent-Run-Id", run.get("session_id"))
                        .header("X-Agent-Id", run.get("expert_id")))
                .andExpect(status().isUnauthorized());

        String body = mockMvc.perform(multipart(endpoint)
                        .file(file)
                        .header("X-AgentCore-Tool-Token", "test-agentcore-tool-token")
                        .header("X-Session-Id", conversation.get("sessionId").asText())
                        .header("X-Agent-Run-Id", run.get("session_id"))
                        .header("X-Agent-Id", run.get("expert_id")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.size").value(14))
                .andExpect(jsonPath("$.sha256").isNotEmpty())
                .andExpect(jsonPath("$.downloadUrl").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String artifactId = objectMapper.readTree(body).get("artifactId").asText();
        assertEquals("agent:" + run.get("expert_id"), jdbc.queryForObject(
                "SELECT created_by FROM digital_team_project_artifact WHERE business_id = ?",
                String.class, artifactId));
        assertEquals(run.get("session_id"), jdbc.queryForObject(
                "SELECT expert_run_id FROM digital_team_project_artifact WHERE business_id = ?",
                String.class, artifactId));
        finishDispatch(projectId);
    }

    private JsonNode reserve(String projectId, String fileName) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects/" + projectId
                        + "/artifacts/uploads")
                        .headers(identity("artifact-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"" + fileName
                                + "\",\"mediaType\":\"text/plain\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPLOADING"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String createProject() throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects")
                        .headers(identity("artifact-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Artifacts " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private JsonNode createConversation(String projectId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects/" + projectId + "/tasks")
                        .headers(identity("artifact-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Artifact tool test\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private void submitMessage(String projectId, String taskId) throws Exception {
        String requestId = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/v1/projects/" + projectId
                        + "/tasks/" + taskId + "/messages")
                        .headers(identity("artifact-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_message_id\":\"" + requestId
                                + "\",\"text\":\"analyze and create a file\"}"))
                .andExpect(status().isAccepted());
    }

    private Map<String, Object> awaitExpertRun(String projectId) {
        for (int attempt = 0; attempt < 30; attempt++) {
            java.util.List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT session_id, expert_id FROM digital_team_coordinator_task "
                            + "WHERE project_id = ? AND session_id IS NOT NULL",
                    projectId);
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
            worker.runOnce();
        }
        throw new AssertionError("Expert run was not started.");
    }

    private void finishDispatch(String projectId) {
        for (int attempt = 0; attempt < 10; attempt++) {
            String status = jdbc.queryForObject(
                    "SELECT status FROM digital_team_coordinator_dispatch WHERE project_id = ?",
                    String.class, projectId);
            if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                return;
            }
            worker.runOnce();
        }
        throw new AssertionError("Artifact tool test dispatch did not finish.");
    }

    private HttpHeaders identity(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", "tenant-artifact");
        headers.set("X-User-Id", userId);
        return headers;
    }
}
