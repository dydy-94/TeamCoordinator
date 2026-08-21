package org.cmb.application.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 租户管理请求体。
 */
public final class TenantRequests {

    private TenantRequests() {
    }

    public static class CreateTenant {
        @NotBlank
        @Size(max = 128)
        private String name;

        @Size(max = 512)
        private String description;

        @NotBlank
        @Size(max = 64)
        private String ownerUserId;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }
    }

    public static class UpdateTenant {
        @Size(max = 128)
        private String name;

        @Size(max = 512)
        private String description;

        @Size(max = 64)
        private String ownerUserId;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }
    }

    public static class AssignMember {
        @NotBlank
        @Size(max = 64)
        private String userId;

        @NotBlank
        private String role;

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }
}
