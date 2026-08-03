package org.cmb.teamcoordinator.planning;

public class PlanningResult {

    private final CoordinatorPlanSpec plan;
    private final String rawJson;
    private final int repairCount;

    public PlanningResult(CoordinatorPlanSpec plan, String rawJson, int repairCount) {
        this.plan = plan;
        this.rawJson = rawJson;
        this.repairCount = repairCount;
    }

    public CoordinatorPlanSpec getPlan() { return plan; }
    public String getRawJson() { return rawJson; }
    public int getRepairCount() { return repairCount; }
}
