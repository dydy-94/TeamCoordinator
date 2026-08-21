package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.cmb.application.domain.entity.ProjectEventDO;

/**
 * SQL access for task event streams (digital_team_project_event).
 */
@Mapper
public interface ProjectEventMapper {

    int insertEvent(
            @Param("id") String id,
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("conversationId") String conversationId,
            @Param("messageId") String messageId,
            @Param("sequence") long sequence,
            @Param("eventType") String eventType,
            @Param("visibility") String visibility,
            @Param("payload") String payload);

    List<String> findNextMarkerPayload(
            @Param("tenantId") String tenantId,
            @Param("conversationId") String conversationId,
            @Param("sessionId") String sessionId,
            @Param("afterSequence") long afterSequence);

    List<ProjectEventDO> findPublicEvents(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("conversationId") String conversationId,
            @Param("afterSequence") long afterSequence,
            @Param("limit") int limit);

    List<Map<String, Object>> findEvents(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("taskId") String taskId);
}
