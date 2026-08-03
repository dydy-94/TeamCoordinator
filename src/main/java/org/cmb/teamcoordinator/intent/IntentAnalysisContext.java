package org.cmb.teamcoordinator.intent;

import java.util.ArrayList;
import java.util.List;
import org.cmb.teamcoordinator.agentcore.ExpertDescriptor;
import org.cmb.teamcoordinator.artifact.MockFileDescriptor;

public class IntentAnalysisContext {

    private String projectName;
    private String projectDescription;
    private String text;
    private List<String> recentMessages = new ArrayList<>();
    private List<ExpertDescriptor> experts = new ArrayList<>();
    private List<MockFileDescriptor> attachments = new ArrayList<>();
    private List<String> attachmentRefs = new ArrayList<>();

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
}
