package org.cmb.application.dto;

/**
 * Lightweight expert descriptor returned by the /experts query API.
 */
public class ExpertInfo {

    private String id;
    private String name;
    private String description;
    private String prompt;

    public ExpertInfo() {}

    public ExpertInfo(String id, String name, String description, String prompt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.prompt = prompt;
    }

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String v) { this.prompt = v; }
}
