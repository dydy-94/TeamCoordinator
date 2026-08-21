package org.cmb.application.domain.entity;

public class AgentArtifactUploadContextDO {

    private final String tenantId;
    private final String coordinatorTaskId;
    private final String agentId;
    private final String agentRunId;

    public AgentArtifactUploadContextDO(
            String tenantId, String coordinatorTaskId, String agentId, String agentRunId) {
        this.tenantId = tenantId;
        this.coordinatorTaskId = coordinatorTaskId;
        this.agentId = agentId;
        this.agentRunId = agentRunId;
    }

    public String getTenantId() { return tenantId; }
    public String getCoordinatorTaskId() { return coordinatorTaskId; }
    public String getAgentId() { return agentId; }
    public String getAgentRunId() { return agentRunId; }
}
