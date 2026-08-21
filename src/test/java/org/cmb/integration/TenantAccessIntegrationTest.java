package org.cmb.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.cmb.TeamCoordinatorApplication;
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
 * 租户门禁(失败关闭):未知租户 404、禁用租户 403、非成员 403、
 * 合法成员放行;公共端点与 mock 端点不受影响。
 */
@SpringBootTest(classes = TeamCoordinatorApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantAccessIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void rejectsUnknownTenant() throws Exception {
        mockMvc.perform(get("/api/v1/projects")
                        .headers(identity("tenant-ghost", "anybody")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
    }

    @Test
    void rejectsNonMember() throws Exception {
        // tenant-message 存在,但 stranger-<uuid> 从未被赋权
        String stranger = "stranger-" + UUID.randomUUID();
        mockMvc.perform(get("/api/v1/projects")
                        .headers(identity("tenant-message", stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_ACCESS_FORBIDDEN"));
    }

    @Test
    void platformAdminBypassesMembership() throws Exception {
        // platform-admin 未加入 tenant-message,但豁免成员校验
        mockMvc.perform(get("/api/v1/projects")
                        .headers(identity("tenant-message", "platform-admin")))
                .andExpect(status().isOk());
    }

    @Test
    void platformAdminFromTableBypassesMembership() throws Exception {
        // 直接插入 platform_admin 表项即可赋权(无 API 路径)
        String tableAdmin = "table-admin-" + UUID.randomUUID();
        jdbc.update("INSERT INTO digital_team_platform_admin (user_id) VALUES (?)",
                tableAdmin);
        mockMvc.perform(get("/api/v1/projects")
                        .headers(identity("tenant-message", tableAdmin)))
                .andExpect(status().isOk());
        // 平台管理员管理端点同样可用
        mockMvc.perform(get("/api/v1/admin/tenants")
                        .headers(identity("tenant-message", tableAdmin)))
                .andExpect(status().isOk());
    }

    @Test
    void platformAdminSeesAllTenantProjects() throws Exception {
        // message-owner 建项目,platform-admin 虽非成员也应可见
        String body = mockMvc.perform(post("/api/v1/projects")
                        .headers(identity("tenant-message", "message-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"admin-visible-" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String projectId = objectMapper.readTree(body).get("id").asText();

        mockMvc.perform(get("/api/v1/projects")
                        .headers(identity("tenant-message", "platform-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + projectId + "')]").exists());

        mockMvc.perform(get("/api/v1/projects/" + projectId)
                        .headers(identity("tenant-message", "platform-admin")))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsDisabledTenant() throws Exception {
        String disabledTenant = "tenant-disabled-" + UUID.randomUUID();
        jdbc.update("INSERT INTO digital_team_tenant "
                        + "(business_id, name, description, owner_user_id, status, created_by) "
                        + "VALUES (?, ?, '', ?, 'DISABLED', 'test')",
                disabledTenant, disabledTenant, "disabled-owner");
        jdbc.update("INSERT INTO digital_team_tenant_user (tenant_id, user_id, role) "
                + "VALUES (?, ?, 'TENANT_ADMIN')", disabledTenant, "disabled-owner");
        mockMvc.perform(get("/api/v1/projects")
                        .headers(identity(disabledTenant, "disabled-owner")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_DISABLED"));
        // 平台管理员对禁用租户同样被拒
        mockMvc.perform(get("/api/v1/projects")
                        .headers(identity(disabledTenant, "platform-admin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_DISABLED"));
    }

    @Test
    void rejectsSseForNonMember() throws Exception {
        String stranger = "stranger-" + UUID.randomUUID();
        mockMvc.perform(get("/api/v1/projects/project-x/tasks/task-y/events")
                        .headers(identity("tenant-message", stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_ACCESS_FORBIDDEN"));
    }

    @Test
    void allowsValidMember() throws Exception {
        mockMvc.perform(get("/api/v1/projects")
                        .headers(identity("tenant-message", "message-owner")))
                .andExpect(status().isOk());
    }

    @Test
    void requiresIdentityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));
    }

    @Test
    void leavesPublicEndpointsOpen() throws Exception {
        mockMvc.perform(get("/api/v1/skills")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/experts")).andExpect(status().isOk());
        mockMvc.perform(get("/health")).andExpect(status().isOk());
    }

    private HttpHeaders identity(String tenantId, String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Tenant-Id", tenantId);
        headers.add("X-User-Id", userId);
        return headers;
    }
}
