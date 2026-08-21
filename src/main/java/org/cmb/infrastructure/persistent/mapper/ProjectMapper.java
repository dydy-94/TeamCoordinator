package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.cmb.application.domain.entity.ProjectDO;

/**
 * SQL access for projects (digital_team_project). Join queries use this
 * table as the main table. Queries that may match multiple rows return
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

    List<ProjectDO> findByTenant(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId);

    List<ProjectDO> findVisible(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("userId") String userId);

    /** 平台管理员:租户下全部项目(不按成员过滤)。 */
    List<ProjectDO> findAllByTenant(@Param("tenantId") String tenantId);

    /** 平台管理员:按 id 直查项目(不按成员过滤)。 */
    List<ProjectDO> findById(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId);

    int updateProject(
            @Param("name") String name,
            @Param("description") String description,
            @Param("coordinatorAgentId") String coordinatorAgentId,
            @Param("status") String status,
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId);
}
