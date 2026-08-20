package org.cmb.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class IntentAnalysisRequest {

    @NotBlank
    @Size(max = 10000)
    private String text;
    @JsonProperty("attachment_refs")
    @Size(max = 10)
    private List<String> attachmentRefs = new ArrayList<>();

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public List<String> getAttachmentRefs() { return attachmentRefs; }
    public void setAttachmentRefs(List<String> attachmentRefs) { this.attachmentRefs = attachmentRefs; }
}
