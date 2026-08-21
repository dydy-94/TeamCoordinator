package org.cmb.application.domain.entity;

import java.time.Instant;

/**
 * Row shape of digital_team_project_conversation_expert_session. The
 * {@code id} column is the business id itself (VARCHAR PK, no separate
 * business_id). Definition-only: reads return a single session id string
 * and upserts use scalar parameters.
 */
public record ProjectConversationExpertSessionDO(
        String id,
        String tenantId,
        String projectId,
        String conversationId,
        String expertId,
        String sessionId,
        String messageId,
        Instant createdAt) {
}
