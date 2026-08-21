package org.cmb.application.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.cmb.application.domain.entity.TenantDO;
import org.cmb.application.domain.entity.TenantUserDO;
import org.cmb.application.dto.TenantMemberView;
import org.cmb.application.dto.TenantRequests.AssignMember;
import org.cmb.application.dto.TenantRequests.CreateTenant;
import org.cmb.application.dto.TenantRequests.UpdateTenant;
import org.cmb.application.dto.TenantView;
import org.cmb.application.service.TenantService;
import org.cmb.common.config.DigitalTeamProperties;
import org.cmb.common.enums.TenantRole;
import org.cmb.common.enums.TenantStatus;
import org.cmb.common.exception.ApiException;
import org.cmb.infrastructure.persistent.PlatformAdminRepository;
import org.cmb.infrastructure.persistent.TenantRepository;
import org.springframework.stereotype.Service;

/**
 * 租户管理实现。授权规则:平台管理员(env 列表)可管理全部租户;
 * TENANT_ADMIN 可管理本租户成员与基本信息;负责人不可被移除;
 * 最后一个 TENANT_ADMIN 不可被移除/降级。
 */
@Service
public class TenantServiceImpl implements TenantService {

    private final TenantRepository repository;
    private final List<String> platformAdmins;
    private final PlatformAdminRepository platformAdminTable;

    public TenantServiceImpl(
            TenantRepository repository,
            DigitalTeamProperties properties,
            PlatformAdminRepository platformAdminTable) {
        this.repository = repository;
        this.platformAdmins = properties.getPlatform().getAdminUsers();
        this.platformAdminTable = platformAdminTable;
    }

    @Override
    public List<TenantView> listMyTenants(String userId) {
        // 平台管理员可见全部租户(豁免成员关系)
        if (isPlatformAdmin(userId)) {
            List<TenantView> result = new ArrayList<>();
            for (TenantDO tenant : repository.listAll()) {
                TenantView view = toView(tenant);
                view.setRole(TenantRole.TENANT_ADMIN.name());
                result.add(view);
            }
            return result;
        }
        List<TenantView> result = new ArrayList<>();
        for (TenantUserDO membership : repository.listMembershipsByUser(userId)) {
            TenantDO tenant = repository.findById(membership.getTenantId());
            if (tenant == null) {
                continue;
            }
            TenantView view = toView(tenant);
            view.setRole(membership.getRole().name());
            result.add(view);
        }
        return result;
    }

    @Override
    public List<TenantView> listAllTenants(String actorUserId) {
        requirePlatformAdmin(actorUserId);
        List<TenantView> result = new ArrayList<>();
        for (TenantDO tenant : repository.listAll()) {
            result.add(toView(tenant));
        }
        return result;
    }

    @Override
    public TenantView createTenant(String actorUserId, CreateTenant request) {
        requirePlatformAdmin(actorUserId);
        if (repository.findByName(request.getName().trim()) != null) {
            throw ApiException.conflict(
                    "TENANT_NAME_EXISTS", "A tenant with this name already exists.");
        }
        TenantDO tenant = new TenantDO();
        tenant.setBusinessId("tenant-" + UUID.randomUUID());
        tenant.setName(request.getName().trim());
        tenant.setDescription(request.getDescription());
        tenant.setOwnerUserId(request.getOwnerUserId().trim());
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setCreatedBy(actorUserId);
        repository.insert(tenant);
        repository.upsertMember(
                tenant.getBusinessId(), tenant.getOwnerUserId(), TenantRole.TENANT_ADMIN);
        return toView(repository.findById(tenant.getBusinessId()));
    }

    @Override
    public TenantView updateTenant(
            String actorUserId, String tenantId, UpdateTenant request) {
        requirePlatformAdmin(actorUserId);
        TenantDO tenant = requireTenant(tenantId);
        String name = request.getName() == null
                ? tenant.getName() : request.getName().trim();
        String description = request.getDescription() == null
                ? tenant.getDescription() : request.getDescription();
        String ownerUserId = request.getOwnerUserId() == null
                ? tenant.getOwnerUserId() : request.getOwnerUserId().trim();
        repository.update(tenantId, name, description, ownerUserId);
        return toView(repository.findById(tenantId));
    }

    @Override
    public TenantView updateTenantInfo(
            String actorUserId, String tenantId, UpdateTenant request) {
        TenantDO tenant = requireManageable(actorUserId, tenantId);
        String name = request.getName() == null
                ? tenant.getName() : request.getName().trim();
        String description = request.getDescription() == null
                ? tenant.getDescription() : request.getDescription();
        repository.update(tenantId, name, description, tenant.getOwnerUserId());
        return toView(repository.findById(tenantId));
    }

    @Override
    public void disableTenant(String actorUserId, String tenantId) {
        requirePlatformAdmin(actorUserId);
        requireTenant(tenantId);
        repository.updateStatus(tenantId, TenantStatus.DISABLED);
    }

    @Override
    public void deleteTenant(String actorUserId, String tenantId) {
        requirePlatformAdmin(actorUserId);
        requireTenant(tenantId);
        if (repository.countProjects(tenantId) > 0) {
            throw ApiException.conflict(
                    "TENANT_HAS_PROJECTS",
                    "Tenant still has projects; disable it instead of deleting.");
        }
        repository.delete(tenantId);
    }

    @Override
    public List<TenantMemberView> assignMember(
            String actorUserId, String tenantId, AssignMember request) {
        requireManageable(actorUserId, tenantId);
        TenantRole role = parseRole(request.getRole());
        String userId = request.getUserId().trim();
        TenantUserDO existing = repository.findMembership(tenantId, userId);
        if (existing != null
                && existing.getRole() == TenantRole.TENANT_ADMIN
                && role != TenantRole.TENANT_ADMIN
                && repository.countAdmins(tenantId) == 1) {
            throw ApiException.conflict(
                    "TENANT_LAST_ADMIN_REMOVAL_FORBIDDEN",
                    "The last tenant administrator cannot be demoted.");
        }
        repository.upsertMember(tenantId, userId, role);
        return listMembersView(tenantId);
    }

    @Override
    public void removeMember(String actorUserId, String tenantId, String userId) {
        TenantDO tenant = requireManageable(actorUserId, tenantId);
        if (userId.equals(tenant.getOwnerUserId())) {
            throw ApiException.conflict(
                    "TENANT_OWNER_REMOVAL_FORBIDDEN",
                    "The tenant owner cannot be removed.");
        }
        TenantUserDO membership = repository.findMembership(tenantId, userId);
        if (membership == null) {
            return;
        }
        if (membership.getRole() == TenantRole.TENANT_ADMIN
                && repository.countAdmins(tenantId) == 1) {
            throw ApiException.conflict(
                    "TENANT_LAST_ADMIN_REMOVAL_FORBIDDEN",
                    "The last tenant administrator cannot be removed.");
        }
        repository.deleteMember(tenantId, userId);
    }

    @Override
    public List<TenantMemberView> listMembers(String actorUserId, String tenantId) {
        requireManageable(actorUserId, tenantId);
        return listMembersView(tenantId);
    }

    private List<TenantMemberView> listMembersView(String tenantId) {
        List<TenantMemberView> result = new ArrayList<>();
        for (TenantUserDO membership : repository.listMembers(tenantId)) {
            TenantMemberView view = new TenantMemberView();
            view.setUserId(membership.getUserId());
            view.setRole(membership.getRole().name());
            view.setCreatedAt(membership.getCreatedAt());
            result.add(view);
        }
        return result;
    }

    private TenantDO requireTenant(String tenantId) {
        TenantDO tenant = repository.findById(tenantId);
        if (tenant == null) {
            throw ApiException.notFound("TENANT_NOT_FOUND", "Tenant was not found.");
        }
        return tenant;
    }

    /** 平台管理员或该租户的 TENANT_ADMIN 可管理租户。 */
    private TenantDO requireManageable(String actorUserId, String tenantId) {
        if (isPlatformAdmin(actorUserId)) {
            return requireTenant(tenantId);
        }
        TenantDO tenant = requireTenant(tenantId);
        TenantUserDO membership = repository.findMembership(tenantId, actorUserId);
        if (membership == null || membership.getRole() != TenantRole.TENANT_ADMIN) {
            throw ApiException.forbidden(
                    "TENANT_MANAGE_FORBIDDEN",
                    "Only the platform administrator or a tenant administrator "
                            + "can manage this tenant.");
        }
        return tenant;
    }

    private void requirePlatformAdmin(String userId) {
        if (!isPlatformAdmin(userId)) {
            throw ApiException.forbidden(
                    "PLATFORM_ADMIN_REQUIRED",
                    "Platform administrator permission is required.");
        }
    }

    /** 平台管理员 = PLATFORM_ADMIN_USERS 环境变量 ∪ platform_admin 表。 */
    private boolean isPlatformAdmin(String userId) {
        return platformAdmins.contains(userId) || platformAdminTable.isAdmin(userId);
    }

    private TenantRole parseRole(String role) {
        try {
            return TenantRole.valueOf(role);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(
                    "TENANT_ROLE_INVALID", "Role must be TENANT_ADMIN or MEMBER.");
        }
    }

    private TenantView toView(TenantDO tenant) {
        TenantView view = new TenantView();
        view.setTenantId(tenant.getBusinessId());
        view.setName(tenant.getName());
        view.setDescription(tenant.getDescription());
        view.setOwnerUserId(tenant.getOwnerUserId());
        view.setStatus(tenant.getStatus().name());
        view.setCreatedAt(tenant.getCreatedAt());
        view.setUpdatedAt(tenant.getUpdatedAt());
        return view;
    }
}
