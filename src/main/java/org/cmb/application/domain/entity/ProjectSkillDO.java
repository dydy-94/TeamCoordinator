package org.cmb.application.domain.entity;

import java.time.Instant;

/**
 * Row shape of digital_team_project_skill (composite PK: project_id +
 * skill_id, no surrogate id). Definition-only: current paths use scalar
 * parameters only.
 */
public record ProjectSkillDO(
        String projectId,
        String tenantId,
        String skillId,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
}
