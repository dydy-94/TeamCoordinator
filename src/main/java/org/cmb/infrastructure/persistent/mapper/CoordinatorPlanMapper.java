package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.cmb.application.domain.DispatchWork;

/**
 * SQL access for execution plans (digital_team_coordinator_plan).
 * Queries that may match multiple rows return {@code List} so the
 * repository facade keeps its "first row or null" semantics.
 */
@Mapper
public interface CoordinatorPlanMapper {

    List<String> findExistingPlanId(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("messageId") String messageId);

    List<String> findLatestPlanId(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("messageId") String messageId);

    int insertPlanSimple(
            @Param("id") String id,
            @Param("work") DispatchWork work,
            @Param("analysisId") String analysisId,
            @Param("intentJson") String intentJson);

    int insertPlanFull(
            @Param("id") String id,
            @Param("work") DispatchWork work,
            @Param("analysisId") String analysisId,
            @Param("planVersion") int planVersion,
            @Param("intentJson") String intentJson,
            @Param("planJson") String planJson,
            @Param("repairCount") int repairCount);

    int insertReplan(
            @Param("id") String id,
            @Param("work") DispatchWork work,
            @Param("analysisId") String analysisId,
            @Param("planVersion") int planVersion,
            @Param("intentJson") String intentJson,
            @Param("planJson") String planJson,
            @Param("repairCount") int repairCount,
            @Param("supersedesPlanId") String supersedesPlanId);

    int supersedePlan(@Param("planId") String planId);

    int updatePlanStatus(
            @Param("planId") String planId,
            @Param("status") String status);

    int failPlansForMessage(
            @Param("tenantId") String tenantId,
            @Param("messageId") String messageId);

    int failPlan(
            @Param("status") String status,
            @Param("planId") String planId);

    List<Map<String, Object>> findPlans(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("taskId") String taskId);
}
