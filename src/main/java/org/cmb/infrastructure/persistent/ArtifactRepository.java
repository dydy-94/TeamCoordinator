package org.cmb.infrastructure.persistent;

import java.util.Collections;
import java.util.List;
import org.cmb.infrastructure.persistent.mapper.ArtifactMapper;
import org.cmb.application.domain.AgentArtifactUploadContext;
import org.springframework.stereotype.Repository;

/**
 * Artifact persistence facade. All SQL lives in {@link ArtifactMapper}.
 */
@Repository
public class ArtifactRepository {

    private final ArtifactMapper mapper;

    public ArtifactRepository(ArtifactMapper mapper) {
        this.mapper = mapper;
    }

    public int nextVersion(String projectId, String fileName) {
        Integer version = mapper.selectNextVersion(projectId, fileName);
        return version == null ? 1 : version;
    }

    public void insert(
            ArtifactRecord artifact, String tenantId, String createdBy) {
        mapper.insert(artifact, tenantId, createdBy);
    }

    public ArtifactRecord find(String tenantId, String projectId, String id) {
        List<ArtifactRecord> rows = mapper.find(tenantId, projectId, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public String resolveStorageKey(
            String tenantId, String projectId, String reference) {
        List<String> keys = mapper.resolveStorageKey(tenantId, projectId, reference);
        return keys.isEmpty() ? reference : keys.get(0);
    }

    public void complete(String id, long size, String sha256) {
        mapper.complete(id, size, sha256);
    }

    public AgentArtifactUploadContext findUploadContextByTaskId(String taskId) {
        java.util.List<AgentArtifactUploadContext> rows =
                mapper.findUploadContextByTaskId(taskId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public String findProjectIdByTaskId(String taskId) {
        return mapper.findProjectIdByTaskId(taskId);
    }

    public AgentArtifactUploadContext findAgentUploadContext(
            String projectId, String conversationId, String businessSessionId,
            String agentRunId, String agentId) {
        List<AgentArtifactUploadContext> rows = mapper.findAgentUploadContext(
                projectId, conversationId, businessSessionId, agentRunId, agentId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean isAvailableAgentArtifact(
            String tenantId, String projectId, String coordinatorTaskId,
            String agentRunId, String artifactId) {
        Integer count = mapper.countAvailableAgentArtifact(
                tenantId, projectId, coordinatorTaskId, agentRunId, artifactId);
        return count != null && count == 1;
    }

    public List<String> findAvailableStorageKeys(
            String planId, List<String> dependencyKeys) {
        if (dependencyKeys.isEmpty()) {
            return Collections.emptyList();
        }
        return mapper.findAvailableStorageKeys(planId, dependencyKeys);
    }

    public void recordDependencyLineage(
            String outputArtifactId, String planId, List<String> dependencyKeys) {
        if (dependencyKeys.isEmpty()) {
            return;
        }
        mapper.recordDependencyLineage(outputArtifactId, planId, dependencyKeys);
    }

    public static final class ArtifactRecord {
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
}
