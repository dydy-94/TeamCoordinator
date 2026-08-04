package org.cmb.teamcoordinator.agentcore;

public class AgentCoreConversationResponse {

    private String returnCode;
    private String message;
    private Data data;

    public static AgentCoreConversationResponse success(AgentRunResponse run) {
        AgentCoreConversationResponse response = new AgentCoreConversationResponse();
        response.returnCode = "SUC0000";
        response.data = new Data();
        response.data.sessionId = run.getSessionId();
        response.data.conversationId = run.getConversationId();
        response.data.queuePosition = run.getQueuePosition();
        return response;
    }

    public AgentRunResponse toRunResponse() {
        if (!"SUC0000".equals(returnCode) || data == null || data.sessionId == null) {
            throw new IllegalStateException(
                    "AgentCore request failed: " + returnCode + " " + message);
        }
        AgentRunResponse response = new AgentRunResponse(data.sessionId, "ACCEPTED");
        response.setConversationId(data.conversationId);
        response.setQueuePosition(data.queuePosition);
        return response;
    }

    public String getReturnCode() { return returnCode; }
    public void setReturnCode(String value) { this.returnCode = value; }
    public String getMessage() { return message; }
    public void setMessage(String value) { this.message = value; }
    public Data getData() { return data; }
    public void setData(Data value) { this.data = value; }

    public static class Data {
        private String sessionId;
        private String conversationId;
        private int queuePosition;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String value) { this.sessionId = value; }
        public String getConversationId() { return conversationId; }
        public void setConversationId(String value) { this.conversationId = value; }
        public int getQueuePosition() { return queuePosition; }
        public void setQueuePosition(int value) { this.queuePosition = value; }
    }
}
