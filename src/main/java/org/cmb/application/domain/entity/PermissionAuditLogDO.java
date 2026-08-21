package org.cmb.application.domain.entity;

import java.time.Instant;

/**
 * Row shape of digital_team_permission_audit_log. Definition-only:
 * PermissionAuditLogMapper is insert-only with scalar parameters.
 */
public record PermissionAuditLogDO(
        Long id,
        String businessId,
        String tenantId,
        String projectId,
        String actorUserId,
        String action,
        String targetId,
        String detail,
        Instant createdAt) {
}
