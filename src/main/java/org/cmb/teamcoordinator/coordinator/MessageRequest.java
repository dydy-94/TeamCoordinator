package org.cmb.teamcoordinator.coordinator;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class MessageRequest {

    @NotBlank
    @Size(max = 128)
    @JsonProperty("client_message_id")
    private String clientMessageId;

    @NotBlank
    @Size(max = 10000)
    private String text;

    @JsonProperty("attachment_refs")
    @Size(max = 10)
    private List<String> attachmentRefs = new ArrayList<>();

    @NotBlank
    @Size(max = 128)
    @JsonProperty("idempotency_key")
    private String idempotencyKey;

    public String getClientMessageId() { return clientMessageId; }
    public void setClientMessageId(String clientMessageId) { this.clientMessageId = clientMessageId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public List<String> getAttachmentRefs() { return attachmentRefs; }
    public void setAttachmentRefs(List<String> attachmentRefs) { this.attachmentRefs = attachmentRefs; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
