package org.cmb.application.domain.entity;

import java.time.Instant;

/**
 * Row shape of digital_team_coordinator_cli_submission. Definition-only:
 * current paths use scalar parameters and single-string reads.
 */
public record CoordinatorCliSubmissionDO(
        Long id,
        String businessId,
        String taskId,
        String kind,
        String payload,
        Instant createdAt) {
}
