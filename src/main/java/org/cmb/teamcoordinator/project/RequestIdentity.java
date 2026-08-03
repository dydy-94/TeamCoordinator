package org.cmb.teamcoordinator.project;

public class RequestIdentity {

    private final String tenantId;
    private final String userId;

    public RequestIdentity(String tenantId, String userId) {
        this.tenantId = tenantId;
        this.userId = userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getUserId() {
        return userId;
    }
}
