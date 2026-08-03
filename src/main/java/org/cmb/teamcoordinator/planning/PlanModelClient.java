package org.cmb.teamcoordinator.planning;

import org.cmb.teamcoordinator.intent.TaskIntent;

public interface PlanModelClient {

    String modelName();

    String createPlan(String prompt, TaskIntent intent, int planVersion);

    String repairPlan(String prompt, TaskIntent intent, String invalidOutput, int attempt);
}
