package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * SQL access for expert-session reuse records
 * (digital_team_project_conversation_expert_session).
 */
@Mapper
public interface ProjectConversationExpertSessionMapper {

    List<String> findExpertSession(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("conversationId") String conversationId,
            @Param("expertId") String expertId,
            @Param("currentMessageId") String currentMessageId);

    int upsertExpertSession(
            @Param("id") String id,
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("conversationId") String conversationId,
            @Param("expertId") String expertId,
            @Param("sessionId") String sessionId,
            @Param("messageId") String messageId);
}
