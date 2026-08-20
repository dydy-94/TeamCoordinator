package org.cmb.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotBlank;

/**
 * Request body for the companion CLI submission endpoints. {@code payload}
 * is the raw structured JSON (decision / plan / verdict) submitted by the
 * agent's CLI call.
 */
public class CliSubmissionRequest {

    @NotBlank
    @JsonProperty("task_id")
    private String taskId;

    @NotBlank
    private String payload;

    public String getTaskId() { return taskId; }
    public void setTaskId(String value) { this.taskId = value; }
    public String getPayload() { return payload; }
    public void setPayload(String value) { this.payload = value; }
}
