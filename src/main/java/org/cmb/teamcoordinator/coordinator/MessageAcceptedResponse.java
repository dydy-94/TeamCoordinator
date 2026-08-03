package org.cmb.teamcoordinator.coordinator;

public class MessageAcceptedResponse {

    private String messageId;
    private String conversationId;
    private String status;

    public MessageAcceptedResponse() {}

    public MessageAcceptedResponse(String messageId, String conversationId, String status) {
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.status = status;
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
