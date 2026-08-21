package org.cmb.application.service;

import java.util.List;
import java.util.Map;
import org.cmb.application.domain.RequestIdentity;
import org.cmb.application.domain.entity.PromptTemplateDO;
import org.cmb.application.dto.CreatePromptTemplateRequest;
import org.cmb.application.dto.RenderedPrompt;

/**
 * Prompt template management and rendering with audit persistence.
 */
public interface PromptService {

    String COORDINATOR_EXECUTION = "coordinator.execution";
    String COORDINATOR_PLANNING = "coordinator.planning";
    String EXPERT_EXECUTION = "expert.execution";
    String EXPERT_RESUME = "expert.resume";
    String COORDINATOR_PLAN_CHECK = "coordinator.plan_check";
    String EXPERT_RESULT_CHECK = "expert.result_check";

    RenderedPrompt render(
            String promptKey, Object context, String tenantId, String projectId,
            String conversationId, String invocationId, String agentId);

    RenderedPrompt render(
            String promptKey, Object context, Map<String, String> extraVariables,
            String tenantId, String projectId, String conversationId,
            String invocationId, String agentId);

    List<PromptTemplateDO> list(RequestIdentity identity, String promptKey);

    PromptTemplateDO create(
            RequestIdentity identity, CreatePromptTemplateRequest request);

    PromptTemplateDO publish(RequestIdentity identity, String id);
}
