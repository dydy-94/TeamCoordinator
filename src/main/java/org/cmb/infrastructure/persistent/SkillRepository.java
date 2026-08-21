package org.cmb.infrastructure.persistent;

import java.util.List;
import org.cmb.infrastructure.persistent.mapper.ProjectSkillMapper;
import org.cmb.infrastructure.persistent.mapper.SkillMapper;
import org.cmb.application.domain.Skill;
import org.springframework.stereotype.Repository;

/**
 * Repository for platform-level skills and project-skill associations.
 * All SQL lives in {@link SkillMapper} and {@link ProjectSkillMapper}.
 */
@Repository
public class SkillRepository {

    private final SkillMapper mapper;
    private final ProjectSkillMapper projectSkillMapper;

    public SkillRepository(SkillMapper mapper, ProjectSkillMapper projectSkillMapper) {
        this.mapper = mapper;
        this.projectSkillMapper = projectSkillMapper;
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
        Integer count = projectSkillMapper.countProjectSkill(tenantId, projectId, skillId);
        return count != null && count > 0;
    }

    public void insertProjectSkill(
            String tenantId, String projectId, String skillId, boolean enabled) {
        projectSkillMapper.insertProjectSkill(tenantId, projectId, skillId, enabled);
    }

    public void updateProjectSkill(
            String tenantId, String projectId, String skillId, boolean enabled) {
        projectSkillMapper.updateProjectSkill(enabled, tenantId, projectId, skillId);
    }

    public int deleteProjectSkill(String tenantId, String projectId, String skillId) {
        return projectSkillMapper.deleteProjectSkill(tenantId, projectId, skillId);
    }
}
