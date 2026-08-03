package org.cmb.teamcoordinator.expertprotocol;

import javax.validation.constraints.NotBlank;

public class ExpertTaskProgress extends ExpertMessage {

    @NotBlank private String message;
    private Integer percent;

    public ExpertTaskProgress() { super("EXPERT_TASK_PROGRESS"); }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Integer getPercent() { return percent; }
    public void setPercent(Integer percent) { this.percent = percent; }
}
