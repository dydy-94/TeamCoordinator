package org.cmb.teamcoordinator.coordinator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class ProjectEvent {

    private String id;
    private String projectId;
    @JsonProperty("taskId")
    private String conversationId;
    private String messageId;
    private long sequence;
    private ProjectEventType type;
    private JsonNode payload;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public long getSequence() { return sequence; }
    public void setSequence(long sequence) { this.sequence = sequence; }
    public ProjectEventType getType() { return type; }
    public void setType(ProjectEventType type) { this.type = type; }
    public JsonNode getPayload() { return payload; }
    public void setPayload(JsonNode payload) { this.payload = payload; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
