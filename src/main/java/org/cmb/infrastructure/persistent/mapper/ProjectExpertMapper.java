package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.cmb.application.domain.ProjectExpert;

/**
 * SQL access for project experts (digital_team_project_expert). Queries
 * that may match multiple rows return {@code List} so the repository
 * facade keeps its "first row or null" semantics.
 */
@Mapper
public interface ProjectExpertMapper {

    List<ProjectExpert> findExperts(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId);

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
}
