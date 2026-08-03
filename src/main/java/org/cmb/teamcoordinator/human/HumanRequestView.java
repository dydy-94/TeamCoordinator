package org.cmb.teamcoordinator.human;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public class HumanRequestView {

    private String id;
    private String projectId;
    private String taskId;
    private HumanRequestType requestType;
    private String question;
    private String status;
    private HumanDecision decision;
    private JsonNode response;
    private Instant expiresAt;

    public String getId() { return id; }
    public void setId(String value) { this.id = value; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String value) { this.projectId = value; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String value) { this.taskId = value; }
    public HumanRequestType getRequestType() { return requestType; }
    public void setRequestType(HumanRequestType value) { this.requestType = value; }
    public String getQuestion() { return question; }
    public void setQuestion(String value) { this.question = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public HumanDecision getDecision() { return decision; }
    public void setDecision(HumanDecision value) { this.decision = value; }
    public JsonNode getResponse() { return response; }
    public void setResponse(JsonNode value) { this.response = value; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant value) { this.expiresAt = value; }
}
