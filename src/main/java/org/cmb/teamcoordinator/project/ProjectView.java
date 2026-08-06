package org.cmb.teamcoordinator.project;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ProjectView {

    private String id;
    private String name;
    private String description;
    private String coordinatorAgentId;
    private ProjectStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private List<ProjectMember> members = new ArrayList<>();
    private List<ProjectExpert> experts = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCoordinatorAgentId() { return coordinatorAgentId; }
    public void setCoordinatorAgentId(String v) { this.coordinatorAgentId = v; }
    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<ProjectMember> getMembers() { return members; }
    public void setMembers(List<ProjectMember> members) { this.members = members; }
    public List<ProjectExpert> getExperts() { return experts; }
    public void setExperts(List<ProjectExpert> experts) { this.experts = experts; }
}
