package org.cmb.application.domain.entity;

import java.time.Instant;

public class ConversationDO {

    private String taskId;
    private String projectId;
    private String sessionId;
    private String coordinatorSessionId;
    private String coordinatorAgentId;
    private String title;
    private String status;
    private Instant createdAt;

    public String getTaskId() { return taskId; }
    public void setTaskId(String value) { this.taskId = value; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String value) { this.projectId = value; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String value) { this.sessionId = value; }
    public String getCoordinatorSessionId() { return coordinatorSessionId; }
    public void setCoordinatorSessionId(String value) { this.coordinatorSessionId = value; }
    public String getCoordinatorAgentId() { return coordinatorAgentId; }
    public void setCoordinatorAgentId(String value) { this.coordinatorAgentId = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { this.createdAt = value; }
}
