package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.cmb.application.dto.MessageAcceptedResponse;

/**
 * SQL access for project messages (digital_team_project_message).
 * Join queries use this table as the main table. Queries that may match
 * multiple rows return {@code List} so the repository facade keeps its
 * "first row or null" semantics.
 */
@Mapper
public interface ProjectMessageMapper {

    List<MessageAcceptedResponse> findDuplicate(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("conversationId") String conversationId,
            @Param("clientMessageId") String clientMessageId);

    int insertMessage(
            @Param("id") String id,
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("conversationId") String conversationId,
            @Param("userId") String userId,
            @Param("clientMessageId") String clientMessageId,
            @Param("text") String text,
            @Param("attachmentRefs") String attachmentRefs);

    List<String> findRecentMessageTexts(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("conversationId") String conversationId,
            @Param("limit") int limit);

    int appendMessageText(
            @Param("answer") String answer,
            @Param("messageId") String messageId,
            @Param("tenantId") String tenantId);

    List<Map<String, Object>> findMessages(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("taskId") String taskId);
}
