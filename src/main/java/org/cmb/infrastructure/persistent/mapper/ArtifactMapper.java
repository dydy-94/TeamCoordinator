package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.cmb.infrastructure.persistent.ArtifactRepository.ArtifactRecord;
import org.cmb.application.domain.AgentArtifactUploadContext;

/**
 * SQL access for artifacts (project_artifact, project_artifact_lineage).
 * Queries that may match multiple rows return {@code List} so the
 * repository facade keeps its "first row or null" semantics.
 */
@Mapper
public interface ArtifactMapper {

    Integer selectNextVersion(
            @Param("projectId") String projectId,
            @Param("fileName") String fileName);

    int insert(
            @Param("artifact") ArtifactRecord artifact,
            @Param("tenantId") String tenantId,
            @Param("createdBy") String createdBy);

    List<ArtifactRecord> find(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("id") String id);

    List<String> resolveStorageKey(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("reference") String reference);

    int complete(
            @Param("id") String id,
            @Param("size") long size,
            @Param("sha256") String sha256);

    List<AgentArtifactUploadContext> findAgentUploadContext(
            @Param("projectId") String projectId,
            @Param("conversationId") String conversationId,
            @Param("businessSessionId") String businessSessionId,
            @Param("agentRunId") String agentRunId,
            @Param("agentId") String agentId);

    Integer countAvailableAgentArtifact(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("coordinatorTaskId") String coordinatorTaskId,
            @Param("agentRunId") String agentRunId,
            @Param("artifactId") String artifactId);

    List<String> findAvailableStorageKeys(
            @Param("planId") String planId,
            @Param("dependencyKeys") List<String> dependencyKeys);

    int recordDependencyLineage(
            @Param("outputArtifactId") String outputArtifactId,
            @Param("planId") String planId,
            @Param("dependencyKeys") List<String> dependencyKeys);
}
