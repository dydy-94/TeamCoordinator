package org.cmb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cmb.TeamCoordinatorApplication;
import org.cmb.application.service.AgentCoreAdapter;
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

@SpringBootTest(classes = TeamCoordinatorApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConversationDeletionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SingleExpertWorker worker;
    @Autowired private AgentCoreAdapter agentCore;

    @Test
    void deletesConversationWithAllRelatedRowsAndAgentSessions() throws Exception {
        String projectId = createProject();
        JsonNode conversation = createConversation(projectId);
        String taskId = conversation.get("taskId").asText();
        String messageId = sendMessage(projectId, taskId, "analyze and write a report");
        finishDispatch(projectId, messageId);

        // 删除前：会话至少产生过协调器/专家 AgentCore 会话
        String coordSession = jdbc.queryForObject(
                "SELECT coordinator_session_id FROM digital_team_project_conversation "
                        + "WHERE business_id = ?", String.class, taskId);
        List<Map<String, Object>> expertSessions = jdbc.queryForList(
                "SELECT expert_id, session_id FROM "
                        + "digital_team_project_conversation_expert_session "
                        + "WHERE conversation_id = ?", taskId);
        assertTrue(coordSession != null && !coordSession.isEmpty());
        assertTrue(!expertSessions.isEmpty(), "expert session mapping must exist");

        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/tasks/" + taskId)
                        .headers(identity()))
                .andExpect(status().isNoContent());

        // 会话与全部关联表清零
        assertCount(0, "SELECT COUNT(*) FROM digital_team_project_conversation "
                + "WHERE business_id = ?", taskId);
        assertCount(0, "SELECT COUNT(*) FROM digital_team_project_message "
                + "WHERE conversation_id = ?", taskId);
        assertCount(0, "SELECT COUNT(*) FROM digital_team_project_event "
                + "WHERE conversation_id = ?", taskId);
        assertCount(0, "SELECT COUNT(*) FROM digital_team_coordinator_dispatch "
                + "WHERE conversation_id = ?", taskId);
        assertCount(0, "SELECT COUNT(*) FROM digital_team_coordinator_plan "
                + "WHERE conversation_id = ?", taskId);
        assertCount(0, "SELECT COUNT(*) FROM digital_team_coordinator_task "
                + "WHERE plan_id IN (SELECT business_id FROM "
                + "digital_team_coordinator_plan WHERE conversation_id = ?)", taskId);
        assertCount(0, "SELECT COUNT(*) FROM digital_team_coordinator_agent_run "
                + "WHERE message_id IN (SELECT business_id FROM "
                + "digital_team_project_message WHERE conversation_id = ?)", taskId);
        assertCount(0, "SELECT COUNT(*) FROM digital_team_coordinator_analysis "
                + "WHERE project_id = ?", projectId);
        assertCount(0, "SELECT COUNT(*) FROM digital_team_conversation_event_sequence "
                + "WHERE conversation_id = ?", taskId);
        assertCount(0, "SELECT COUNT(*) FROM "
                + "digital_team_project_conversation_expert_session "
                + "WHERE conversation_id = ?", taskId);
        assertCount(0, "SELECT COUNT(*) FROM digital_team_coordinator_cli_submission "
                + "WHERE task_id = ?", taskId);
        assertCount(0, "SELECT COUNT(*) FROM digital_team_prompt_execution "
                + "WHERE conversation_id = ?", taskId);
        assertCount(0, "SELECT COUNT(*) FROM digital_team_human_request "
                + "WHERE project_id = ?", projectId);
        assertCount(0, "SELECT COUNT(*) FROM digital_team_project_artifact "
                + "WHERE project_id = ?", projectId);
        // 共享 H2 里其他测试类的血缘行与本项目无关，仅断言本项目产物已无血缘
        assertCount(0, "SELECT COUNT(*) FROM digital_team_project_artifact_lineage "
                + "WHERE output_artifact_id IN (SELECT business_id FROM "
                + "digital_team_project_artifact WHERE project_id = ?) "
                + "OR input_artifact_id IN (SELECT business_id FROM "
                + "digital_team_project_artifact WHERE project_id = ?)",
                projectId, projectId);

        // 删除后接口不再可见
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/tasks/" + taskId)
                        .headers(identity()))
                .andExpect(status().isNotFound());

        // AgentCore 侧会话已删除
        assertTrue(agentCore.streamEvents("coordinator", coordSession, 0L).isEmpty(),
                "coordinator AgentCore session must be deleted");
        for (Map<String, Object> expert : expertSessions) {
            assertTrue(agentCore.streamEvents(
                            String.valueOf(expert.get("expert_id")),
                            String.valueOf(expert.get("session_id")), 0L).isEmpty(),
                    "expert AgentCore session must be deleted");
        }
    }

    private void assertCount(int expected, String sql, Object... params) {
        Integer count = params.length == 0
                ? jdbc.queryForObject(sql, Integer.class)
                : jdbc.queryForObject(sql, Integer.class, params);
        assertEquals(expected, count, sql);
    }

    private void finishDispatch(String projectId, String messageId) {
        for (int attempt = 0; attempt < 40; attempt++) {
            String status = jdbc.queryForObject(
                    "SELECT status FROM digital_team_coordinator_dispatch "
                            + "WHERE project_id = ? AND message_id = ?",
                    String.class, projectId, messageId);
            if ("COMPLETED".equals(status)) {
                return;
            }
            worker.runOnce();
        }
        throw new AssertionError("Dispatch did not complete.");
    }

    private String sendMessage(String projectId, String taskId, String text)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects/" + projectId
                        + "/tasks/" + taskId + "/messages")
                        .headers(identity())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_message_id\":\"" + UUID.randomUUID()
                                + "\",\"text\":\"" + text + "\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("messageId").asText();
    }

    private JsonNode createConversation(String projectId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects/" + projectId + "/tasks")
                        .headers(identity())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Deletion test\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String createProject() throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects")
                        .headers(identity())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Deletion " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private HttpHeaders identity() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Tenant-Id", "deletion-tenant");
        headers.add("X-User-Id", "deletion-owner");
        return headers;
    }
}
