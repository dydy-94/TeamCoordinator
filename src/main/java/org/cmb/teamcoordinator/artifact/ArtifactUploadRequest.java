package org.cmb.teamcoordinator.artifact;

import javax.validation.constraints.NotBlank;

public class ArtifactUploadRequest {

    @NotBlank private String fileName;
    @NotBlank private String mediaType;
    private String taskId;

    public String getFileName() { return fileName; }
    public void setFileName(String value) { this.fileName = value; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String value) { this.mediaType = value; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String value) { this.taskId = value; }
}
