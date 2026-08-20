package org.cmb.integration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.cmb.TeamCoordinatorApplication;
import org.cmb.common.exception.ApiException;
import org.cmb.application.service.ProjectService;
import org.cmb.application.domain.RequestIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = TeamCoordinatorApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectPermissionIntegrationTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final String OWNER = "owner-a";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProjectService projectService;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void enforcesProjectRoleTenantIdempotencyAndArchiveRules() throws Exception {
        String projectId = createProject();

        mockMvc.perform(get("/api/v1/projects/" + projectId).headers(identity(TENANT_A, OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].role").value("OWNER"));

        upsertMember(projectId, "member-a", "MEMBER");
        upsertMember(projectId, "viewer-a", "VIEWER");
        upsertMember(projectId, "viewer-a", "VIEWER");

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/experts")
                        .headers(identity(TENANT_A, OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expertId\":\"expert-analysis\",\"enabled\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/experts")
                        .headers(identity(TENANT_A, OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expertId\":\"expert-analysis\",\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experts[0].enabled").value(false));

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/members")
                        .headers(identity(TENANT_A, "member-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"other\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_MANAGE_FORBIDDEN"));

        mockMvc.perform(get("/api/v1/projects/" + projectId)
                        .headers(identity(TENANT_A, "not-a-member")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/projects/" + projectId)
                        .headers(identity(TENANT_B, OWNER)))
                .andExpect(status().isNotFound());

        assertThrows(
                ApiException.class,
                () -> projectService.requireTaskInitiator(
                        new RequestIdentity(TENANT_A, "viewer-a"), projectId));
        projectService.requireTaskInitiator(
                new RequestIdentity(TENANT_A, "member-a"), projectId);

        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/members/member-a")
                        .headers(identity(TENANT_A, OWNER)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/projects/" + projectId)
                        .headers(identity(TENANT_A, "member-a")))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/projects/" + projectId)
                        .headers(identity(TENANT_A, OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/experts")
                        .headers(identity(TENANT_A, OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expertId\":\"expert-writing\",\"enabled\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
        assertThrows(
                ApiException.class,
                () -> projectService.requireTaskInitiator(
                        new RequestIdentity(TENANT_A, OWNER), projectId));

        Integer viewerCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_member WHERE tenant_id = ? AND project_id = ? "
                        + "AND user_id = ?",
                Integer.class,
                TENANT_A,
                projectId,
                "viewer-a");
        Integer auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM permission_audit_log WHERE tenant_id = ? AND project_id = ?",
                Integer.class,
                TENANT_A,
                projectId);
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(1), viewerCount);
        org.junit.jupiter.api.Assertions.assertTrue(auditCount != null && auditCount >= 6);
    }

    private String createProject() throws Exception {
        String name = "Project " + UUID.randomUUID();
        String body = mockMvc.perform(post("/api/v1/projects")
                        .headers(identity(TENANT_A, OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"description\":\"MVP\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        return response.get("id").asText();
    }

    private void upsertMember(String projectId, String userId, String role) throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/members")
                        .headers(identity(TENANT_A, OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isOk());
    }

    private org.springframework.http.HttpHeaders identity(String tenantId, String userId) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Tenant-Id", tenantId);
        headers.set("X-User-Id", userId);
        return headers;
    }
}
