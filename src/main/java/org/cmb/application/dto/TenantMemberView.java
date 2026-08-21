package org.cmb.application.dto;

import java.time.Instant;

/**
 * 租户成员视图(外部 userId + 角色)。
 */
public class TenantMemberView {

    private String userId;
    private String role;
    private Instant createdAt;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
