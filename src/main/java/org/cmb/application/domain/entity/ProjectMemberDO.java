package org.cmb.application.domain.entity;
import org.cmb.common.enums.ProjectRole;

public class ProjectMemberDO {

    private String userId;
    private ProjectRole role;

    public ProjectMemberDO() {}

    public ProjectMemberDO(String userId, ProjectRole role) {
        this.userId = userId;
        this.role = role;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public ProjectRole getRole() { return role; }
    public void setRole(ProjectRole role) { this.role = role; }
}
