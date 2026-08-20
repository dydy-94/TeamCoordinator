package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.cmb.application.domain.Skill;

/**
 * SQL access for platform-level skills and project-skill associations
 * (skill, project_skill). Queries that may match multiple rows return
 * {@code List} so the repository facade keeps its "first row or null"
 * semantics.
 */
@Mapper
public interface SkillMapper {

    List<Skill> listAll();

    List<Skill> findByBusinessId(@Param("skillId") String skillId);

    List<Skill> findByProject(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId);

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
