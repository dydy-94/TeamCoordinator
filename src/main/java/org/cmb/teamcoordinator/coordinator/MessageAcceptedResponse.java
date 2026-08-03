package org.cmb.teamcoordinator.coordinator;

public class MessageAcceptedResponse {

    private String messageId;
    private String taskId;
    private String sessionId;
    private String status;

    public MessageAcceptedResponse() {}

    public MessageAcceptedResponse(
            String messageId, String taskId, String sessionId, String status) {
        this.messageId = messageId;
        this.taskId = taskId;
        this.sessionId = sessionId;
        this.status = status;
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String value) { this.taskId = value; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String value) { this.sessionId = value; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
