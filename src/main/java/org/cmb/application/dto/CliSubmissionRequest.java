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
    @JsonProperty("session_id")
    private String sessionId;

    @NotBlank
    private String payload;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String value) { this.sessionId = value; }
    public String getPayload() { return payload; }
    public void setPayload(String value) { this.payload = value; }
}
