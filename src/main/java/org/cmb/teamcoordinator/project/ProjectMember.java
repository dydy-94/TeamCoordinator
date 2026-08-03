package org.cmb.teamcoordinator.project;

public class ProjectMember {

    private String userId;
    private ProjectRole role;

    public ProjectMember() {}

    public ProjectMember(String userId, ProjectRole role) {
        this.userId = userId;
        this.role = role;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public ProjectRole getRole() { return role; }
    public void setRole(ProjectRole role) { this.role = role; }
}
