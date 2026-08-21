package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * SQL access for per-conversation event sequence allocators
 * (digital_team_conversation_event_sequence).
 */
@Mapper
public interface ConversationEventSequenceMapper {

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
}
