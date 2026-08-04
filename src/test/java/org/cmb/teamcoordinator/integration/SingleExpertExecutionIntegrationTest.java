package org.cmb.teamcoordinator.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.cmb.teamcoordinator.TeamCoordinatorApplication;
import org.cmb.teamcoordinator.agentcore.AgentCoreAdapter;
import org.cmb.teamcoordinator.agentcore.AgentRunEvent;
import org.cmb.teamcoordinator.execution.ExecutionRepository;
import org.cmb.teamcoordinator.execution.SingleExpertWorker;
import org.cmb.teamcoordinator.execution.TaskRecord;
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
class SingleExpertExecutionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SingleExpertWorker worker;
    @Autowired private ExecutionRepository executionRepository;
    @Autowired private AgentCoreAdapter agentCore;

    @Test
    void completesSingleExpertRunWithLeaseRecoveryAndEventIdempotency() throws Exception {
        String projectId = createProject();
        submitMessage(projectId, "分析当前接口");

        TaskRecord task = runUntilTaskExists(projectId);
        assertNotNull(task.getSessionId());
        assertEquals("RUNNING", task.getStatus());

        String dispatchId = dispatchId(projectId);
        jdbc.update(
                "UPDATE coordinator_dispatch SET lease_owner = 'dead-instance', "
                        + "lease_expires_at = ? WHERE business_id = ?",
                Timestamp.from(Instant.now().plusSeconds(60)),
                dispatchId);
        worker.runOnce();
        assertEquals("RUNNING", task(projectId).getStatus());

        jdbc.update(
                "UPDATE coordinator_dispatch SET lease_expires_at = ? WHERE business_id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)),
                dispatchId);
        task = runUntilTerminal(projectId);
        assertEquals("SUCCEEDED", task.getStatus());
        assertEquals("COMPLETED", dispatchStatus(dispatchId));

        List<AgentRunEvent> replay = agentCore.streamEvents(task.getSessionId(), 0L);
        AgentRunEvent result = replay.get(replay.size() - 1);
        assertFalse(executionRepository.recordEvent(
                "tenant-execution", task.getId(), result));
        assertFalse(executionRepository.advanceTask(
                task.getId(), 2L, "RUNNING", null));

        Integer requestCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM coordinator_task WHERE request_id = ?",
                Integer.class,
                task.getRequestId());
        Integer finalCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_event WHERE project_id = ? "
                        + "AND event_type = 'FINAL_RESPONSE'",
                Integer.class,
                projectId);
        assertEquals(Integer.valueOf(1), requestCount);
        assertEquals(Integer.valueOf(1), finalCount);
        String businessSessionId = jdbc.queryForObject(
                "SELECT c.session_id FROM project_conversation c "
                        + "JOIN coordinator_dispatch d ON d.conversation_id = c.business_id "
                        + "WHERE d.project_id = ?",
                String.class, projectId);
        assertEquals(businessSessionId, jdbc.queryForObject(
                "SELECT business_session_id FROM coordinator_agent_run "
                        + "WHERE project_id = ?",
                String.class, projectId));
        String resultJson = jdbc.queryForObject(
                "SELECT result_json FROM coordinator_task WHERE business_id = ?",
                String.class, task.getId());
        assertTrue(resultJson.contains("\"resultText\":"));
    }

    @Test
    void exposesFailureTimeoutAndCancellationAsTerminalStates() throws Exception {
        String failedProject = createProject();
        submitMessage(failedProject, "分析 fail 场景");
        assertEquals("FAILED", runUntilTerminal(failedProject).getStatus());

        String timeoutProject = createProject();
        submitMessage(timeoutProject, "分析 timeout 场景");
        assertEquals("TIMED_OUT", runUntilTerminal(timeoutProject).getStatus());

        String cancelledProject = createProject();
        submitMessage(cancelledProject, "分析取消场景");
        TaskRecord running = runUntilTaskExists(cancelledProject);
        mockMvc.perform(delete("/api/v1/projects/" + cancelledProject
                        + "/expert-tasks/" + running.getId())
                        .headers(identity()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertEquals("CANCELLED", dispatchStatus(dispatchId(cancelledProject)));
    }

    @Test
    void failsPersistentlyWhenAgentCoreRunIsLost() throws Exception {
        String projectId = createProject();
        submitMessage(projectId, "分析丢失 Run 场景");
        TaskRecord task = runUntilTaskExists(projectId);
        jdbc.update(
                "UPDATE coordinator_task SET session_id = 'missing-run', status = 'RUNNING' "
                        + "WHERE business_id = ?",
                task.getId());
        jdbc.update(
                "UPDATE coordinator_dispatch SET status = 'RUNNING', lease_owner = NULL, "
                        + "lease_expires_at = NULL WHERE project_id = ?",
                projectId);

        TaskRecord terminal = runUntilTerminal(projectId);
        assertEquals("FAILED", terminal.getStatus());
        assertEquals("FAILED", dispatchStatus(dispatchId(projectId)));
    }

    private void submitMessage(String projectId, String text) throws Exception {
        String taskBody = mockMvc.perform(post(
                        "/api/v1/projects/" + projectId + "/tasks")
                        .headers(identity())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Execution test\"}"))
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
                        .content("{\"name\":\"Execution " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private TaskRecord task(String projectId) {
        String taskId = jdbc.queryForObject(
                "SELECT business_id FROM coordinator_task WHERE project_id = ?",
                String.class,
                projectId);
        return executionRepository.findTask("tenant-execution", projectId, taskId);
    }

    private TaskRecord runUntilTaskExists(String projectId) {
        for (int attempt = 0; attempt < 30; attempt++) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM coordinator_task WHERE project_id = ?",
                    Integer.class,
                    projectId);
            if (count != null && count > 0) {
                return task(projectId);
            }
            worker.runOnce();
        }
        throw new AssertionError("Task was not created for project " + projectId);
    }

    private TaskRecord runUntilTerminal(String projectId) {
        for (int attempt = 0; attempt < 30; attempt++) {
            TaskRecord current = runUntilTaskExists(projectId);
            if ("SUCCEEDED".equals(current.getStatus())
                    || "FAILED".equals(current.getStatus())
                    || "TIMED_OUT".equals(current.getStatus())
                    || "CANCELLED".equals(current.getStatus())) {
                return current;
            }
            worker.runOnce();
        }
        throw new AssertionError("Task did not reach a terminal state for project " + projectId);
    }

    private String dispatchId(String projectId) {
        return jdbc.queryForObject(
                "SELECT business_id FROM coordinator_dispatch WHERE project_id = ?",
                String.class,
                projectId);
    }

    private String dispatchStatus(String dispatchId) {
        return jdbc.queryForObject(
                "SELECT status FROM coordinator_dispatch WHERE business_id = ?",
                String.class,
                dispatchId);
    }

    private HttpHeaders identity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", "tenant-execution");
        headers.set("X-User-Id", "execution-owner");
        return headers;
    }
}
