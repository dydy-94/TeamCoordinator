package org.cmb.application.domain.entity;

import java.time.Instant;

/**
 * Row shape of digital_team_coordinator_plan. Definition-only: current
 * read/write paths use scalar parameters and map projections
 * (CoordinatorPlanMapper.findPlans keeps its alias contract).
 */
public record CoordinatorPlanDO(
        Long id,
        String businessId,
        String tenantId,
        String projectId,
        String conversationId,
        String messageId,
        String analysisId,
        String status,
        int planVersion,
        String intentJson,
        String planJson,
        int repairCount,
        String supersedesPlanId,
        Instant createdAt,
        Instant updatedAt) {
}
