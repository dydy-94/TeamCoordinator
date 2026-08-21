package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * SQL access for artifact dependency lineage
 * (digital_team_project_artifact_lineage).
 */
@Mapper
public interface ProjectArtifactLineageMapper {

    int recordDependencyLineage(
            @Param("outputArtifactId") String outputArtifactId,
            @Param("planId") String planId,
            @Param("dependencyKeys") List<String> dependencyKeys);
}
