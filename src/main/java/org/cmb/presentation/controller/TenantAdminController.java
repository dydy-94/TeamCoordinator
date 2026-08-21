package org.cmb.presentation.controller;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.cmb.application.dto.TenantRequests.AssignMember;
import org.cmb.application.dto.TenantRequests.CreateTenant;
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
 * 平台管理员租户管理端点(平台管理员 = digital-team.platform.admin-users)。
 * 仅要求 X-User-Id。
 */
@RestController
@RequestMapping("/api/v1/admin/tenants")
public class TenantAdminController {

    private final IdentityProvider identities;
    private final TenantService tenants;

    public TenantAdminController(IdentityProvider identities, TenantService tenants) {
        this.identities = identities;
        this.tenants = tenants;
    }

    @GetMapping
    public List<TenantView> listAll(HttpServletRequest request) {
        return tenants.listAllTenants(identities.currentUserId(request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantView create(
            HttpServletRequest request, @Valid @RequestBody CreateTenant body) {
        return tenants.createTenant(identities.currentUserId(request), body);
    }

    @PatchMapping("/{tenantId}")
    public TenantView update(
            HttpServletRequest request,
            @PathVariable String tenantId,
            @Valid @RequestBody UpdateTenant body) {
        return tenants.updateTenant(
                identities.currentUserId(request), tenantId, body);
    }

    @PostMapping("/{tenantId}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(HttpServletRequest request, @PathVariable String tenantId) {
        tenants.disableTenant(identities.currentUserId(request), tenantId);
    }

    @DeleteMapping("/{tenantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(HttpServletRequest request, @PathVariable String tenantId) {
        tenants.deleteTenant(identities.currentUserId(request), tenantId);
    }

    @PostMapping("/{tenantId}/members")
    public void assignMember(
            HttpServletRequest request,
            @PathVariable String tenantId,
            @Valid @RequestBody AssignMember body) {
        tenants.assignMember(identities.currentUserId(request), tenantId, body);
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
