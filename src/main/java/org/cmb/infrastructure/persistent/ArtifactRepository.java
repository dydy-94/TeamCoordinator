package org.cmb.infrastructure.persistent;

import java.util.Collections;
import java.util.List;
import org.cmb.infrastructure.persistent.mapper.CoordinatorTaskMapper;
import org.cmb.infrastructure.persistent.mapper.ProjectArtifactLineageMapper;
import org.cmb.infrastructure.persistent.mapper.ProjectArtifactMapper;
import org.cmb.application.domain.entity.AgentArtifactUploadContextDO;
import org.cmb.application.domain.entity.ArtifactDO;
import org.springframework.stereotype.Repository;

/**
 * Artifact persistence facade. All SQL lives in
 * {@link ProjectArtifactMapper}, {@link CoordinatorTaskMapper} and
 * {@link ProjectArtifactLineageMapper}.
 */
@Repository
public class ArtifactRepository {

    private final ProjectArtifactMapper mapper;
    private final CoordinatorTaskMapper taskMapper;
    private final ProjectArtifactLineageMapper lineageMapper;

    public ArtifactRepository(
            ProjectArtifactMapper mapper,
            CoordinatorTaskMapper taskMapper,
            ProjectArtifactLineageMapper lineageMapper) {
        this.mapper = mapper;
        this.taskMapper = taskMapper;
        this.lineageMapper = lineageMapper;
    }

    public int nextVersion(String projectId, String fileName) {
        Integer version = mapper.selectNextVersion(projectId, fileName);
        return version == null ? 1 : version;
    }

    public void insert(
            ArtifactDO artifact, String tenantId, String createdBy) {
        mapper.insert(artifact, tenantId, createdBy);
    }

    public ArtifactDO find(String tenantId, String projectId, String id) {
        List<ArtifactDO> rows = mapper.find(tenantId, projectId, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public ArtifactDO findByStorageKey(
            String tenantId, String projectId, String storageKey) {
        List<ArtifactDO> rows =
                mapper.findByStorageKey(tenantId, projectId, storageKey);
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

    public AgentArtifactUploadContextDO findUploadContextByTaskId(String taskId) {
        java.util.List<AgentArtifactUploadContextDO> rows =
                taskMapper.findUploadContextByTaskId(taskId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public String findProjectIdByTaskId(String taskId) {
        return taskMapper.findProjectIdByTaskId(taskId);
    }

    public AgentArtifactUploadContextDO findAgentUploadContext(
            String projectId, String conversationId, String businessSessionId,
            String agentRunId, String agentId) {
        List<AgentArtifactUploadContextDO> rows = taskMapper.findAgentUploadContext(
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
        lineageMapper.recordDependencyLineage(outputArtifactId, planId, dependencyKeys);
    }

}
