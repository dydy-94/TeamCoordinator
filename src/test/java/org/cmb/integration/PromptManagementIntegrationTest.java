package org.cmb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import org.cmb.application.service.PromptService;
import org.cmb.application.dto.RenderedPrompt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PromptManagementIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PromptService prompts;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void createsPublishesRendersAndAuditsDatabasePromptVersions() throws Exception {
        mockMvc.perform(get("/api/v1/admin/prompts")
                        .headers(identity("ordinary-user")))
                .andExpect(status().isForbidden());

        String body = mockMvc.perform(post("/api/v1/admin/prompts")
                        .headers(identity("prompt-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptKey\":\"test.dynamic\","
                                + "\"agentScope\":\"EXPERT_COMMON\","
                                + "\"scene\":\"EXPERT_EXECUTION\","
                                + "\"templateContent\":\"Dynamic {{context_json}}\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(body);

        mockMvc.perform(post("/api/v1/admin/prompts/"
                        + created.get("id").asText() + "/publish")
                        .headers(identity("prompt-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        RenderedPrompt rendered = prompts.render(
                "test.dynamic", Collections.singletonMap("objective", "inspect API"),
                "tenant-prompt", "project-prompt", "task-prompt",
                "invocation-prompt", "expert-analysis");
        assertTrue(rendered.getContent().contains("\"objective\":\"inspect API\""));
        assertEquals(1, rendered.getVersion());
        assertEquals(Integer.valueOf(1), jdbc.queryForObject(
                "SELECT COUNT(*) FROM digital_team_prompt_execution WHERE invocation_id = ?",
                Integer.class, "invocation-prompt"));
    }

    @Test
    void deletesDraftVersionButRefusesPublished() throws Exception {
        // 建 DRAFT → 可删(204)
        String body = mockMvc.perform(post("/api/v1/admin/prompts")
                        .headers(identity("prompt-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptKey\":\"test.delete\","
                                + "\"agentScope\":\"EXPERT_COMMON\","
                                + "\"scene\":\"EXPERT_EXECUTION\","
                                + "\"templateContent\":\"Delete {{context_json}}\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String draftId = objectMapper.readTree(body).get("id").asText();

        mockMvc.perform(delete("/api/v1/admin/prompts/" + draftId)
                        .headers(identity("prompt-admin")))
                .andExpect(status().isNoContent());

        // 发布后的版本不可删(409),需先发布同 key 其他版本
        String published = mockMvc.perform(post("/api/v1/admin/prompts")
                        .headers(identity("prompt-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptKey\":\"test.delete-pub\","
                                + "\"agentScope\":\"EXPERT_COMMON\","
                                + "\"scene\":\"EXPERT_EXECUTION\","
                                + "\"templateContent\":\"Pub {{context_json}}\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String publishedId = objectMapper.readTree(published).get("id").asText();
        mockMvc.perform(post("/api/v1/admin/prompts/" + publishedId + "/publish")
                        .headers(identity("prompt-admin")))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/admin/prompts/" + publishedId)
                        .headers(identity("prompt-admin")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROMPT_PUBLISHED_DELETE_FORBIDDEN"));
    }

    private HttpHeaders identity(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", "tenant-prompt");
        headers.set("X-User-Id", userId);
        return headers;
    }
}
