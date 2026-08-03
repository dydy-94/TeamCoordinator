package org.cmb.teamcoordinator.expertprotocol;

import javax.validation.constraints.NotBlank;

public class ExpertTaskCancel extends ExpertMessage {

    @NotBlank private String reason;

    public ExpertTaskCancel() { super("EXPERT_TASK_CANCEL"); }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
