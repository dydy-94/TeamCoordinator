package org.cmb.application.domain.entity;
import org.cmb.common.enums.TenantStatus;

import java.time.Instant;

/**
 * Row type for digital_team_tenant. The tenant is the multi-tenancy root:
 * every tenant-scoped table references tenant via its {@code businessId}.
 */
public class TenantDO {

    private Long databaseId;
    private String businessId;
    private String name;
    private String description;
    private String ownerUserId;
    private TenantStatus status;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getDatabaseId() { return databaseId; }
    public void setDatabaseId(Long value) { this.databaseId = value; }
    public String getBusinessId() { return businessId; }
    public void setBusinessId(String value) { this.businessId = value; }
    public String getId() { return businessId; }
    public void setId(String value) { this.businessId = value; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }
    public TenantStatus getStatus() { return status; }
    public void setStatus(TenantStatus status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
