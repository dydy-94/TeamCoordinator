package org.cmb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        driveTicks(projectId);
        String conversationId = conversationId(projectId);
        assertNotNull(conversationId, "conversation was not recorded");

        String decision = "{\"decision_type\":\"ANSWER\",\"answer\":\"直接回答\"}";
        submit("submit-decision", conversationId, decision).andExpect(status().isOk());
        assertEquals(1, submissionCount(conversationId, "DECISION"));

        // Re-submission overwrites instead of duplicating.
        submit("submit-decision", conversationId, decision).andExpect(status().isOk());
        assertEquals(1, submissionCount(conversationId, "DECISION"));

        // Invalid payloads are rejected by the server-side schema check.
        submit("submit-decision", conversationId,
                "{\"decision_type\":\"ANSWER\"}").andExpect(status().isBadRequest());
        submit("submit-verdict", conversationId,
                "{\"reason\":\"no consistent field\"}").andExpect(status().isBadRequest());

        // Wrong token is rejected.
        mockMvc.perform(post("/api/v1/agent-tools/cli/submit-decision")
                        .header("X-AgentCore-Tool-Token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"task_id\":\"" + conversationId
                                + "\",\"payload\":\"" + decision.replace("\"", "\\\"") + "\"}"))
                .andExpect(status().isUnauthorized());
        cleanupDispatch(projectId);
    }

    @Test
    void planSubmissionWritesPlanAndTasksForPendingDispatch() throws Exception {
        String projectId = createProject();
        String taskId = createTask(projectId);
        postMessage(projectId, taskId, "CLI 计划提交");
        driveTicks(projectId);
        String conversationId = conversationId(projectId);
        assertNotNull(conversationId, "conversation was not recorded");

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

        submit("submit-decision", conversationId, decision).andExpect(status().isOk());
        submit("submit-plan", conversationId, plan).andExpect(status().isOk());

        assertEquals(1, submissionCount(conversationId, "PLAN"));
        Integer planCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM coordinator_plan p "
                        + "WHERE p.conversation_id = ?",
                Integer.class, conversationId);
        assertNotNull(planCount);
        assertEquals(1, planCount, "plan submission should drive plan creation");
        Integer taskCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM coordinator_task t "
                        + "JOIN coordinator_plan p ON p.business_id = t.plan_id "
                        + "WHERE p.conversation_id = ?",
                Integer.class, conversationId);
        assertNotNull(taskCount);
        assertEquals(1, taskCount, "plan submission should create its tasks");
        cleanupDispatch(projectId);
    }

    @Test
    void taskDetailPullAndResultWriteBack() throws Exception {
        String projectId = createProject();
        String taskId = createTask(projectId);
        postMessage(projectId, taskId, "CLI 拉取式执行");
        // Let the mock flow create the plan and start the expert task.
        for (int attempt = 0; attempt < 20; attempt++) {
            Integer running = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM coordinator_task WHERE project_id = ? "
                            + "AND status = 'RUNNING'",
                    Integer.class, projectId);
            if (running != null && running > 0) {
                break;
            }
            worker.runOnce();
        }
        String coordinatorTaskId = jdbc.queryForObject(
                "SELECT business_id FROM coordinator_task WHERE project_id = ? "
                        + "ORDER BY created_at LIMIT 1",
                String.class, projectId);
        assertNotNull(coordinatorTaskId);

        // The expert pulls its contract via the CLI endpoint.
        String detail = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/v1/agent-tools/cli/tasks/" + coordinatorTaskId)
                        .header("X-AgentCore-Tool-Token", "test-agentcore-tool-token"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(detail.contains("systemPrompt"), "detail must include rendered prompt");
        assertTrue(detail.contains("objective"), "detail must include objective");

        // The expert writes its result back via the CLI endpoint.
        mockMvc.perform(post("/api/v1/agent-tools/cli/tasks/" + coordinatorTaskId + "/result")
                        .header("X-AgentCore-Tool-Token", "test-agentcore-tool-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result_text\":\"CLI 写回的结果\"}"))
                .andExpect(status().isOk());
        assertEquals("SUCCEEDED", jdbc.queryForObject(
                "SELECT status FROM coordinator_task WHERE business_id = ?",
                String.class, coordinatorTaskId));

        // Re-submitting a result for a terminal task is rejected.
        mockMvc.perform(post("/api/v1/agent-tools/cli/tasks/" + coordinatorTaskId + "/result")
                        .header("X-AgentCore-Tool-Token", "test-agentcore-tool-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result_text\":\"again\"}"))
                .andExpect(status().isConflict());
        cleanupDispatch(projectId);
    }

    @Test
    void messageAttachmentsReachExpertTaskDetail() throws Exception {
        String projectId = createProject();
        String taskId = createTask(projectId);
        // User uploads an artifact (reserve + complete, memory store).
        String reserveBody = mockMvc.perform(post(
                        "/api/v1/projects/" + projectId + "/artifacts/uploads")
                        .headers(identity())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"spec.txt\",\"mediaType\":\"text/plain\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String artifactId = objectMapper.readTree(reserveBody).get("artifactId").asText();
        String storageKey = jdbc.queryForObject(
                "SELECT storage_key FROM project_artifact WHERE business_id = ?",
                String.class, artifactId);
        // In-memory store: push the file content through the mock endpoint.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/mock/files/" + storageKey + "/content")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("spec content for experts"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/projects/" + projectId
                        + "/artifacts/" + artifactId + "/complete")
                        .headers(identity()))
                .andExpect(status().isOk());

        postMessage(projectId, taskId, "分析附件内容",
                java.util.Arrays.asList(artifactId));
        String coordinatorTaskId = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            java.util.List<String> rows = jdbc.queryForList(
                    "SELECT business_id FROM coordinator_task WHERE project_id = ? "
                            + "AND status = 'RUNNING' ORDER BY created_at",
                    String.class, projectId);
            if (!rows.isEmpty()) {
                coordinatorTaskId = rows.get(0);
                break;
            }
            worker.runOnce();
        }
        assertNotNull(coordinatorTaskId, "expert task did not start");

        String detail = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/v1/agent-tools/cli/tasks/" + coordinatorTaskId)
                        .header("X-AgentCore-Tool-Token", "test-agentcore-tool-token"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(detail.contains("spec.txt"), "message attachment must reach the expert: " + detail);
        assertTrue(detail.contains("fileDownloadUrl"), "attachment must carry a download URL");
        cleanupDispatch(projectId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions submit(
            String endpoint, String sessionId, String payload) throws Exception {
        org.cmb.application.dto.CliSubmissionRequest body =
                new org.cmb.application.dto.CliSubmissionRequest();
        body.setTaskId(sessionId);
        body.setPayload(payload);
        return mockMvc.perform(post("/api/v1/agent-tools/cli/" + endpoint)
                        .header("X-AgentCore-Tool-Token", "test-agentcore-tool-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)));
    }

    private int submissionCount(String sessionId, String kind) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM coordinator_cli_submission "
                        + "WHERE task_id = ? AND kind = ?",
                Integer.class, sessionId, kind);
        return count == null ? 0 : count;
    }

    /** Drive ticks until this project's dispatch has been processed. */
    private void driveTicks(String projectId) {
        for (int attempt = 0; attempt < 20; attempt++) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM project_conversation WHERE project_id = ?",
                    Integer.class, projectId);
            if (count != null && count > 0) {
                return;
            }
            worker.runOnce();
        }
    }

    private String conversationId(String projectId) {
        java.util.List<String> rows = jdbc.queryForList(
                "SELECT business_id FROM project_conversation WHERE project_id = ?",
                String.class, projectId);
        return rows.isEmpty() ? null : rows.get(0);
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
        postMessage(projectId, taskId, text, java.util.Collections.emptyList());
    }

    private void postMessage(String projectId, String taskId, String text,
            java.util.List<String> attachmentRefs) throws Exception {
        StringBuilder refs = new StringBuilder("[");
        for (String ref : attachmentRefs) {
            if (refs.length() > 1) {
                refs.append(",");
            }
            refs.append("\"").append(ref).append("\"");
        }
        refs.append("]");
        mockMvc.perform(post("/api/v1/projects/" + projectId
                        + "/tasks/" + taskId + "/messages")
                        .headers(identity())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_message_id\":\"client-" + UUID.randomUUID()
                                + "\",\"text\":\"" + text
                                + "\",\"attachment_refs\":" + refs
                                + ",\"idempotency_key\":\"idem-"
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
