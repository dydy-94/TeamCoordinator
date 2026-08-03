package org.cmb.teamcoordinator.planning;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;

public class CoordinatorPlanSpec {

    @Min(1)
    @JsonProperty("plan_version")
    private int planVersion = 1;
    @Valid
    @NotEmpty
    @Size(max = 8)
    private List<PlannedTask> tasks = new ArrayList<>();

    public int getPlanVersion() { return planVersion; }
    public void setPlanVersion(int value) { this.planVersion = value; }
    public List<PlannedTask> getTasks() { return tasks; }
    public void setTasks(List<PlannedTask> value) { this.tasks = value; }
}
