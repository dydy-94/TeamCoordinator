package org.cmb.teamcoordinator.artifact;

import java.util.List;
import org.cmb.teamcoordinator.persistence.MyBatisExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class ArtifactRepository {

    private final MyBatisExecutor jdbc;

    public ArtifactRepository(MyBatisExecutor jdbc) {
        this.jdbc = jdbc;
    }

    public int nextVersion(String projectId, String fileName) {
        Integer version = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version), 0) + 1 FROM project_artifact "
                        + "WHERE project_id = ? AND file_name = ?",
                Integer.class, projectId, fileName);
        return version == null ? 1 : version;
    }

    public void insert(
            ArtifactRecord artifact, String tenantId, String createdBy) {
        jdbc.update(
                "INSERT INTO project_artifact "
                        + "(business_id, tenant_id, project_id, task_id, expert_run_id, version, "
                        + "storage_key, file_name, media_type, status, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                artifact.id, tenantId, artifact.projectId, artifact.taskId,
                artifact.expertRunId, artifact.version, artifact.storageKey,
                artifact.fileName, artifact.mediaType, artifact.status, createdBy);
    }

    public ArtifactRecord find(String tenantId, String projectId, String id) {
        List<ArtifactRecord> rows = jdbc.query(
                "SELECT * FROM project_artifact WHERE tenant_id = ? AND project_id = ? AND business_id = ?",
                (rs, rowNum) -> {
                    ArtifactRecord record = new ArtifactRecord();
                    record.id = rs.getString("business_id");
                    record.projectId = rs.getString("project_id");
                    record.taskId = rs.getString("task_id");
                    record.expertRunId = rs.getString("expert_run_id");
                    record.version = rs.getInt("version");
                    record.storageKey = rs.getString("storage_key");
                    record.fileName = rs.getString("file_name");
                    record.mediaType = rs.getString("media_type");
                    long size = rs.getLong("size_bytes");
                    record.size = rs.wasNull() ? null : size;
                    record.sha256 = rs.getString("sha256");
                    record.status = rs.getString("status");
                    return record;
                },
                tenantId, projectId, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public String resolveStorageKey(
            String tenantId, String projectId, String reference) {
        List<String> keys = jdbc.queryForList(
                "SELECT storage_key FROM project_artifact "
                        + "WHERE tenant_id = ? AND project_id = ? AND business_id = ? "
                        + "AND status = 'AVAILABLE'",
                String.class, tenantId, projectId, reference);
        return keys.isEmpty() ? reference : keys.get(0);
    }

    public void complete(String id, long size, String sha256) {
        jdbc.update(
                "UPDATE project_artifact SET size_bytes = ?, sha256 = ?, "
                        + "status = 'AVAILABLE', completed_at = CURRENT_TIMESTAMP "
                        + "WHERE business_id = ? AND status = 'UPLOADING'",
                size, sha256, id);
    }

    public AgentArtifactUploadContext findAgentUploadContext(
            String projectId, String conversationId, String businessSessionId,
            String agentRunId, String agentId) {
        List<AgentArtifactUploadContext> rows = jdbc.query(
                "SELECT t.tenant_id, t.business_id coordinator_task_id "
                        + "FROM coordinator_task t "
                        + "JOIN coordinator_plan p ON p.business_id = t.plan_id "
                        + "JOIN project_conversation c ON c.business_id = p.conversation_id "
                        + "WHERE t.project_id = ? AND p.conversation_id = ? "
                        + "AND c.session_id = ? AND t.session_id = ? AND t.expert_id = ? "
                        + "AND t.status IN ('RUNNING', 'WAITING_HUMAN')",
                (rs, rowNum) -> new AgentArtifactUploadContext(
                        rs.getString("tenant_id"), rs.getString("coordinator_task_id"),
                        agentId, agentRunId),
                projectId, conversationId, businessSessionId, agentRunId, agentId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean isAvailableAgentArtifact(
            String tenantId, String projectId, String coordinatorTaskId,
            String agentRunId, String artifactId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_artifact WHERE tenant_id = ? "
                        + "AND project_id = ? AND task_id = ? AND expert_run_id = ? "
                        + "AND business_id = ? AND status = 'AVAILABLE'",
                Integer.class, tenantId, projectId, coordinatorTaskId, agentRunId, artifactId);
        return count != null && count == 1;
    }

    public List<String> findAvailableStorageKeys(
            String planId, List<String> dependencyKeys) {
        if (dependencyKeys.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        String placeholders = String.join(
                ",", java.util.Collections.nCopies(dependencyKeys.size(), "?"));
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(planId);
        args.addAll(dependencyKeys);
        return jdbc.queryForList(
                "SELECT a.storage_key FROM project_artifact a "
                        + "JOIN coordinator_task t ON t.business_id = a.task_id "
                        + "WHERE t.plan_id = ? AND t.task_key IN (" + placeholders + ") "
                        + "AND a.status = 'AVAILABLE' ORDER BY a.business_id",
                String.class, args.toArray());
    }

    public void recordDependencyLineage(
            String outputArtifactId, String planId, List<String> dependencyKeys) {
        if (dependencyKeys.isEmpty()) {
            return;
        }
        String placeholders = String.join(
                ",", java.util.Collections.nCopies(dependencyKeys.size(), "?"));
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(outputArtifactId);
        args.add(planId);
        args.addAll(dependencyKeys);
        jdbc.update(
                "INSERT INTO project_artifact_lineage "
                        + "(output_artifact_id, input_artifact_id) "
                        + "SELECT ?, a.business_id FROM project_artifact a "
                        + "JOIN coordinator_task t ON t.business_id = a.task_id "
                        + "WHERE t.plan_id = ? AND t.task_key IN (" + placeholders + ") "
                        + "AND a.status = 'AVAILABLE'",
                args.toArray());
    }

    static final class ArtifactRecord {
        String id;
        String projectId;
        String taskId;
        String expertRunId;
        int version;
        String storageKey;
        String fileName;
        String mediaType;
        Long size;
        String sha256;
        String status;
        String uploadUrl;
    }
}
