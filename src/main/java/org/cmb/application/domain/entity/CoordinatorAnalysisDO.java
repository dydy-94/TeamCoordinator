package org.cmb.application.domain.entity;

import java.time.Instant;

/**
 * Row shape of digital_team_coordinator_analysis. Definition-only:
 * CoordinatorAnalysisMapper is insert-only with scalar parameters.
 */
public record CoordinatorAnalysisDO(
        Long id,
        String businessId,
        String tenantId,
        String projectId,
        String userId,
        String inputSnapshot,
        String modelName,
        String promptVersion,
        String schemaVersion,
        String decisionType,
        String decisionJson,
        boolean repaired,
        Instant createdAt) {
}
