package org.cmb.infrastructure.persistent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * SQL access for project-skill bindings (digital_team_project_skill).
 */
@Mapper
public interface ProjectSkillMapper {

    Integer countProjectSkill(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("skillId") String skillId);

    int insertProjectSkill(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("skillId") String skillId,
            @Param("enabled") boolean enabled);

    int updateProjectSkill(
            @Param("enabled") boolean enabled,
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("skillId") String skillId);

    int deleteProjectSkill(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("skillId") String skillId);
}
