package org.cmb.application.domain.entity;

import java.time.Instant;

/**
 * Row shape of digital_team_project_artifact_lineage. Definition-only:
 * ProjectArtifactLineageMapper is insert-only with scalar parameters.
 */
public record ProjectArtifactLineageDO(
        Long id,
        String outputArtifactId,
        String inputArtifactId,
        Instant createdAt) {
}
