package org.cmb.infrastructure.persistent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * SQL access for expert task event records
 * (digital_team_coordinator_task_event).
 */
@Mapper
public interface CoordinatorTaskEventMapper {

    int insertTaskEvent(
            @Param("id") String id,
            @Param("tenantId") String tenantId,
            @Param("taskId") String taskId,
            @Param("eventId") String eventId,
            @Param("sequence") long sequence,
            @Param("eventType") String eventType,
            @Param("payload") String payload);
}
