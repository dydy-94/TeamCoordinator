package org.cmb.application.dto;
import org.cmb.common.enums.HumanDecision;

import com.fasterxml.jackson.databind.JsonNode;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class HumanResponseRequest {

    @NotNull private HumanDecision decision;
    @NotNull private JsonNode response;
    @NotBlank private String idempotencyKey;

    public HumanDecision getDecision() { return decision; }
    public void setDecision(HumanDecision value) { this.decision = value; }
    public JsonNode getResponse() { return response; }
    public void setResponse(JsonNode value) { this.response = value; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { this.idempotencyKey = value; }
}
