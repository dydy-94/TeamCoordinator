package org.cmb.teamcoordinator.expertprotocol;

import javax.validation.constraints.NotBlank;

public class ExpertHumanInputRequired extends ExpertMessage {

    @NotBlank private String question;

    public ExpertHumanInputRequired() { super("EXPERT_HUMAN_INPUT_REQUIRED"); }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
}
