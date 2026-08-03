package org.cmb.teamcoordinator.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                "SELECT COUNT(*) FROM project_artifact WHERE project_id = ?",
                Integer.class, projectId) == 2);
        mockMvc.perform(get("/api/v1/projects/" + projectId
                        + "/artifacts/" + artifactId)
                        .headers(identity("outsider")))
                .andExpect(status().isNotFound());
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

    private HttpHeaders identity(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", "tenant-artifact");
        headers.set("X-User-Id", userId);
        return headers;
    }
}
