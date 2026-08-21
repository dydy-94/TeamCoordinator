package org.cmb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.cmb.TeamCoordinatorApplication;
import org.cmb.application.domain.entity.DispatchWorkDO;
import org.cmb.infrastructure.persistent.ExecutionRepository;
import org.cmb.infrastructure.worker.SingleExpertWorker;
import org.cmb.application.domain.entity.TaskDO;
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
 * Covers the execution-engine fault tolerance fixes: lease renewal,
 * transient AgentCore failure tolerance, stranded STARTING recovery,
 * retryable expert-capacity exhaustion, plan/task status consistency,
 * and the sequence-guard-free cancel transition.
 *
 * The test profile sets digital-team.execution.agentcore-failure-threshold
 * to 3 so failure counting can be exercised quickly.
 *
 * claimNext() always picks the oldest claimable dispatch, so tests that
 * drive worker.runOnce() must not assume their own dispatch is processed
 * on a specific tick; assertions are written against reachable states.
 */
@SpringBootTest(classes = TeamCoordinatorApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExecutionFaultToleranceIntegrationTest {

    private static final String TENANT = "tenant-fault";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SingleExpertWorker worker;
    @Autowired private ExecutionRepository executionRepository;

    @Test
    void renewsLeaseOnlyWhileOwnerHoldsDispatch() throws Exception {
        String projectId = createProject();
        submitMessage(projectId, "续租测试");
        // claimNext may hand back an older leftover dispatch; the renewal
        // semantics hold for whichever dispatch we actually own.
        DispatchWorkDO claimed = executionRepository.claimNext("instance-a", 30);
        assertNotNull(claimed);
        String claimedId = claimed.getDispatchId();

        // Wrong owner must not be able to extend the lease.
        assertEquals(0, executionRepository.renewLease(claimedId, "instance-b", 30));
        // Correct owner extends it beyond the renewal interval.
        assertEquals(1, executionRepository.renewLease(claimedId, "instance-a", 30));
        Timestamp expires = jdbc.queryForObject(
                "SELECT lease_expires_at FROM digital_team_coordinator_dispatch WHERE business_id = ?",
                Timestamp.class,
                claimedId);
        assertNotNull(expires);
        assertTrue(expires.toInstant().isAfter(Instant.now().plusSeconds(20)));

        // Once released, even the previous owner cannot renew.
        executionRepository.releaseDispatch(claimedId);
        assertEquals(0, executionRepository.renewLease(claimedId, "instance-a", 30));

        // Leave the dispatch terminal so it does not steal ticks from
        // other tests in the shared claim pool.
        jdbc.update(
                "UPDATE digital_team_coordinator_dispatch SET status = 'COMPLETED' WHERE business_id = ?",
                claimedId);
    }

    @Test
    void toleratesTransientAgentCoreFailuresUpToThreshold() throws Exception {
        String projectId = createProject();
        submitMessage(projectId, "瞬时故障容忍");
        TaskDO task = runUntilTaskExists(projectId);
        makeDispatchClaimable(projectId);
        jdbc.update(
                "UPDATE digital_team_coordinator_task SET session_id = 'missing-run', status = 'RUNNING' "
                        + "WHERE business_id = ?",
                task.getId());

        // Drive ticks until the task fails; it must survive while the
        // consecutive-failure count stays below the threshold of 3.
        boolean failedEarly = false;
        TaskDO current = null;
        for (int i = 0; i < 30; i++) {
            worker.runOnce();
            current = task(projectId);
            int failures = consecutiveFailures(task.getId());
            if ("FAILED".equals(current.getStatus())) {
                failedEarly = failures < 3;
                break;
            }
        }
        assertNotNull(current);
        assertEquals("FAILED", current.getStatus());
        assertFalse(failedEarly, "Task failed before the failure threshold");
        // Exactly the threshold was reached before failing; no further
        // increments happen once the task is terminal.
        assertEquals(3, consecutiveFailures(task.getId()));
        assertEquals("FAILED", dispatchStatus(dispatchId(projectId)));
    }

    @Test
    void recoversStrandedStartingTasks() throws Exception {
        String projectId = createProject();
        submitMessage(projectId, "恢复 STARTING 卡死");
        TaskDO task = runUntilTaskExists(projectId);
        // Simulate a crash between submitRun and saveSession: STARTING with
        // no session and an old updated_at, plus a claimable dispatch.
        String planId = jdbc.queryForObject(
                "SELECT plan_id FROM digital_team_coordinator_task WHERE business_id = ?",
                String.class,
                task.getId());
        jdbc.update(
                "UPDATE digital_team_coordinator_plan SET status = 'RUNNING' WHERE business_id = ?",
                planId);
        jdbc.update(
                "UPDATE digital_team_coordinator_task SET status = 'STARTING', session_id = NULL, "
                        + "updated_at = ? WHERE business_id = ?",
                Timestamp.from(Instant.now().minusSeconds(120)),
                task.getId());
        makeDispatchClaimable(projectId);

        // Without recovery the task would stay STARTING forever; within a
        // bounded number of ticks it must move back into the pipeline.
        boolean recovered = false;
        for (int i = 0; i < 30 && !recovered; i++) {
            worker.runOnce();
            recovered = !"STARTING".equals(task(projectId).getStatus());
        }
        assertTrue(recovered, "Stranded STARTING task was not recovered");

        assertEquals("SUCCEEDED", runUntilTerminal(projectId).getStatus());
    }

    @Test
    void expertCapacityExhaustionIsRetryableNotFatal() throws Exception {
        String loadProject = createProject();
        submitMessage(loadProject, "负载项目");
        TaskDO loadTask = runUntilTaskExists(loadProject);
        // Fake RUNNING tasks push every mock expert (concurrency limit 2) to
        // its limit, so no candidate is available for new dispatches.
        List<String> expertIds = Arrays.asList(
                "expert-analysis", "expert-writing", "expert-file",
                "expert-ui", "expert-backend");
        for (String expertId : expertIds) {
            for (int i = 0; i < 2; i++) {
                jdbc.update(
                        "INSERT INTO digital_team_coordinator_task "
                                + "(business_id, tenant_id, project_id, plan_id, task_key, "
                                + "request_id, expert_id, status, objective, last_sequence) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'RUNNING', 'fake load', 0)",
                        "fake-" + UUID.randomUUID(),
                        TENANT,
                        loadProject,
                        loadTask.getPlanId(),
                        "fake-" + expertId + "-" + i,
                        "fake-request-" + expertId + "-" + i,
                        expertId);
            }
        }
        // Take the load project's dispatch out of the claim pool so it does
        // not monopolize claimNext for the rest of the test.
        jdbc.update(
                "UPDATE digital_team_coordinator_dispatch SET status = 'COMPLETED' WHERE project_id = ?",
                loadProject);

        String projectId = createProject();
        submitMessage(projectId, "无专家容量");
        runUntilTaskExists(projectId);
        // Every subsequent tick finds no candidate: the task must stay
        // PENDING and the dispatch must stay RUNNING (retry), never fail.
        for (int i = 0; i < 3; i++) {
            worker.runOnce();
            assertEquals("PENDING", task(projectId).getStatus());
            assertEquals("RUNNING", dispatchStatus(dispatchId(projectId)));
        }

        // Capacity frees up: the message completes.
        jdbc.update("DELETE FROM digital_team_coordinator_task WHERE request_id LIKE 'fake-request-%'");
        assertEquals("SUCCEEDED", runUntilTerminal(projectId).getStatus());

        // Leave no RUNNING task behind: the load project's dispatch was
        // completed above, so its real task would otherwise occupy an
        // expert slot forever and poison the shared expert pool.
        jdbc.update(
                "UPDATE digital_team_coordinator_task SET status = 'FAILED' WHERE project_id = ? "
                        + "AND request_id NOT LIKE 'fake-request-%'",
                loadProject);
    }

    @Test
    void failTasksForMessageAlsoFailsPlan() throws Exception {
        String projectId = createProject();
        submitMessage(projectId, "失败一致性");
        TaskDO task = runUntilTaskExists(projectId);
        jdbc.update(
                "UPDATE digital_team_coordinator_task SET status = 'RUNNING' WHERE business_id = ?",
                task.getId());
        String messageId = jdbc.queryForObject(
                "SELECT message_id FROM digital_team_coordinator_plan WHERE business_id = ?",
                String.class,
                task.getPlanId());

        executionRepository.failTasksForMessage(TENANT, messageId);

        assertEquals("FAILED", jdbc.queryForObject(
                "SELECT status FROM digital_team_coordinator_plan WHERE business_id = ?",
                String.class,
                task.getPlanId()));
        assertEquals("FAILED", task(projectId).getStatus());

        // Terminal dispatch: keep it out of the shared claim pool.
        jdbc.update(
                "UPDATE digital_team_coordinator_dispatch SET status = 'FAILED' WHERE project_id = ?",
                projectId);
    }

    @Test
    void cancelTransitionIgnoresSequenceGuard() throws Exception {
        String projectId = createProject();
        submitMessage(projectId, "取消防守卫");
        TaskDO task = runUntilTaskExists(projectId);
        // A stale last_sequence far above the synthetic cancel sequence: the
        // sequence-guarded advanceTask would reject it, cancelTask must not.
        jdbc.update(
                "UPDATE digital_team_coordinator_task SET last_sequence = 1000, status = 'RUNNING' "
                        + "WHERE business_id = ?",
                task.getId());

        assertTrue(executionRepository.cancelTask(task.getId(), "CANCELLED", "{}"));
        assertEquals("CANCELLED", task(projectId).getStatus());
        // Terminal guard holds: neither event replay nor a late cancel
        // can move a CANCELLED task.
        assertFalse(executionRepository.advanceTask(task.getId(), 1001L, "RUNNING", null));
        assertFalse(executionRepository.cancelTask(task.getId(), "CANCELLED", "{}"));

        // Terminal dispatch: keep it out of the shared claim pool.
        jdbc.update(
                "UPDATE digital_team_coordinator_dispatch SET status = 'CANCELLED' WHERE project_id = ?",
                projectId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private int consecutiveFailures(String taskId) {
        Integer count = jdbc.queryForObject(
                "SELECT consecutive_failures FROM digital_team_coordinator_task WHERE business_id = ?",
                Integer.class,
                taskId);
        return count == null ? 0 : count;
    }

    private void makeDispatchClaimable(String projectId) {
        jdbc.update(
                "UPDATE digital_team_coordinator_dispatch SET status = 'RUNNING', lease_owner = NULL, "
                        + "lease_expires_at = NULL WHERE project_id = ?",
                projectId);
    }

    private void submitMessage(String projectId, String text) throws Exception {
        String taskBody = mockMvc.perform(post(
                        "/api/v1/projects/" + projectId + "/tasks")
                        .headers(identity())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Fault tolerance test\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(taskBody).get("taskId").asText();
        mockMvc.perform(post("/api/v1/projects/" + projectId
                        + "/tasks/" + taskId + "/messages")
                        .headers(identity())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_message_id\":\"client-" + UUID.randomUUID()
                                + "\",\"text\":\"" + text
                                + "\",\"attachment_refs\":[]}"))
                .andExpect(status().isAccepted());
    }

    private String createProject() throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects")
                        .headers(identity())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fault " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private TaskDO task(String projectId) {
        String taskId = jdbc.queryForObject(
                "SELECT business_id FROM digital_team_coordinator_task WHERE project_id = ? "
                        + "AND request_id NOT LIKE 'fake-request-%' ORDER BY created_at LIMIT 1",
                String.class,
                projectId);
        return executionRepository.findTask(TENANT, projectId, taskId);
    }

    private TaskDO runUntilTaskExists(String projectId) {
        for (int attempt = 0; attempt < 30; attempt++) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM digital_team_coordinator_task WHERE project_id = ?",
                    Integer.class,
                    projectId);
            if (count != null && count > 0) {
                return task(projectId);
            }
            worker.runOnce();
        }
        throw new AssertionError("Task was not created for project " + projectId);
    }

    private TaskDO runUntilTerminal(String projectId) {
        for (int attempt = 0; attempt < 60; attempt++) {
            TaskDO current = task(projectId);
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
                "SELECT business_id FROM digital_team_coordinator_dispatch WHERE project_id = ?",
                String.class,
                projectId);
    }

    private String dispatchStatus(String dispatchId) {
        return jdbc.queryForObject(
                "SELECT status FROM digital_team_coordinator_dispatch WHERE business_id = ?",
                String.class,
                dispatchId);
    }

    private HttpHeaders identity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", TENANT);
        headers.set("X-User-Id", "fault-owner");
        return headers;
    }
}
