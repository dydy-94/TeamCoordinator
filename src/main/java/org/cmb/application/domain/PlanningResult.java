package org.cmb.application.domain;

public class PlanningResult {

    private final CoordinatorPlanSpec plan;
    private final String rawJson;
    private final int repairCount;
    private final String sessionId;

    public PlanningResult(CoordinatorPlanSpec plan, String rawJson,
            int repairCount, String sessionId) {
        this.plan = plan;
        this.rawJson = rawJson;
        this.repairCount = repairCount;
        this.sessionId = sessionId;
    }

    public CoordinatorPlanSpec getPlan() { return plan; }
    public String getRawJson() { return rawJson; }
    public int getRepairCount() { return repairCount; }
    public String getSessionId() { return sessionId; }
}
