package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.cmb.application.domain.entity.ProjectMemberDO;

/**
 * SQL access for project members (digital_team_project_member). Queries
 * that may match multiple rows return {@code List} so the repository
 * facade keeps its "first row or null" semantics.
 */
@Mapper
public interface ProjectMemberMapper {

    List<String> findRole(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("userId") String userId);

    List<ProjectMemberDO> findMembers(
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
}
