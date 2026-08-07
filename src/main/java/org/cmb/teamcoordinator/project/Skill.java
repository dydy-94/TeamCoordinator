package org.cmb.teamcoordinator.project;

import java.time.Instant;

/**
 * Represents a built-in skill provided by the platform's AgentCore.
 * Skills are predefined capabilities that projects can enable for their expert agents.
 */
public class Skill {

    private Long databaseId;
    private String businessId;
    private String name;
    private String description;
    private String prompt;
    private boolean enabled = true;
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
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
