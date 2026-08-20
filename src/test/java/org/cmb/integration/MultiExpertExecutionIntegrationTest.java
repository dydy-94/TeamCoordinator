package org.cmb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.cmb.infrastructure.worker.SingleExpertWorker;
import org.cmb.infrastructure.persistent.ExecutionRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MultiExpertExecutionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SingleExpertWorker worker;
    @Autowired private ExecutionRepository executionRepository;

    @Test
    void runsDependentExpertsInOrderAndCompletesPlan() throws Exception {
        String projectId = createProject();
        submit(projectId, "分析接口风险并撰写报告");

        runUntilTaskCount(projectId, 2);
        List<String> firstStatuses = statuses(projectId);
        assertEquals(Arrays2.of("RUNNING", "PENDING"), firstStatuses);

        worker.runOnce();
        List<String> secondStatuses = statuses(projectId);
        assertEquals(Arrays2.of("SUCCEEDED", "RUNNING"), secondStatuses);

        worker.runOnce();
        assertEquals(Arrays2.of("SUCCEEDED", "SUCCEEDED"), statuses(projectId));
        assertEquals("COMPLETED", jdbc.queryForObject(
                "SELECT status FROM coordinator_dispatch WHERE project_id = ?",
                String.class, projectId));
        assertEquals(Integer.valueOf(2), jdbc.queryForObject(
                "SELECT COUNT(DISTINCT expert_id) FROM coordinator_task WHERE project_id = ?",
                Integer.class, projectId));
        assertEquals(Integer.valueOf(2), jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_artifact WHERE project_id = ? "
                        + "AND status = 'AVAILABLE'",
                Integer.class, projectId));
        String writingResult = jdbc.queryForObject(
                "SELECT result_json FROM coordinator_task WHERE project_id = ? "
                        + "AND task_key = 'write-report'",
                String.class, projectId);
        assertTrue(writingResult.contains("content"));
        assertTrue(writingResult.contains("Task completed") || writingResult.contains("Mock result"));
        assertEquals(Integer.valueOf(1), jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_artifact_lineage l "
                        + "JOIN project_artifact a ON a.business_id = l.output_artifact_id "
                        + "WHERE a.project_id = ?",
                Integer.class, projectId));
    }

    @Test
    void startsIndependentTasksTogetherBeforeFanInTask() throws Exception {
        String projectId = createProject();
        submit(projectId, "并行分析两个方面并撰写报告");

        runUntilTaskCount(projectId, 3);
        assertEquals(java.util.Arrays.asList("RUNNING", "RUNNING", "PENDING"),
                statuses(projectId));

        worker.runOnce();
        assertEquals(java.util.Arrays.asList("SUCCEEDED", "SUCCEEDED", "RUNNING"),
                statuses(projectId));

        worker.runOnce();
        assertEquals("COMPLETED", jdbc.queryForObject(
                "SELECT status FROM coordinator_dispatch WHERE project_id = ?",
                String.class, projectId));
    }

    private List<String> statuses(String projectId) {
        return jdbc.queryForList(
                "SELECT status FROM coordinator_task WHERE project_id = ? ORDER BY created_at, task_key",
                String.class, projectId);
    }

    private void runUntilTaskCount(String projectId, int expected) {
        for (int attempt = 0; attempt < 50; attempt++) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM coordinator_task WHERE project_id = ?",
                    Integer.class, projectId);
            if (count != null && count >= expected) {
                return;
            }
            worker.runOnce();
        }
        throw new AssertionError("Expected " + expected + " tasks for " + projectId);
    }

    private String createProject() throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects")
                        .headers(identity())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Multi " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("id").asText();
    }

    private void submit(String projectId, String text) throws Exception {
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

    private HttpHeaders identity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", "tenant-multi");
        headers.set("X-User-Id", "multi-owner");
        return headers;
    }

    private static final class Arrays2 {
        private static List<String> of(String first, String second) {
            return java.util.Arrays.asList(first, second);
        }
    }
}
