package org.cmb.application.dto;

public class RenderedPrompt {
    private final String content;
    private final int version;
    private final String templateId;

    public RenderedPrompt(String content, int version, String templateId) {
        this.content = content;
        this.version = version;
        this.templateId = templateId;
    }

    public String getContent() { return content; }
    public int getVersion() { return version; }
    public String getTemplateId() { return templateId; }
}
