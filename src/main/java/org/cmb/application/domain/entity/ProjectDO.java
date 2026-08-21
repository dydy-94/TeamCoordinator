package org.cmb.application.domain.entity;
import org.cmb.common.enums.ProjectStatus;

import java.time.Instant;

public class ProjectDO {

    private Long databaseId;
    private String businessId;
    private String tenantId;
    private String name;
    private String description;
    private ProjectStatus status;
    private String coordinatorAgentId;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getDatabaseId() { return databaseId; }
    public void setDatabaseId(Long value) { this.databaseId = value; }
    public String getBusinessId() { return businessId; }
    public void setBusinessId(String value) { this.businessId = value; }
    public String getId() { return businessId; }
    public void setId(String value) { this.businessId = value; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }
    public String getCoordinatorAgentId() { return coordinatorAgentId; }
    public void setCoordinatorAgentId(String v) { this.coordinatorAgentId = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
