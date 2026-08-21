package org.cmb.application.domain.entity;

/**
 * Row type for digital_team_project_artifact (extracted from
 * ArtifactRepository's nested record). Public fields match the
 * ProjectArtifactMapper resultMap property names. {@code uploadUrl} is a
 * write-side field set by ArtifactService.reserve, not a table column.
 */
public class ArtifactDO {
    public String id;
    public String projectId;
    public String taskId;
    public String expertRunId;
    public int version;
    public String storageKey;
    public String fileName;
    public String mediaType;
    public Long size;
    public String sha256;
    public String status;
    public String uploadUrl;
}
