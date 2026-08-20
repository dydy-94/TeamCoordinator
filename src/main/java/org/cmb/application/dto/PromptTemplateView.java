package org.cmb.application.dto;

import java.time.Instant;

public class PromptTemplateView {
    private String id;
    private String promptKey;
    private String agentScope;
    private String scene;
    private int version;
    private String status;
    private String templateContent;
    private String variablesSchema;
    private Instant createdAt;
    private Instant publishedAt;

    public String getId() { return id; }
    public void setId(String value) { this.id = value; }
    public String getPromptKey() { return promptKey; }
    public void setPromptKey(String value) { this.promptKey = value; }
    public String getAgentScope() { return agentScope; }
    public void setAgentScope(String value) { this.agentScope = value; }
    public String getScene() { return scene; }
    public void setScene(String value) { this.scene = value; }
    public int getVersion() { return version; }
    public void setVersion(int value) { this.version = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getTemplateContent() { return templateContent; }
    public void setTemplateContent(String value) { this.templateContent = value; }
    public String getVariablesSchema() { return variablesSchema; }
    public void setVariablesSchema(String value) { this.variablesSchema = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { this.createdAt = value; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant value) { this.publishedAt = value; }
}
