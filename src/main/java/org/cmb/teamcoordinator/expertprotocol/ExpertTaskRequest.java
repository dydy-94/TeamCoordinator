package org.cmb.teamcoordinator.expertprotocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotBlank;

public class ExpertTaskRequest extends ExpertMessage {

    @NotBlank private String objective;
    @NotBlank @JsonProperty("expert_id")
    private String expertId;
    @JsonProperty("attachment_refs")
    private List<String> attachmentRefs = new ArrayList<>();

    public ExpertTaskRequest() { super("EXPERT_TASK_REQUEST"); }
    public String getObjective() { return objective; }
    public void setObjective(String objective) { this.objective = objective; }
    public String getExpertId() { return expertId; }
    public void setExpertId(String expertId) { this.expertId = expertId; }
    public List<String> getAttachmentRefs() { return attachmentRefs; }
    public void setAttachmentRefs(List<String> value) { this.attachmentRefs = value; }
}
