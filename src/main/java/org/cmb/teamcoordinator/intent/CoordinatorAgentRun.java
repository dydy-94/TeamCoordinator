package org.cmb.teamcoordinator.intent;

public class CoordinatorAgentRun {

    private String id;
    private String sessionId;
    private String stage;
    private String status;
    private long lastSequence;
    private String contextJson;
    private String invalidOutput;
    private String outputJson;

    public String getId() { return id; }
    public void setId(String value) { this.id = value; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String value) { this.sessionId = value; }
    public String getStage() { return stage; }
    public void setStage(String value) { this.stage = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public long getLastSequence() { return lastSequence; }
    public void setLastSequence(long value) { this.lastSequence = value; }
    public String getContextJson() { return contextJson; }
    public void setContextJson(String value) { this.contextJson = value; }
    public String getInvalidOutput() { return invalidOutput; }
    public void setInvalidOutput(String value) { this.invalidOutput = value; }
    public String getOutputJson() { return outputJson; }
    public void setOutputJson(String value) { this.outputJson = value; }
}
