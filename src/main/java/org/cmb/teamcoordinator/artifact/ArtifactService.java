package org.cmb.teamcoordinator.artifact;

import java.security.MessageDigest;
import java.util.UUID;
import org.cmb.teamcoordinator.artifact.ArtifactRepository.ArtifactRecord;
import org.cmb.teamcoordinator.common.ApiException;
import org.cmb.teamcoordinator.execution.DispatchWork;
import org.cmb.teamcoordinator.execution.TaskRecord;
import org.cmb.teamcoordinator.project.ProjectService;
import org.cmb.teamcoordinator.project.RequestIdentity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArtifactService {

    private static final long MAX_SIZE = 10 * 1024 * 1024;
    private final ArtifactRepository repository;
    private final FileStore fileStore;
    private final ProjectService projectService;

    public ArtifactService(
            ArtifactRepository repository,
            FileStore fileStore,
            ProjectService projectService) {
        this.repository = repository;
        this.fileStore = fileStore;
        this.projectService = projectService;
    }

    @Transactional
    public ArtifactView reserve(
            RequestIdentity identity, String projectId, ArtifactUploadRequest request) {
        projectService.requireTaskInitiator(identity, projectId);
        MockFileDescriptor file =
                fileStore.reserve(request.getFileName(), request.getMediaType());
        ArtifactRecord artifact = new ArtifactRecord();
        artifact.id = "artifact-" + UUID.randomUUID();
        artifact.projectId = projectId;
        artifact.taskId = request.getTaskId();
        artifact.version = repository.nextVersion(projectId, request.getFileName());
        artifact.storageKey = file.getFileId();
        artifact.fileName = request.getFileName();
        artifact.mediaType = request.getMediaType();
        artifact.status = "UPLOADING";
        artifact.uploadUrl = file.getUploadUrl();
        repository.insert(artifact, identity.getTenantId(), identity.getUserId());
        return toView(artifact, false);
    }

    @Transactional
    public ArtifactView complete(
            RequestIdentity identity, String projectId, String artifactId) {
        projectService.get(identity, projectId);
        ArtifactRecord artifact = require(identity, projectId, artifactId);
        byte[] content = fileStore.getContent(artifact.storageKey);
        if (content == null || content.length == 0) {
            throw ApiException.conflict(
                    "ARTIFACT_UPLOAD_INCOMPLETE", "Artifact object has not been uploaded.");
        }
        if (content.length > MAX_SIZE) {
            throw ApiException.conflict(
                    "ARTIFACT_TOO_LARGE", "Artifact exceeds the maximum size.");
        }
        repository.complete(artifact.id, content.length, sha256(content));
        return toView(require(identity, projectId, artifactId), true);
    }

    public ArtifactView get(
            RequestIdentity identity, String projectId, String artifactId) {
        projectService.get(identity, projectId);
        ArtifactRecord artifact = require(identity, projectId, artifactId);
        return toView(artifact, "AVAILABLE".equals(artifact.status));
    }

    @Transactional
    public String registerExpertArtifact(
            DispatchWork work, TaskRecord task, String storageKey) {
        MockFileDescriptor descriptor = fileStore.getDescriptor(storageKey);
        byte[] content = fileStore.getContent(storageKey);
        if (descriptor == null || content == null || content.length == 0) {
            throw new IllegalStateException(
                    "Expert artifact upload was not completed: " + storageKey);
        }
        ArtifactRecord artifact = new ArtifactRecord();
        artifact.id = "artifact-" + UUID.randomUUID();
        artifact.projectId = work.getProjectId();
        artifact.taskId = task.getId();
        artifact.expertRunId = task.getSessionId();
        artifact.fileName = descriptor.getFileName();
        artifact.mediaType = descriptor.getContentType();
        artifact.version = repository.nextVersion(
                work.getProjectId(), artifact.fileName);
        artifact.storageKey = storageKey;
        artifact.status = "AVAILABLE";
        repository.insert(artifact, work.getTenantId(), "expert:" + task.getExpertId());
        repository.complete(artifact.id, content.length, sha256(content));
        repository.recordDependencyLineage(
                artifact.id, task.getPlanId(), task.getDependencies());
        return artifact.id;
    }

    private ArtifactRecord require(
            RequestIdentity identity, String projectId, String artifactId) {
        ArtifactRecord artifact =
                repository.find(identity.getTenantId(), projectId, artifactId);
        if (artifact == null) {
            throw ApiException.notFound("ARTIFACT_NOT_FOUND", "Artifact was not found.");
        }
        return artifact;
    }

    private ArtifactView toView(ArtifactRecord artifact, boolean includeDownload) {
        ArtifactView view = new ArtifactView();
        view.setArtifactId(artifact.id);
        view.setVersion(artifact.version);
        view.setFileName(artifact.fileName);
        view.setMediaType(artifact.mediaType);
        view.setSize(artifact.size);
        view.setSha256(artifact.sha256);
        view.setStatus(artifact.status);
        view.setUploadUrl(artifact.uploadUrl);
        if (includeDownload) {
            view.setDownloadUrl(fileStore.downloadUrl(artifact.storageKey));
        }
        return view;
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder value = new StringBuilder();
            for (byte item : digest) {
                value.append(String.format("%02x", item));
            }
            return value.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash artifact.", ex);
        }
    }
}
