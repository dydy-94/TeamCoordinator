package org.cmb.teamcoordinator.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.cmb.infrastructure.worker.SingleExpertWorker;
import org.cmb.application.service.HumanRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HumanRequestIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SingleExpertWorker worker;
    @Autowired private HumanRequestService humanRequestService;

    @Test
    void validatesAndIdempotentlyResolvesCoordinatorClarification() throws Exception {
        String projectId = createProject();
        submit(projectId, "处理一下");
        runUntilWaiting(projectId);
        String requestId = jdbc.queryForObject(
                "SELECT business_id FROM human_request WHERE project_id = ?",
                String.class, projectId);

        respond(projectId, requestId,
                "{\"decision\":\"ANSWER\",\"response\":{},"
                        + "\"idempotencyKey\":\"response-invalid\"}", 400);
        assertEquals("PENDING", requestStatus(requestId));

        String response = "{\"decision\":\"ANSWER\","
                + "\"response\":{\"answer\":\"请分析当前接口\"},"
                + "\"idempotencyKey\":\"response-1\"}";
        mockMvc.perform(post("/api/v1/projects/" + projectId
                        + "/human-requests/" + requestId + "/responses")
                        .headers(identity())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(response))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
        respond(projectId, requestId, response, 200);

        assertEquals("RESOLVED", requestStatus(requestId));
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM coordinator_dispatch WHERE project_id = ?",
                String.class, projectId));
    }

    @Test
    void resumesOriginalExpertSessionAfterHumanAnswer() throws Exception {
        String projectId = createProject();
        submit(projectId, "分析 need-human");
        runUntilTaskStatus(projectId, "WAITING_HUMAN");
        String requestId = jdbc.queryForObject(
                "SELECT business_id FROM human_request WHERE project_id = ? AND task_id IS NOT NULL",
                String.class, projectId);
        String sessionId = jdbc.queryForObject(
                "SELECT session_id FROM coordinator_task WHERE project_id = ?",
                String.class, projectId);

        respond(projectId, requestId,
                "{\"decision\":\"ANSWER\",\"response\":{\"answer\":\"Use option A\"},"
                        + "\"idempotencyKey\":\"expert-response-1\"}", 200);
        runUntilTaskStatus(projectId, "SUCCEEDED");

        assertEquals(sessionId, jdbc.queryForObject(
                "SELECT session_id FROM coordinator_task WHERE project_id = ?",
                String.class, projectId));
        assertEquals("COMPLETED", jdbc.queryForObject(
                "SELECT status FROM coordinator_dispatch WHERE project_id = ?",
                String.class, projectId));
    }

    @Test
    void onlyOwnerCanApproveAndFinalDecisionCannotChange() throws Exception {
        String projectId = createProject();
        jdbc.update(
                "INSERT INTO project_member (tenant_id, project_id, user_id, role) "
                        + "VALUES ('tenant-human', ?, 'ordinary-member', 'MEMBER')",
                projectId);
        String requestId = "human-" + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO human_request "
                        + "(business_id, tenant_id, project_id, request_type, question, allowed_roles, "
                        + "input_schema, status) VALUES (?, 'tenant-human', ?, 'APPROVAL', "
                        + "'Approve release?', 'OWNER', '{\"type\":\"object\"}', 'PENDING')",
                requestId, projectId);
        String approval = "{\"decision\":\"APPROVE\",\"response\":{},"
                + "\"idempotencyKey\":\"approval-1\"}";

        mockMvc.perform(post("/api/v1/projects/" + projectId
                        + "/human-requests/" + requestId + "/responses")
                        .headers(identity("ordinary-member"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approval))
                .andExpect(status().isForbidden());
        respond(projectId, requestId, approval, 200);
        respond(projectId, requestId,
                "{\"decision\":\"REJECT\",\"response\":{},"
                        + "\"idempotencyKey\":\"approval-2\"}", 409);
        assertEquals("APPROVE", jdbc.queryForObject(
                "SELECT decision FROM human_request WHERE business_id = ?",
                String.class, requestId));
    }

    @Test
    void expiresWaitingExpertTaskPersistently() throws Exception {
        String projectId = createProject();
        submit(projectId, "分析 need-human");
        runUntilTaskStatus(projectId, "WAITING_HUMAN");
        jdbc.update(
                "UPDATE human_request SET expires_at = DATEADD('SECOND', -1, "
                        + "CURRENT_TIMESTAMP) WHERE project_id = ? AND task_id IS NOT NULL",
                projectId);

        humanRequestService.expireDueRequests();

        assertEquals("EXPIRED", jdbc.queryForObject(
                "SELECT status FROM human_request WHERE project_id = ?",
                String.class, projectId));
        assertEquals("TIMED_OUT", jdbc.queryForObject(
                "SELECT status FROM coordinator_task WHERE project_id = ?",
                String.class, projectId));
    }

    private void respond(
            String projectId, String requestId, String body, int expected) throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + projectId
                        + "/human-requests/" + requestId + "/responses")
                        .headers(identity())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expected));
    }

    private void runUntilWaiting(String projectId) {
        for (int attempt = 0; attempt < 50; attempt++) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM human_request WHERE project_id = ?",
                    Integer.class, projectId);
            if (count != null && count > 0) {
                return;
            }
            worker.runOnce();
        }
        throw new AssertionError("Human request was not created.");
    }

    private void runUntilTaskStatus(String projectId, String expected) {
        for (int attempt = 0; attempt < 50; attempt++) {
            java.util.List<String> statuses = jdbc.queryForList(
                    "SELECT status FROM coordinator_task WHERE project_id = ?",
                    String.class, projectId);
            if (!statuses.isEmpty() && expected.equals(statuses.get(0))) {
                return;
            }
            worker.runOnce();
        }
        throw new AssertionError("Task did not reach " + expected);
    }

    private String requestStatus(String requestId) {
        return jdbc.queryForObject(
                "SELECT status FROM human_request WHERE business_id = ?",
                String.class, requestId);
    }

    private void submit(String projectId, String text) throws Exception {
        String taskBody = mockMvc.perform(post(
                        "/api/v1/projects/" + projectId + "/tasks")
                        .headers(identity())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Human test\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(taskBody).get("taskId").asText();
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

    private String createProject() throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects")
                        .headers(identity())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Human " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private HttpHeaders identity() {
        return identity("human-owner");
    }

    private HttpHeaders identity(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", "tenant-human");
        headers.set("X-User-Id", userId);
        return headers;
    }
}
