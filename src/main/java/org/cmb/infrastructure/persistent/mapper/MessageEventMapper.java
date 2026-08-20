package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.cmb.application.dto.MessageAcceptedResponse;
import org.cmb.application.domain.ProjectEvent;

/**
 * SQL access for project messages and their events (project_message,
 * project_conversation, conversation_event_sequence, project_event,
 * coordinator_dispatch). Queries that may match multiple rows return
 * {@code List} so the repository facade keeps its "first row or null"
 * semantics.
 */
@Mapper
public interface MessageEventMapper {

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

    int insertSequence(
            @Param("tenantId") String tenantId,
            @Param("conversationId") String conversationId);

    List<Long> selectSequence(
            @Param("tenantId") String tenantId,
            @Param("conversationId") String conversationId);

    int updateSequence(
            @Param("tenantId") String tenantId,
            @Param("conversationId") String conversationId,
            @Param("next") long next,
            @Param("newNext") long newNext);

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

    int insertDispatch(
            @Param("id") String id,
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("conversationId") String conversationId,
            @Param("messageId") String messageId);

    List<ProjectEvent> findPublicEvents(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("conversationId") String conversationId,
            @Param("afterSequence") long afterSequence,
            @Param("limit") int limit);

    List<String> findRecentMessageTexts(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("conversationId") String conversationId,
            @Param("limit") int limit);
}
