package org.cmb.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.cmb.TeamCoordinatorApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 租户管理端到端:平台管理员 CRUD/禁用/删除约束、租户管理员成员管理、
 * 权限拒绝与「我的租户列表」。
 */
@SpringBootTest(classes = TeamCoordinatorApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantAdminIntegrationTest {

    private static final String PLATFORM = "platform-admin";
    private static final String TENANT_ADMIN = "tenant-admin-user";
    private static final String PLAIN_MEMBER = "plain-member";

    @Autowired private MockMvc mockMvc;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Test
    void platformAdminCreatesListsAndDeletesEmptyTenant() throws Exception {
        String tenantId = createTenant("t-" + UUID.randomUUID(), "some-owner");

        mockMvc.perform(get("/api/v1/admin/tenants").headers(identity(PLATFORM)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.tenantId == '" + tenantId + "')]").exists());

        mockMvc.perform(delete("/api/v1/admin/tenants/" + tenantId)
                        .headers(identity(PLATFORM)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/tenants").headers(identity(PLATFORM)))
                .andExpect(jsonPath("$[?(@.tenantId == '" + tenantId + "')]").doesNotExist());
    }

    @Test
    void rejectsDuplicateTenantName() throws Exception {
        String name = "dup-" + UUID.randomUUID();
        createTenant(name, "owner-a");
        mockMvc.perform(post("/api/v1/admin/tenants")
                        .headers(identity(PLATFORM))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"ownerUserId\":\"owner-b\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_NAME_EXISTS"));
    }

    @Test
    void disableMakesTenantInaccessible() throws Exception {
        String tenantId = createTenant("t-" + UUID.randomUUID(), "some-owner");

        mockMvc.perform(post("/api/v1/admin/tenants/" + tenantId + "/disable")
                        .headers(identity(PLATFORM)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects")
                        .headers(identity(tenantId, "some-owner")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_DISABLED"));
    }

    @Test
    void refusesToDeleteTenantWithProjects() throws Exception {
        String tenantId = createTenant("t-" + UUID.randomUUID(), "some-owner");
        mockMvc.perform(post("/api/v1/projects")
                        .headers(identity(tenantId, "some-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"p-" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/admin/tenants/" + tenantId)
                        .headers(identity(PLATFORM)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_HAS_PROJECTS"));
    }

    @Test
    void nonAdminCannotUseAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tenants").headers(identity("stranger")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLATFORM_ADMIN_REQUIRED"));

        mockMvc.perform(post("/api/v1/admin/tenants")
                        .headers(identity("stranger"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"ownerUserId\":\"y\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLATFORM_ADMIN_REQUIRED"));
    }

    @Test
    void tenantAdminManagesOwnMembers() throws Exception {
        String tenantId = createTenant("t-" + UUID.randomUUID(), TENANT_ADMIN);

        // 租户管理员可赋权成员
        mockMvc.perform(post("/api/v1/tenants/" + tenantId + "/members")
                        .headers(identity(TENANT_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + PLAIN_MEMBER + "\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isOk());

        // 普通成员不可管理
        mockMvc.perform(post("/api/v1/tenants/" + tenantId + "/members")
                        .headers(identity(PLAIN_MEMBER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"x\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_MANAGE_FORBIDDEN"));

        // 普通成员已可访问该租户(门禁放行)
        mockMvc.perform(get("/api/v1/projects").headers(identity(tenantId, PLAIN_MEMBER)))
                .andExpect(status().isOk());

        // 移除普通成员
        mockMvc.perform(delete("/api/v1/tenants/" + tenantId + "/members/" + PLAIN_MEMBER)
                        .headers(identity(TENANT_ADMIN)))
                .andExpect(status().isNoContent());

        // 负责人不可移除
        mockMvc.perform(delete("/api/v1/tenants/" + tenantId + "/members/" + TENANT_ADMIN)
                        .headers(identity(PLATFORM)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_OWNER_REMOVAL_FORBIDDEN"));
    }

    @Test
    void lastTenantAdminCannotBeDemotedOrRemoved() throws Exception {
        String tenantId = createTenant("t-" + UUID.randomUUID(), "owner-x");
        // 再赋权一个管理员,然后尝试降级最后一个(此时只有 owner-x 一个管理员)
        mockMvc.perform(post("/api/v1/tenants/" + tenantId + "/members")
                        .headers(identity(PLATFORM))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"owner-x\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_LAST_ADMIN_REMOVAL_FORBIDDEN"));
    }

    @Test
    void listsMyTenantsWithRole() throws Exception {
        String tenantId = createTenant("t-" + UUID.randomUUID(), "my-owner");
        mockMvc.perform(post("/api/v1/admin/tenants/" + tenantId + "/members")
                        .headers(identity(PLATFORM))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"me-user\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tenants").headers(identity("me-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.tenantId == '" + tenantId
                        + "' && @.role == 'MEMBER')]").exists());
    }

    private String createTenant(String name, String ownerUserId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/tenants")
                        .headers(identity(PLATFORM))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"ownerUserId\":\""
                                + ownerUserId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("tenantId").asText();
    }

    private HttpHeaders identity(String userId) {
        return identity("tenant-admin-scope", userId);
    }

    private HttpHeaders identity(String tenantId, String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Tenant-Id", tenantId);
        headers.add("X-User-Id", userId);
        return headers;
    }
}
