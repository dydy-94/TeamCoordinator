package org.cmb.application.service.impl;
import org.cmb.application.service.IdentityProvider;
import org.cmb.application.domain.RequestIdentity;
import org.cmb.application.domain.entity.TenantDO;
import org.cmb.application.domain.entity.TenantUserDO;
import org.cmb.common.config.DigitalTeamProperties;
import org.cmb.common.enums.TenantStatus;
import org.cmb.infrastructure.persistent.PlatformAdminRepository;
import org.cmb.infrastructure.persistent.TenantRepository;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.cmb.common.exception.ApiException;
import org.springframework.stereotype.Component;

/**
 * Header 身份提供者。除读取 X-Tenant-Id/X-User-Id 外,还做失败关闭的
 * 租户门禁:租户必须存在且为 ACTIVE,当前用户必须是该租户成员
 * (平台管理员豁免成员校验,可访问任意 ACTIVE 租户)。
 */
@Component
public class HeaderIdentityProvider implements IdentityProvider {

    private final TenantRepository tenants;
    private final List<String> platformAdmins;
    private final PlatformAdminRepository platformAdminTable;

    public HeaderIdentityProvider(
            TenantRepository tenants,
            DigitalTeamProperties properties,
            PlatformAdminRepository platformAdminTable) {
        this.tenants = tenants;
        this.platformAdmins = properties.getPlatform().getAdminUsers();
        this.platformAdminTable = platformAdminTable;
    }

    @Override
    public RequestIdentity currentIdentity(HttpServletRequest request) {
        String tenantId = request.getHeader("X-Tenant-Id");
        String userId = request.getHeader("X-User-Id");
        if (isBlank(tenantId) || isBlank(userId)) {
            throw ApiException.unauthorized("IDENTITY_REQUIRED", "X-Tenant-Id and X-User-Id are required.");
        }
        String tenant = tenantId.trim();
        String user = userId.trim();

        TenantDO tenantRow = tenants.findById(tenant);
        if (tenantRow == null) {
            throw ApiException.notFound("TENANT_NOT_FOUND", "Tenant was not found.");
        }
        if (tenantRow.getStatus() != TenantStatus.ACTIVE) {
            throw ApiException.forbidden("TENANT_DISABLED", "Tenant is disabled.");
        }
        if (!isPlatformAdmin(user)) {
            TenantUserDO membership = tenants.findMembership(tenant, user);
            if (membership == null) {
                throw ApiException.forbidden(
                        "TENANT_ACCESS_FORBIDDEN", "User is not a member of this tenant.");
            }
        }
        return new RequestIdentity(tenant, user);
    }

    /** 平台管理员 = PLATFORM_ADMIN_USERS 环境变量 ∪ platform_admin 表。 */
    private boolean isPlatformAdmin(String userId) {
        return platformAdmins.contains(userId) || platformAdminTable.isAdmin(userId);
    }

    @Override
    public String currentUserId(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (isBlank(userId)) {
            throw ApiException.unauthorized("IDENTITY_REQUIRED", "X-User-Id is required.");
        }
        return userId.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
