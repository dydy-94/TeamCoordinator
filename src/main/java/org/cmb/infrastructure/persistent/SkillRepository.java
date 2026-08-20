package org.cmb.infrastructure.persistent;

import java.util.List;
import org.cmb.infrastructure.persistent.mapper.SkillMapper;
import org.cmb.teamcoordinator.project.Skill;
import org.springframework.stereotype.Repository;

/**
 * Repository for platform-level skills and project-skill associations.
 * All SQL lives in {@link SkillMapper}.
 */
@Repository
public class SkillRepository {

    private final SkillMapper mapper;

    public SkillRepository(SkillMapper mapper) {
        this.mapper = mapper;
    }

    // ── Global skill pool ─────────────────────────────────────────────────

    public List<Skill> listAll() {
        return mapper.listAll();
    }

    public Skill findByBusinessId(String skillId) {
        List<Skill> rows = mapper.findByBusinessId(skillId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ── Project-skill associations ────────────────────────────────────────

    public List<Skill> findByProject(String tenantId, String projectId) {
        return mapper.findByProject(tenantId, projectId);
    }

    public boolean projectSkillExists(String tenantId, String projectId, String skillId) {
        Integer count = mapper.countProjectSkill(tenantId, projectId, skillId);
        return count != null && count > 0;
    }

    public void insertProjectSkill(
            String tenantId, String projectId, String skillId, boolean enabled) {
        mapper.insertProjectSkill(tenantId, projectId, skillId, enabled);
    }

    public void updateProjectSkill(
            String tenantId, String projectId, String skillId, boolean enabled) {
        mapper.updateProjectSkill(enabled, tenantId, projectId, skillId);
    }

    public int deleteProjectSkill(String tenantId, String projectId, String skillId) {
        return mapper.deleteProjectSkill(tenantId, projectId, skillId);
    }
}
