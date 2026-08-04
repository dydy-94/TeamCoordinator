package org.cmb.teamcoordinator.project;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectRepository {

    private final JdbcTemplate jdbc;

    public ProjectRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertProject(ProjectRecord project) {
        jdbc.update(
                "INSERT INTO project (business_id, tenant_id, name, description, status, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                project.getId(),
                project.getTenantId(),
                project.getName(),
                project.getDescription(),
                project.getStatus().name(),
                project.getCreatedBy());
    }

    public ProjectRecord findVisible(String tenantId, String projectId, String userId) {
        List<ProjectRecord> rows = jdbc.query(
                "SELECT p.* FROM project p JOIN project_member m ON m.project_id = p.business_id "
                        + "AND m.tenant_id = p.tenant_id "
                        + "WHERE p.tenant_id = ? AND p.business_id = ? AND m.user_id = ?",
                projectMapper(),
                tenantId,
                projectId,
                userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public ProjectRole findRole(String tenantId, String projectId, String userId) {
        List<String> roles = jdbc.queryForList(
                "SELECT role FROM project_member WHERE tenant_id = ? AND project_id = ? AND user_id = ?",
                String.class,
                tenantId,
                projectId,
                userId);
        return roles.isEmpty() ? null : ProjectRole.valueOf(roles.get(0));
    }

    public List<ProjectMember> findMembers(String tenantId, String projectId) {
        return jdbc.query(
                "SELECT user_id, role FROM project_member WHERE tenant_id = ? AND project_id = ? "
                        + "ORDER BY user_id",
                (rs, rowNum) -> new ProjectMember(
                        rs.getString("user_id"), ProjectRole.valueOf(rs.getString("role"))),
                tenantId,
                projectId);
    }

    public List<ProjectExpert> findExperts(String tenantId, String projectId) {
        return jdbc.query(
                "SELECT expert_id, enabled FROM project_expert WHERE tenant_id = ? AND project_id = ? "
                        + "ORDER BY expert_id",
                (rs, rowNum) ->
                        new ProjectExpert(rs.getString("expert_id"), rs.getBoolean("enabled")),
                tenantId,
                projectId);
    }

    public void updateProject(
            String tenantId,
            String projectId,
            String name,
            String description,
            ProjectStatus status) {
        jdbc.update(
                "UPDATE project SET name = ?, description = ?, status = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE tenant_id = ? AND business_id = ?",
                name,
                description,
                status.name(),
                tenantId,
                projectId);
    }

    public boolean memberExists(String tenantId, String projectId, String userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_member "
                        + "WHERE tenant_id = ? AND project_id = ? AND user_id = ?",
                Integer.class,
                tenantId,
                projectId,
                userId);
        return count != null && count > 0;
    }

    public void insertMember(
            String tenantId, String projectId, String userId, ProjectRole role) {
        jdbc.update(
                "INSERT INTO project_member (tenant_id, project_id, user_id, role) VALUES (?, ?, ?, ?)",
                tenantId,
                projectId,
                userId,
                role.name());
    }

    public void updateMember(
            String tenantId, String projectId, String userId, ProjectRole role) {
        jdbc.update(
                "UPDATE project_member SET role = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE tenant_id = ? AND project_id = ? AND user_id = ?",
                role.name(),
                tenantId,
                projectId,
                userId);
    }

    public int deleteMember(String tenantId, String projectId, String userId) {
        return jdbc.update(
                "DELETE FROM project_member WHERE tenant_id = ? AND project_id = ? AND user_id = ?",
                tenantId,
                projectId,
                userId);
    }

    public boolean expertExists(String tenantId, String projectId, String expertId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_expert "
                        + "WHERE tenant_id = ? AND project_id = ? AND expert_id = ?",
                Integer.class,
                tenantId,
                projectId,
                expertId);
        return count != null && count > 0;
    }

    public void insertExpert(
            String tenantId, String projectId, String expertId, boolean enabled) {
        jdbc.update(
                "INSERT INTO project_expert (tenant_id, project_id, expert_id, enabled) VALUES (?, ?, ?, ?)",
                tenantId,
                projectId,
                expertId,
                enabled);
    }

    public void updateExpert(
            String tenantId, String projectId, String expertId, boolean enabled) {
        jdbc.update(
                "UPDATE project_expert SET enabled = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE tenant_id = ? AND project_id = ? AND expert_id = ?",
                enabled,
                tenantId,
                projectId,
                expertId);
    }

    public int deleteExpert(String tenantId, String projectId, String expertId) {
        return jdbc.update(
                "DELETE FROM project_expert WHERE tenant_id = ? AND project_id = ? AND expert_id = ?",
                tenantId,
                projectId,
                expertId);
    }

    public void audit(
            RequestIdentity identity,
            String projectId,
            String action,
            String targetId,
            String detail) {
        jdbc.update(
                "INSERT INTO permission_audit_log "
                        + "(business_id, tenant_id, project_id, actor_user_id, action, "
                        + "target_id, detail) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "audit-" + UUID.randomUUID(),
                identity.getTenantId(),
                projectId,
                identity.getUserId(),
                action,
                targetId,
                detail);
    }

    private RowMapper<ProjectRecord> projectMapper() {
        return (rs, rowNum) -> mapProject(rs);
    }

    private ProjectRecord mapProject(ResultSet rs) throws SQLException {
        ProjectRecord project = new ProjectRecord();
        project.setDatabaseId(rs.getLong("id"));
        project.setBusinessId(rs.getString("business_id"));
        project.setTenantId(rs.getString("tenant_id"));
        project.setName(rs.getString("name"));
        project.setDescription(rs.getString("description"));
        project.setStatus(ProjectStatus.valueOf(rs.getString("status")));
        project.setCreatedBy(rs.getString("created_by"));
        project.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
        project.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
        return project;
    }

    private Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
