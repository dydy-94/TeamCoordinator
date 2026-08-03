package org.cmb.teamcoordinator.expertprotocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;

public class ExpertTaskResult extends ExpertMessage {

    @NotNull private Map<String, Object> result = new LinkedHashMap<>();
    @JsonProperty("artifact_refs")
    private List<String> artifactRefs = new ArrayList<>();

    public ExpertTaskResult() { super("EXPERT_TASK_RESULT"); }
    public Map<String, Object> getResult() { return result; }
    public void setResult(Map<String, Object> result) { this.result = result; }
    public List<String> getArtifactRefs() { return artifactRefs; }
    public void setArtifactRefs(List<String> value) { this.artifactRefs = value; }
}
