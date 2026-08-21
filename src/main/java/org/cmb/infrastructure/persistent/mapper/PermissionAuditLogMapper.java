package org.cmb.infrastructure.persistent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * SQL access for permission audit records (digital_team_permission_audit_log).
 */
@Mapper
public interface PermissionAuditLogMapper {

    int insertAudit(
            @Param("id") String id,
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("actorUserId") String actorUserId,
            @Param("action") String action,
            @Param("targetId") String targetId,
            @Param("detail") String detail);
}
