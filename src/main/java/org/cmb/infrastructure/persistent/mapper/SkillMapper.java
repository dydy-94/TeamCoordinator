package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.cmb.application.domain.entity.SkillDO;

/**
 * SQL access for skills (digital_team_skill). Join queries use this table
 * as the main table. Queries that may match multiple rows return
 * {@code List} so the repository facade keeps its "first row or null"
 * semantics.
 */
@Mapper
public interface SkillMapper {

    List<SkillDO> listAll();

    List<SkillDO> findByBusinessId(@Param("skillId") String skillId);

    List<SkillDO> findByProject(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId);
}
