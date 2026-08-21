package org.cmb.application.domain.entity;

import java.time.Instant;

/**
 * Row shape of digital_team_prompt_execution. Definition-only:
 * PromptExecutionMapper is insert-only with scalar parameters.
 */
public record PromptExecutionDO(
        Long id,
        String businessId,
        String tenantId,
        String projectId,
        String conversationId,
        String invocationId,
        String agentId,
        String scene,
        String promptTemplateId,
        int promptVersion,
        String renderedPrompt,
        String variablesSnapshot,
        Instant createdAt) {
}
