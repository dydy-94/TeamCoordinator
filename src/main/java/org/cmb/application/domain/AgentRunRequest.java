package org.cmb.application.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AgentRunRequest {

    private String taskText;

    private String systemPrompt;

    private List<String> skillNames = new ArrayList<>();

    private List<AgentRunAttachment> attachments = new ArrayList<>();

    private Map<String, Object> structuredInput;

    /**
     * When non-null, submit within this existing AgentCore conversation
     * instead of creating a new one. Must be the sessionId from a previous
     * {@code submitRun()} call to the same agent within the same task.
     */
    private String conversationSessionId;

    public String getConversationSessionId() { return conversationSessionId; }
    public void setConversationSessionId(String v) { this.conversationSessionId = v; }

    public String getTaskText() {
        return taskText;
    }

    public void setTaskText(String taskText) {
        this.taskText = taskText;
    }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String value) { this.systemPrompt = value; }
    public List<String> getSkillNames() { return skillNames; }
    public void setSkillNames(List<String> value) {
        this.skillNames = value == null ? new ArrayList<>() : value;
    }
    public List<AgentRunAttachment> getAttachments() { return attachments; }
    public void setAttachments(List<AgentRunAttachment> value) {
        this.attachments = value == null ? new ArrayList<>() : value;
    }

    @JsonIgnore
    public String getContextText() {
        try {
            return new ObjectMapper().writeValueAsString(structuredInput);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize AgentCore context.", ex);
        }
    }

    public Map<String, Object> getStructuredInput() {
        return structuredInput;
    }

    public void setStructuredInput(Map<String, Object> structuredInput) {
        this.structuredInput = structuredInput;
    }

}
