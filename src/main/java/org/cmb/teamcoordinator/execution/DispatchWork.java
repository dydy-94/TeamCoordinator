package org.cmb.teamcoordinator.execution;

import java.util.ArrayList;
import java.util.List;

public class DispatchWork {

    private String dispatchId;
    private String tenantId;
    private String projectId;
    private String conversationId;
    private String messageId;
    private String userId;
    private String text;
    private List<String> attachmentRefs = new ArrayList<>();

    public String getDispatchId() { return dispatchId; }
    public void setDispatchId(String value) { this.dispatchId = value; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String value) { this.tenantId = value; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String value) { this.projectId = value; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String value) { this.conversationId = value; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String value) { this.messageId = value; }
    public String getUserId() { return userId; }
    public void setUserId(String value) { this.userId = value; }
    public String getText() { return text; }
    public void setText(String value) { this.text = value; }
    public List<String> getAttachmentRefs() { return attachmentRefs; }
    public void setAttachmentRefs(List<String> value) { this.attachmentRefs = value; }
}
