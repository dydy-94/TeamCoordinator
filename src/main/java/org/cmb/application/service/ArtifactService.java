package org.cmb.application.service;

import java.util.List;
import org.cmb.application.domain.AgentRunAttachment;
import org.cmb.application.domain.RequestIdentity;
import org.cmb.application.domain.entity.AgentArtifactUploadContextDO;
import org.cmb.application.domain.entity.DispatchWorkDO;
import org.cmb.application.domain.entity.TaskDO;
import org.cmb.application.dto.ArtifactUploadRequest;
import org.cmb.application.dto.ArtifactView;

/**
 * Artifact lifecycle: reservation, upload completion, agent uploads,
 * attachment conversion and expert artifact registration.
 */
public interface ArtifactService {

    ArtifactView reserve(
            RequestIdentity identity, String projectId, ArtifactUploadRequest request);

    ArtifactView complete(RequestIdentity identity, String projectId, String artifactId);

    ArtifactView get(RequestIdentity identity, String projectId, String artifactId);

    ArtifactView getByStorageKey(
            RequestIdentity identity, String projectId, String storageKey);

    ArtifactView uploadFromAgent(
            String projectId, AgentArtifactUploadContextDO context,
            String fileName, String mediaType, byte[] content);

    List<String> acceptAgentArtifacts(
            DispatchWorkDO work, TaskDO task, List<?> artifactIds);

    List<AgentRunAttachment> toAgentAttachments(List<String> storageKeys);

    String registerExpertArtifact(DispatchWorkDO work, TaskDO task, String storageKey);
}
