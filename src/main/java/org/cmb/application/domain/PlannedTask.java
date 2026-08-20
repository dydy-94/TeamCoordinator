package org.cmb.application.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class PlannedTask {

    @NotBlank
    @JsonProperty("task_key")
    private String taskKey;
    @NotBlank private String objective;
    @NotNull private List<String> dependencies = new ArrayList<>();
    @NotBlank
    @JsonProperty("expected_output")
    private String expectedOutput;
    @NotBlank
    @JsonProperty("acceptance_criteria")
    private String acceptanceCriteria;
    @NotEmpty
    @JsonProperty("required_capabilities")
    private List<String> requiredCapabilities = new ArrayList<>();

    public String getTaskKey() { return taskKey; }
    public void setTaskKey(String value) { this.taskKey = value; }
    public String getObjective() { return objective; }
    public void setObjective(String value) { this.objective = value; }
    public List<String> getDependencies() { return dependencies; }
    public void setDependencies(List<String> value) { this.dependencies = value; }
    public String getExpectedOutput() { return expectedOutput; }
    public void setExpectedOutput(String value) { this.expectedOutput = value; }
    public String getAcceptanceCriteria() { return acceptanceCriteria; }
    public void setAcceptanceCriteria(String value) { this.acceptanceCriteria = value; }
    public List<String> getRequiredCapabilities() { return requiredCapabilities; }
    public void setRequiredCapabilities(List<String> value) { this.requiredCapabilities = value; }
}
