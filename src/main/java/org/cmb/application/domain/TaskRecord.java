package org.cmb.application.domain;

import java.util.ArrayList;
import java.util.List;

public class TaskRecord {

    private Long databaseId;
    private String businessId;
    private String planId;
    private String taskKey;
    private String requestId;
    private String expertId;
    private String sessionId;
    private String status;
    private String objective;
    private String expectedOutput;
    private String acceptanceCriteria;
    private String resultJson;
    private String correctionOf;
    private int correctionCount;
    private List<String> dependencies = new ArrayList<>();
    private List<String> requiredCapabilities = new ArrayList<>();
    private long lastSequence;

    public Long getDatabaseId() { return databaseId; }
    public void setDatabaseId(Long value) { this.databaseId = value; }
    public String getBusinessId() { return businessId; }
    public void setBusinessId(String value) { this.businessId = value; }
    public String getId() { return businessId; }
    public void setId(String value) { this.businessId = value; }
    public String getPlanId() { return planId; }
    public void setPlanId(String value) { this.planId = value; }
    public String getTaskKey() { return taskKey; }
    public void setTaskKey(String value) { this.taskKey = value; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { this.requestId = value; }
    public String getExpertId() { return expertId; }
    public void setExpertId(String value) { this.expertId = value; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String value) { this.sessionId = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getObjective() { return objective; }
    public void setObjective(String value) { this.objective = value; }
    public String getExpectedOutput() { return expectedOutput; }
    public void setExpectedOutput(String value) { this.expectedOutput = value; }
    public String getAcceptanceCriteria() { return acceptanceCriteria; }
    public void setAcceptanceCriteria(String value) { this.acceptanceCriteria = value; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String value) { this.resultJson = value; }
    public String getCorrectionOf() { return correctionOf; }
    public void setCorrectionOf(String value) { this.correctionOf = value; }
    public int getCorrectionCount() { return correctionCount; }
    public void setCorrectionCount(int value) { this.correctionCount = value; }
    public List<String> getDependencies() { return dependencies; }
    public void setDependencies(List<String> value) { this.dependencies = value; }
    public List<String> getRequiredCapabilities() { return requiredCapabilities; }
    public void setRequiredCapabilities(List<String> value) { this.requiredCapabilities = value; }
    public long getLastSequence() { return lastSequence; }
    public void setLastSequence(long value) { this.lastSequence = value; }
}
