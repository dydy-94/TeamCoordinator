package org.cmb.teamcoordinator.expertprotocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotBlank;

public class ExpertTaskResume extends ExpertMessage {

    @NotBlank @JsonProperty("human_response")
    private String humanResponse;

    public ExpertTaskResume() { super("EXPERT_TASK_RESUME"); }
    public String getHumanResponse() { return humanResponse; }
    public void setHumanResponse(String humanResponse) { this.humanResponse = humanResponse; }
}
