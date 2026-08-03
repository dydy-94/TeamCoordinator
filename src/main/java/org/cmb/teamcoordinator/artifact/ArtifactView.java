package org.cmb.teamcoordinator.artifact;

public class ArtifactView {

    private String artifactId;
    private int version;
    private String fileName;
    private String mediaType;
    private Long size;
    private String sha256;
    private String status;
    private String uploadUrl;
    private String downloadUrl;

    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String value) { this.artifactId = value; }
    public int getVersion() { return version; }
    public void setVersion(int value) { this.version = value; }
    public String getFileName() { return fileName; }
    public void setFileName(String value) { this.fileName = value; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String value) { this.mediaType = value; }
    public Long getSize() { return size; }
    public void setSize(Long value) { this.size = value; }
    public String getSha256() { return sha256; }
    public void setSha256(String value) { this.sha256 = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getUploadUrl() { return uploadUrl; }
    public void setUploadUrl(String value) { this.uploadUrl = value; }
    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String value) { this.downloadUrl = value; }
}
