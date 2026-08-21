package org.cmb.application.domain.entity;
import org.cmb.common.enums.TenantRole;

import java.time.Instant;

/**
 * Row type for digital_team_tenant_user — a tenant-to-external-user
 * assignment. The user id comes from the external login system; this
 * service stores no user entity of its own.
 */
public class TenantUserDO {

    private Long databaseId;
    private String tenantId;
    private String userId;
    private TenantRole role;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getDatabaseId() { return databaseId; }
    public void setDatabaseId(Long value) { this.databaseId = value; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public TenantRole getRole() { return role; }
    public void setRole(TenantRole role) { this.role = role; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
