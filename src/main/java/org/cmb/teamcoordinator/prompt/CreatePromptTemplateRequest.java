package org.cmb.teamcoordinator.prompt;

import javax.validation.constraints.NotBlank;

public class CreatePromptTemplateRequest {
    @NotBlank private String promptKey;
    @NotBlank private String agentScope;
    @NotBlank private String scene;
    @NotBlank private String templateContent;
    private String variablesSchema = "{\"required\":[\"context_json\"]}";

    public String getPromptKey() { return promptKey; }
    public void setPromptKey(String value) { this.promptKey = value; }
    public String getAgentScope() { return agentScope; }
    public void setAgentScope(String value) { this.agentScope = value; }
    public String getScene() { return scene; }
    public void setScene(String value) { this.scene = value; }
    public String getTemplateContent() { return templateContent; }
    public void setTemplateContent(String value) { this.templateContent = value; }
    public String getVariablesSchema() { return variablesSchema; }
    public void setVariablesSchema(String value) { this.variablesSchema = value; }
}
