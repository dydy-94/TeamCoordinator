package org.cmb.presentation.controller;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.cmb.application.dto.TenantMemberView;
import org.cmb.application.dto.TenantRequests.AssignMember;
import org.cmb.application.dto.TenantRequests.UpdateTenant;
import org.cmb.application.dto.TenantView;
import org.cmb.application.service.IdentityProvider;
import org.cmb.application.service.TenantService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户自服务端点:我的租户列表(前端切换器数据源)与租户管理员操作。
 * 仅要求 X-User-Id——用户可能携带过期/无效的 X-Tenant-Id 头。
 */
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final IdentityProvider identities;
    private final TenantService tenants;

    public TenantController(IdentityProvider identities, TenantService tenants) {
        this.identities = identities;
        this.tenants = tenants;
    }

    @GetMapping
    public List<TenantView> listMyTenants(HttpServletRequest request) {
        return tenants.listMyTenants(identities.currentUserId(request));
    }

    @GetMapping("/{tenantId}/members")
    public List<TenantMemberView> listMembers(
            HttpServletRequest request, @PathVariable String tenantId) {
        return tenants.listMembers(identities.currentUserId(request), tenantId);
    }

    @PatchMapping("/{tenantId}")
    public TenantView updateTenantInfo(
            HttpServletRequest request,
            @PathVariable String tenantId,
            @Valid @RequestBody UpdateTenant body) {
        return tenants.updateTenantInfo(
                identities.currentUserId(request), tenantId, body);
    }

    @PostMapping("/{tenantId}/members")
    public List<TenantMemberView> assignMember(
            HttpServletRequest request,
            @PathVariable String tenantId,
            @Valid @RequestBody AssignMember body) {
        return tenants.assignMember(
                identities.currentUserId(request), tenantId, body);
    }

    @DeleteMapping("/{tenantId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            HttpServletRequest request,
            @PathVariable String tenantId,
            @PathVariable String userId) {
        tenants.removeMember(identities.currentUserId(request), tenantId, userId);
    }
}
