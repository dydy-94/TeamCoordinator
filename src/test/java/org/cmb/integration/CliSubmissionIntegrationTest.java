package org.cmb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.cmb.TeamCoordinatorApplication;
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

/**
 * Covers the companion-CLI submission channel: structured decision / plan /
 * verdict payloads are validated, stored keyed by session, and the plan
 * drives plan/task creation for the pending dispatch.
 */
@SpringBootTest(classes = TeamCoordinatorApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CliSubmissionIntegrationTest {

    private static final String TENANT = "tenant-cli";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SingleExpertWorker worker;

    @Test
    void storesValidatedSubmissionsAndRejectsInvalidOnes() throws Exception {
        String projectId = createProject();
        String taskId = createTask(projectId);
        postMessage(projectId, taskId, "CLI 提交测试");
        String sessionId = coordinatorSession(projectId);
        assertNotNull(sessionId, "coordinator session was not recorded");

        String decision = "{\"decision_type\":\"ANSWER\",\"answer\":\"直接回答\"}";
        submit("submit-decision", sessionId, decision).andExpect(status().isOk());
        assertEquals(1, submissionCount(sessionId, "DECISION"));

        // Re-submission overwrites instead of duplicating.
        submit("submit-decision", sessionId, decision).andExpect(status().isOk());
        assertEquals(1, submissionCount(sessionId, "DECISION"));

        // Invalid payloads are rejected by the server-side schema check.
        submit("submit-decision", sessionId,
                "{\"decision_type\":\"ANSWER\"}").andExpect(status().isBadRequest());
        submit("submit-verdict", sessionId,
                "{\"reason\":\"no consistent field\"}").andExpect(status().isBadRequest());

        // Wrong token is rejected.
        mockMvc.perform(post("/api/v1/agent-tools/cli/submit-decision")
                        .header("X-AgentCore-Tool-Token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"session_id\":\"" + sessionId
                                + "\",\"payload\":\"" + decision.replace("\"", "\\\"") + "\"}"))
                .andExpect(status().isUnauthorized());
        cleanupDispatch(projectId);
    }

    @Test
    void planSubmissionWritesPlanAndTasksForPendingDispatch() throws Exception {
        String projectId = createProject();
        String taskId = createTask(projectId);
        postMessage(projectId, taskId, "CLI 计划提交");
        String sessionId = coordinatorSession(projectId);
        assertNotNull(sessionId, "coordinator session was not recorded");

        String decision = "{\"decision_type\":\"CREATE_PLAN\",\"task_intent\":{"
                + "\"intent\":\"风险分析\",\"objective\":\"分析接口风险\","
                + "\"expected_outputs\":[\"风险清单\"],\"constraints\":[],"
                + "\"required_capabilities\":[\"analysis\"],\"input_refs\":[],"
                + "\"missing_information\":[],\"risk_level\":\"HIGH\","
                + "\"execution_mode\":\"MULTI_EXPERT\"}}";
        String plan = "{\"plan_version\":1,\"tasks\":[{\"task_key\":\"a1\","
                + "\"objective\":\"分析接口风险\",\"dependencies\":[],"
                + "\"expected_output\":\"风险清单\",\"acceptance_criteria\":\"覆盖全部接口\","
                + "\"required_capabilities\":[\"analysis\"]}]}";

        submit("submit-decision", sessionId, decision).andExpect(status().isOk());
        submit("submit-plan", sessionId, plan).andExpect(status().isOk());

        assertEquals(1, submissionCount(sessionId, "PLAN"));
        Integer planCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM coordinator_plan p "
                        + "JOIN project_conversation c ON c.business_id = p.conversation_id "
                        + "WHERE c.tenant_id = ?",
                Integer.class, TENANT);
        assertNotNull(planCount);
        assertEquals(1, planCount, "plan submission should drive plan creation");
        Integer taskCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM coordinator_task t "
                        + "JOIN coordinator_plan p ON p.business_id = t.plan_id "
                        + "JOIN project_conversation c ON c.business_id = p.conversation_id "
                        + "WHERE c.tenant_id = ?",
                Integer.class, TENANT);
        assertNotNull(taskCount);
        assertEquals(1, taskCount, "plan submission should create its tasks");
        cleanupDispatch(projectId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions submit(
            String endpoint, String sessionId, String payload) throws Exception {
        org.cmb.application.dto.CliSubmissionRequest body =
                new org.cmb.application.dto.CliSubmissionRequest();
        body.setSessionId(sessionId);
        body.setPayload(payload);
        return mockMvc.perform(post("/api/v1/agent-tools/cli/" + endpoint)
                        .header("X-AgentCore-Tool-Token", "test-agentcore-tool-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)));
    }

    private int submissionCount(String sessionId, String kind) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM coordinator_cli_submission "
                        + "WHERE session_id = ? AND kind = ?",
                Integer.class, sessionId, kind);
        return count == null ? 0 : count;
    }

    private String coordinatorSession(String projectId) {
        // Drive ticks until this project's coordinator session is recorded.
        // claimNext picks the oldest claimable dispatch, so leftovers from
        // earlier tests may consume a tick or two first.
        for (int attempt = 0; attempt < 20; attempt++) {
            java.util.List<String> rows = jdbc.queryForList(
                    "SELECT coordinator_session_id FROM project_conversation "
                            + "WHERE project_id = ? AND coordinator_session_id IS NOT NULL",
                    String.class, projectId);
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
            worker.runOnce();
        }
        return null;
    }

    private void cleanupDispatch(String projectId) {
        jdbc.update(
                "UPDATE coordinator_dispatch SET status = 'COMPLETED' "
                        + "WHERE project_id = ? AND status <> 'COMPLETED'",
                projectId);
        // Terminal tasks free their expert concurrency slots; leftover
        // RUNNING tasks would starve later tests sharing the expert pool.
        jdbc.update(
                "UPDATE coordinator_task SET status = 'FAILED' "
                        + "WHERE project_id = ? AND status NOT IN "
                        + "('SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT')",
                projectId);
    }

    private void postMessage(String projectId, String taskId, String text)
            throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + projectId
                        + "/tasks/" + taskId + "/messages")
                        .headers(identity())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_message_id\":\"client-" + UUID.randomUUID()
                                + "\",\"text\":\"" + text
                                + "\",\"attachment_refs\":[],\"idempotency_key\":\"idem-"
                                + UUID.randomUUID() + "\"}"))
                .andExpect(status().isAccepted());
    }

    private String createTask(String projectId) throws Exception {
        String body = mockMvc.perform(post(
                        "/api/v1/projects/" + projectId + "/tasks")
                        .headers(identity())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"CLI submission test\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("taskId").asText();
    }

    private String createProject() throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects")
                        .headers(identity())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CLI " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private HttpHeaders identity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", TENANT);
        headers.set("X-User-Id", "cli-owner");
        return headers;
    }
}
