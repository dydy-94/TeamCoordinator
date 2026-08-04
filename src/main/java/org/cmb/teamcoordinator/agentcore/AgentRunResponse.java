package org.cmb.teamcoordinator.agentcore;

public class AgentRunResponse {

    private String sessionId;
    private String status;
    private String conversationId;
    private int queuePosition;

    public AgentRunResponse() {
    }

    public AgentRunResponse(String sessionId, String status) {
        this.sessionId = sessionId;
        this.status = status;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String value) { this.conversationId = value; }
    public int getQueuePosition() { return queuePosition; }
    public void setQueuePosition(int value) { this.queuePosition = value; }
}
