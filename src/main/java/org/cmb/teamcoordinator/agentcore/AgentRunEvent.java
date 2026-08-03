package org.cmb.teamcoordinator.agentcore;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class AgentRunEvent {

    private String sessionId;
    private String eventId;
    private long sequence;
    private String type;
    private String status;
    private String message;
    private Instant createdAt;
    private Map<String, Object> payload = new LinkedHashMap<>();

    public AgentRunEvent() {
    }

    public AgentRunEvent(String sessionId, long sequence, String type, String status, String message) {
        this.sessionId = sessionId;
        this.eventId = sessionId + ":" + sequence;
        this.sequence = sequence;
        this.type = type;
        this.status = status;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}
