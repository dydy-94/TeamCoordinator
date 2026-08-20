package org.cmb.application.domain;

public class AgentRunAttachment {

    private String fileName;
    private String fileDownloadUrl;

    public AgentRunAttachment() {
    }

    public AgentRunAttachment(String fileName, String fileDownloadUrl) {
        this.fileName = fileName;
        this.fileDownloadUrl = fileDownloadUrl;
    }

    public String getFileName() { return fileName; }
    public void setFileName(String value) { this.fileName = value; }
    public String getFileDownloadUrl() { return fileDownloadUrl; }
    public void setFileDownloadUrl(String value) { this.fileDownloadUrl = value; }
}
