package org.cmb.application.domain;

import java.util.ArrayList;
import java.util.List;
import org.cmb.application.domain.ExpertDescriptor;
import org.cmb.application.domain.MockFileDescriptor;

public class IntentAnalysisContext {

    private String projectName;
    private String projectDescription;
    private String text;
    private List<String> recentMessages = new ArrayList<>();
    private List<ExpertDescriptor> experts = new ArrayList<>();
    private List<MockFileDescriptor> attachments = new ArrayList<>();
    private List<String> attachmentRefs = new ArrayList<>();
    /** Non-null when this task has a pending human request (expert confirm or coordinator ask). */
    private String pendingStatus;
    /** The question being asked by the waiting expert or coordinator. */
    private String pendingQuestion;
    /** Expert ID when pendingStatus is EXPERT_WAITING. */
    private String pendingExpertName;
    /** Project-level override for the Coordinator agent ID. */
    private String coordinatorAgentId;

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getProjectDescription() { return projectDescription; }
    public void setProjectDescription(String value) { this.projectDescription = value; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public List<String> getRecentMessages() { return recentMessages; }
    public void setRecentMessages(List<String> value) { this.recentMessages = value; }
    public List<ExpertDescriptor> getExperts() { return experts; }
    public void setExperts(List<ExpertDescriptor> experts) { this.experts = experts; }
    public List<MockFileDescriptor> getAttachments() { return attachments; }
    public void setAttachments(List<MockFileDescriptor> value) { this.attachments = value; }
    public List<String> getAttachmentRefs() { return attachmentRefs; }
    public void setAttachmentRefs(List<String> value) { this.attachmentRefs = value; }
    public String getPendingStatus() { return pendingStatus; }
    public void setPendingStatus(String value) { this.pendingStatus = value; }
    public String getPendingQuestion() { return pendingQuestion; }
    public void setPendingQuestion(String value) { this.pendingQuestion = value; }
    public String getPendingExpertName() { return pendingExpertName; }
    public void setPendingExpertName(String value) { this.pendingExpertName = value; }
    public String getCoordinatorAgentId() { return coordinatorAgentId; }
    public void setCoordinatorAgentId(String v) { this.coordinatorAgentId = v; }
}
