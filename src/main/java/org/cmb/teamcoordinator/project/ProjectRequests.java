package org.cmb.teamcoordinator.project;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public final class ProjectRequests {

    private ProjectRequests() {}

    public static class CreateProject {
        @NotBlank
        @Size(max = 128)
        private String name;

        @Size(max = 1024)
        private String description;

        @Size(max = 128)
        private String coordinatorAgentId;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCoordinatorAgentId() { return coordinatorAgentId; }
        public void setCoordinatorAgentId(String v) { this.coordinatorAgentId = v; }
    }

    public static class UpdateProject {
        @Size(max = 128)
        private String name;

        @Size(max = 1024)
        private String description;

        @Size(max = 128)
        private String coordinatorAgentId;

        private ProjectStatus status;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCoordinatorAgentId() { return coordinatorAgentId; }
        public void setCoordinatorAgentId(String v) { this.coordinatorAgentId = v; }
        public ProjectStatus getStatus() { return status; }
        public void setStatus(ProjectStatus status) { this.status = status; }
    }

    public static class UpsertMember {
        @NotBlank
        private String userId;

        @NotNull
        private ProjectRole role;

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public ProjectRole getRole() { return role; }
        public void setRole(ProjectRole role) { this.role = role; }
    }

    public static class UpsertExpert {
        @NotBlank
        private String expertId;

        private boolean enabled = true;

        public String getExpertId() { return expertId; }
        public void setExpertId(String expertId) { this.expertId = expertId; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class UpsertSkill {
        @NotBlank
        private String skillId;

        private boolean enabled = true;

        public String getSkillId() { return skillId; }
        public void setSkillId(String skillId) { this.skillId = skillId; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
