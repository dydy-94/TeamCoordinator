package org.cmb.teamcoordinator.agentcore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotBlank;

public class AgentRunRequest {

    @NotBlank
    private String expertId;

    @NotBlank
    private String taskText;

    private Map<String, Object> structuredInput;

    private List<String> attachmentRefs = new ArrayList<>();

    private String idempotencyKey;

    public String getExpertId() {
        return expertId;
    }

    public void setExpertId(String expertId) {
        this.expertId = expertId;
    }

    public String getTaskText() {
        return taskText;
    }

    public void setTaskText(String taskText) {
        this.taskText = taskText;
    }

    public Map<String, Object> getStructuredInput() {
        return structuredInput;
    }

    public void setStructuredInput(Map<String, Object> structuredInput) {
        this.structuredInput = structuredInput;
    }

    public List<String> getAttachmentRefs() {
        return attachmentRefs;
    }

    public void setAttachmentRefs(List<String> attachmentRefs) {
        this.attachmentRefs = attachmentRefs;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
