package org.cmb.infrastructure.persistent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * SQL access for intent analysis runs (coordinator_analysis).
 */
@Mapper
public interface CoordinatorAnalysisMapper {

    int insertAnalysis(
            @Param("analysisId") String analysisId,
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("userId") String userId,
            @Param("inputSnapshot") String inputSnapshot,
            @Param("modelName") String modelName,
            @Param("promptVersion") String promptVersion,
            @Param("schemaVersion") String schemaVersion,
            @Param("decisionType") String decisionType,
            @Param("decisionJson") String decisionJson,
            @Param("repaired") boolean repaired);
}
