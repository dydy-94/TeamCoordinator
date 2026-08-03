package org.cmb.teamcoordinator.integration;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.cmb.teamcoordinator.TeamCoordinatorApplication;
import org.cmb.teamcoordinator.coordinator.EventVisibility;
import org.cmb.teamcoordinator.coordinator.MessageEventRepository;
import org.cmb.teamcoordinator.coordinator.ProjectEventStreamHub;
import org.cmb.teamcoordinator.coordinator.ProjectEventType;
import org.cmb.teamcoordinator.execution.DispatchWork;
import org.cmb.teamcoordinator.execution.ExecutionRepository;
import org.cmb.teamcoordinator.project.RequestIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = TeamCoordinatorApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectMessageEventIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MessageEventRepository eventRepository;
    @Autowired private ProjectEventStreamHub streamHub;
    @Autowired private ExecutionRepository executionRepository;

    @Test
    void persistsIdempotentMessageAndReplaysEventsAfterCursor() throws Exception {
        String projectId = createProject("message-owner");
        String requestBody = "{\"client_message_id\":\"client-1\",\"text\":\"analyze this\","
                + "\"attachment_refs\":[\"file-1\"],\"idempotency_key\":\"idem-1\"}";

        String first = mockMvc.perform(post("/api/v1/projects/" + projectId + "/messages")
                        .headers(identity("tenant-message", "message-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String duplicate = mockMvc.perform(post("/api/v1/projects/" + projectId + "/messages")
                        .headers(identity("tenant-message", "message-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode firstJson = objectMapper.readTree(first);
        JsonNode duplicateJson = objectMapper.readTree(duplicate);
        assertEquals(firstJson.get("messageId").asText(), duplicateJson.get("messageId").asText());

        Integer messageCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_message WHERE tenant_id = ? AND project_id = ?",
                Integer.class,
                "tenant-message",
                projectId);
        Integer dispatchCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM coordinator_dispatch WHERE tenant_id = ? AND project_id = ? "
                        + "AND status = 'PENDING'",
                Integer.class,
                "tenant-message",
                projectId);
        assertEquals(Integer.valueOf(1), messageCount);
        assertEquals(Integer.valueOf(1), dispatchCount);

        MvcResult stream = mockMvc.perform(get("/api/v1/projects/" + projectId + "/events")
                        .headers(identity("tenant-message", "message-owner"))
                        .header("Last-Event-ID", "0"))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("Content-Type", startsWith("text/event-stream")))
                .andReturn();
        String events = stream.getResponse().getContentAsString();
        assertTrue(events.contains("event:COORDINATOR_ANALYZING"));
        assertTrue(events.contains("id:2"));
        assertTrue(!events.contains("MESSAGE_ACCEPTED_INTERNAL"));

        MvcResult afterCursor = mockMvc.perform(get("/api/v1/projects/" + projectId + "/events")
                        .headers(identity("tenant-message", "message-owner"))
                        .header("Last-Event-ID", "2"))
                .andExpect(request().asyncStarted())
                .andReturn();
        assertEquals("", afterCursor.getResponse().getContentAsString());

        String liveRequest = "{\"client_message_id\":\"client-2\",\"text\":\"follow up\","
                + "\"attachment_refs\":[],\"idempotency_key\":\"idem-2\"}";
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/messages")
                        .headers(identity("tenant-message", "message-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(liveRequest))
                .andExpect(status().isAccepted());
        String liveEvents = afterCursor.getResponse().getContentAsString();
        assertTrue(liveEvents.contains("event:COORDINATOR_ANALYZING"));
        assertTrue(liveEvents.contains("id:4"));

        eventRepository.insertEvent(
                new RequestIdentity("tenant-message", "remote-instance"),
                projectId,
                firstJson.get("conversationId").asText(),
                null,
                ProjectEventType.PLAN_CREATED,
                EventVisibility.PUBLIC,
                objectMapper.createObjectNode().put("source", "remote-instance"));
        streamHub.pollDatabaseEvents();
        String databaseEvents = afterCursor.getResponse().getContentAsString();
        assertTrue(databaseEvents.contains("event:PLAN_CREATED"));
        assertTrue(databaseEvents.contains("id:5"));

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/events")
                        .headers(identity("tenant-message", "outsider")))
                .andExpect(status().isNotFound());
    }

    @Test
    void serializesMessagesWithinOneProjectAcrossInstances() throws Exception {
        jdbc.update("UPDATE coordinator_dispatch SET status = 'COMPLETED'");
        String projectId = createProject("message-owner");
        submit(projectId, "serial-client-1", "first");
        submit(projectId, "serial-client-2", "second");

        DispatchWork first = executionRepository.claimNext("instance-a", 30);
        assertEquals(projectId, first.getProjectId());
        executionRepository.releaseDispatch(first.getDispatchId());

        DispatchWork retriedFirst = executionRepository.claimNext("instance-b", 30);
        assertEquals(first.getDispatchId(), retriedFirst.getDispatchId());
        jdbc.update(
                "UPDATE coordinator_dispatch SET status = 'COMPLETED', "
                        + "lease_owner = NULL, lease_expires_at = NULL WHERE id = ?",
                first.getDispatchId());

        DispatchWork second = executionRepository.claimNext("instance-b", 30);
        assertEquals(projectId, second.getProjectId());
        assertTrue(!first.getDispatchId().equals(second.getDispatchId()));
    }

    private void submit(String projectId, String clientId, String text) throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/messages")
                        .headers(identity("tenant-message", "message-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_message_id\":\"" + clientId
                                + "\",\"text\":\"" + text
                                + "\",\"attachment_refs\":[],\"idempotency_key\":\""
                                + clientId + "\"}"))
                .andExpect(status().isAccepted());
    }

    private String createProject(String owner) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects")
                        .headers(identity("tenant-message", owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Messages " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private HttpHeaders identity(String tenantId, String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", tenantId);
        headers.set("X-User-Id", userId);
        return headers;
    }
}
