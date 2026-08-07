package org.cmb.teamcoordinator.project;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.cmb.teamcoordinator.persistence.MyBatisExecutor;
import org.cmb.teamcoordinator.persistence.MyBatisRow;
import org.springframework.stereotype.Repository;

/**
 * Repository for platform-level skills and project-skill associations.
 */
@Repository
public class SkillRepository {

    private final MyBatisExecutor jdbc;

    public SkillRepository(MyBatisExecutor jdbc) {
        this.jdbc = jdbc;
    }

    // ── Global skill pool ─────────────────────────────────────────────────

    public List<Skill> listAll() {
        return jdbc.query(
                "SELECT id, business_id, name, description, prompt, created_at, updated_at "
                        + "FROM skill ORDER BY name",
                (rs, rowNum) -> mapSkill(rs));
    }

    public Skill findByBusinessId(String skillId) {
        List<Skill> rows = jdbc.query(
                "SELECT id, business_id, name, description, prompt, created_at, updated_at "
                        + "FROM skill WHERE business_id = ?",
                (rs, rowNum) -> mapSkill(rs),
                skillId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ── Project-skill associations ────────────────────────────────────────

    public List<Skill> findByProject(String tenantId, String projectId) {
        return jdbc.query(
                "SELECT s.id, s.business_id, s.name, s.description, s.prompt, "
                        + "s.created_at, s.updated_at, ps.enabled "
                        + "FROM skill s JOIN project_skill ps "
                        + "ON ps.skill_id = s.business_id "
                        + "WHERE ps.tenant_id = ? AND ps.project_id = ? "
                        + "ORDER BY s.name",
                (rs, rowNum) -> {
                    Skill skill = mapSkill(rs);
                    skill.setEnabled(rs.getBoolean("enabled"));
                    return skill;
                },
                tenantId,
                projectId);
    }

    public boolean projectSkillExists(String tenantId, String projectId, String skillId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_skill "
                        + "WHERE tenant_id = ? AND project_id = ? AND skill_id = ?",
                Integer.class,
                tenantId,
                projectId,
                skillId);
        return count != null && count > 0;
    }

    public void insertProjectSkill(
            String tenantId, String projectId, String skillId, boolean enabled) {
        jdbc.update(
                "INSERT INTO project_skill (tenant_id, project_id, skill_id, enabled) "
                        + "VALUES (?, ?, ?, ?)",
                tenantId,
                projectId,
                skillId,
                enabled);
    }

    public void updateProjectSkill(
            String tenantId, String projectId, String skillId, boolean enabled) {
        jdbc.update(
                "UPDATE project_skill SET enabled = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE tenant_id = ? AND project_id = ? AND skill_id = ?",
                enabled,
                tenantId,
                projectId,
                skillId);
    }

    public int deleteProjectSkill(String tenantId, String projectId, String skillId) {
        return jdbc.update(
                "DELETE FROM project_skill WHERE tenant_id = ? AND project_id = ? AND skill_id = ?",
                tenantId,
                projectId,
                skillId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Skill mapSkill(MyBatisRow rs) {
        Skill skill = new Skill();
        skill.setDatabaseId(rs.getLong("id"));
        skill.setBusinessId(rs.getString("business_id"));
        skill.setName(rs.getString("name"));
        skill.setDescription(rs.getString("description"));
        skill.setPrompt(rs.getString("prompt"));
        skill.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
        skill.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
        return skill;
    }

    private Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
