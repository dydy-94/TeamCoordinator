package org.cmb.teamcoordinator.intent;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class TaskIntent {

    @NotBlank private String intent;
    @NotBlank private String objective;
    @NotNull @JsonProperty("expected_outputs")
    private List<String> expectedOutputs = new ArrayList<>();
    @NotNull private List<String> constraints = new ArrayList<>();
    @NotNull @JsonProperty("required_capabilities")
    private List<String> requiredCapabilities = new ArrayList<>();
    @NotNull @JsonProperty("input_refs")
    private List<String> inputRefs = new ArrayList<>();
    @NotNull @JsonProperty("missing_information")
    private List<String> missingInformation = new ArrayList<>();
    @NotNull @JsonProperty("risk_level")
    private RiskLevel riskLevel;
    @NotNull @JsonProperty("execution_mode")
    private ExecutionMode executionMode;

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public String getObjective() { return objective; }
    public void setObjective(String objective) { this.objective = objective; }
    public List<String> getExpectedOutputs() { return expectedOutputs; }
    public void setExpectedOutputs(List<String> value) { this.expectedOutputs = value; }
    public List<String> getConstraints() { return constraints; }
    public void setConstraints(List<String> constraints) { this.constraints = constraints; }
    public List<String> getRequiredCapabilities() { return requiredCapabilities; }
    public void setRequiredCapabilities(List<String> value) { this.requiredCapabilities = value; }
    public List<String> getInputRefs() { return inputRefs; }
    public void setInputRefs(List<String> inputRefs) { this.inputRefs = inputRefs; }
    public List<String> getMissingInformation() { return missingInformation; }
    public void setMissingInformation(List<String> value) { this.missingInformation = value; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }
    public ExecutionMode getExecutionMode() { return executionMode; }
    public void setExecutionMode(ExecutionMode executionMode) { this.executionMode = executionMode; }
}
