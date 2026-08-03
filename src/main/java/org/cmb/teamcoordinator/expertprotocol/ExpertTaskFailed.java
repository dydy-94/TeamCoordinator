package org.cmb.teamcoordinator.expertprotocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotBlank;

public class ExpertTaskFailed extends ExpertMessage {

    @NotBlank @JsonProperty("error_code")
    private String errorCode;
    @NotBlank private String message;
    private boolean retryable;

    public ExpertTaskFailed() { super("EXPERT_TASK_FAILED"); }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public boolean isRetryable() { return retryable; }
    public void setRetryable(boolean retryable) { this.retryable = retryable; }
}
