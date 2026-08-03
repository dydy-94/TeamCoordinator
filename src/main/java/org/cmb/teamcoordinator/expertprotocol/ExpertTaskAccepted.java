package org.cmb.teamcoordinator.expertprotocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotBlank;

public class ExpertTaskAccepted extends ExpertMessage {

    @NotBlank @JsonProperty("session_id")
    private String sessionId;

    public ExpertTaskAccepted() { super("EXPERT_TASK_ACCEPTED"); }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
