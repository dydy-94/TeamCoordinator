package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.cmb.application.domain.ProjectExpert;
import org.cmb.application.domain.ProjectMember;
import org.cmb.application.domain.ProjectRecord;

/**
 * SQL access for projects (project, project_member, project_expert,
 * permission_audit_log). Queries that may match multiple rows return
 * {@code List} so the repository facade keeps its "first row or null"
 * semantics.
 */
@Mapper
public interface ProjectMapper {

    int insertProject(
            @Param("businessId") String businessId,
            @Param("tenantId") String tenantId,
            @Param("name") String name,
            @Param("description") String description,
            @Param("coordinatorAgentId") String coordinatorAgentId,
            @Param("status") String status,
            @Param("createdBy") String createdBy);

    List<ProjectRecord> findByTenant(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId);

    List<ProjectRecord> findVisible(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("userId") String userId);

    List<String> findRole(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("userId") String userId);

    List<ProjectMember> findMembers(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId);

    List<ProjectExpert> findExperts(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId);

    int updateProject(
            @Param("name") String name,
            @Param("description") String description,
            @Param("coordinatorAgentId") String coordinatorAgentId,
            @Param("status") String status,
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId);

    Integer countMember(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("userId") String userId);

    int insertMember(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("userId") String userId,
            @Param("role") String role);

    int updateMember(
            @Param("role") String role,
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("userId") String userId);

    int deleteMember(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("userId") String userId);

    Integer countExpert(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("expertId") String expertId);

    int insertExpert(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("expertId") String expertId,
            @Param("enabled") boolean enabled);

    int updateExpert(
            @Param("enabled") boolean enabled,
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("expertId") String expertId);

    int deleteExpert(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("expertId") String expertId);

    int insertAudit(
            @Param("id") String id,
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("actorUserId") String actorUserId,
            @Param("action") String action,
            @Param("targetId") String targetId,
            @Param("detail") String detail);
}
