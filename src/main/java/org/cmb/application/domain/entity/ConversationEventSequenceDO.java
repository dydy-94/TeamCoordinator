package org.cmb.application.domain.entity;

/**
 * Row shape of digital_team_conversation_event_sequence. Definition-only:
 * selectSequence returns a long and insert/update use scalar parameters.
 */
public record ConversationEventSequenceDO(
        Long id,
        String tenantId,
        String conversationId,
        Long nextSequence) {
}
