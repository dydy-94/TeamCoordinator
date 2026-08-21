package org.cmb.application.dto;

import java.time.Instant;

/**
 * 租户视图(列表/详情共用)。{@code role} 仅在「我的租户列表」
 * 中填充(当前用户在该租户的角色)。
 */
public class TenantView {

    private String tenantId;
    private String name;
    private String description;
    private String ownerUserId;
    private String status;
    private String role;
    private Instant createdAt;
    private Instant updatedAt;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
