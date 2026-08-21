package org.cmb.application.service.impl;

import org.cmb.application.service.PromptService;
import org.cmb.application.dto.CreatePromptTemplateRequest;
import org.cmb.application.dto.RenderedPrompt;
import org.cmb.application.domain.entity.PromptTemplateDO;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.cmb.infrastructure.persistent.PlatformAdminRepository;
import org.cmb.infrastructure.persistent.PromptRepository;
import org.cmb.common.exception.ApiException;
import org.cmb.common.config.DigitalTeamProperties;
import org.cmb.application.domain.RequestIdentity;
import org.springframework.stereotype.Service;

@Service
public class PromptServiceImpl implements PromptService {

    private final PromptRepository repository;
    private final ObjectMapper objectMapper;
    private final List<String> platformAdmins;
    private final PlatformAdminRepository platformAdminTable;

    public PromptServiceImpl(
            PromptRepository repository, ObjectMapper objectMapper,
            DigitalTeamProperties properties,
            PlatformAdminRepository platformAdminTable) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.platformAdmins = properties.getPlatform().getAdminUsers();
        this.platformAdminTable = platformAdminTable;
    }

    public RenderedPrompt render(
            String promptKey, Object context, String tenantId, String projectId,
            String conversationId, String invocationId, String agentId) {
        return render(promptKey, context, null, tenantId, projectId,
                conversationId, invocationId, agentId);
    }

    /**
     * Render a prompt template, additionally replacing {@code {{variable}}}
     * placeholders found in {@code extraVariables} before the context JSON.
     * Variables that do not appear in the template are ignored.
     */
    public RenderedPrompt render(
            String promptKey, Object context, Map<String, String> extraVariables,
            String tenantId, String projectId, String conversationId,
            String invocationId, String agentId) {
        PromptTemplateDO template = repository.findPublished(promptKey);
        if (template == null) {
            throw new IllegalStateException(
                    "No published prompt template exists for " + promptKey);
        }
        String rendered = template.getTemplateContent();
        if (extraVariables != null) {
            for (Map.Entry<String, String> entry : extraVariables.entrySet()) {
                rendered = rendered.replace(
                        "{{" + entry.getKey() + "}}", entry.getValue());
            }
        }
        String contextJson = write(context);
        rendered = rendered.replace("{{context_json}}", contextJson);
        if (rendered.contains("{{")) {
            throw new IllegalStateException(
                    "Prompt template contains unresolved variables: " + promptKey);
        }
        repository.audit(
                tenantId, projectId, conversationId, invocationId, agentId,
                template, rendered, contextJson);
        return new RenderedPrompt(rendered, template.getVersion(), template.getId());
    }

    public List<PromptTemplateDO> list(RequestIdentity identity, String promptKey) {
        requireAdmin(identity);
        return repository.list(promptKey);
    }

    public PromptTemplateDO create(
            RequestIdentity identity, CreatePromptTemplateRequest request) {
        requireAdmin(identity);
        if (!request.getTemplateContent().contains("{{context_json}}")) {
            throw ApiException.badRequest(
                    "PROMPT_CONTEXT_REQUIRED",
                    "Prompt template must contain {{context_json}}.");
        }
        return repository.create(request, identity.getUserId());
    }

    public PromptTemplateDO publish(RequestIdentity identity, String id) {
        requireAdmin(identity);
        PromptTemplateDO result = repository.publish(id);
        if (result == null) {
            throw ApiException.notFound("PROMPT_NOT_FOUND", "Prompt template was not found.");
        }
        return result;
    }

    @Override
    public void delete(RequestIdentity identity, String id) {
        requireAdmin(identity);
        PromptTemplateDO target = repository.find(id);
        if (target == null) {
            throw ApiException.notFound("PROMPT_NOT_FOUND", "Prompt template was not found.");
        }
        if ("PUBLISHED".equals(target.getStatus())) {
            throw ApiException.conflict(
                    "PROMPT_PUBLISHED_DELETE_FORBIDDEN",
                    "The published version cannot be deleted; "
                            + "publish another version of the same key first.");
        }
        repository.delete(id);
    }

    /** 提示词管理与租户管理统一鉴权:平台管理员(env ∪ 表)。 */
    private void requireAdmin(RequestIdentity identity) {
        String userId = identity.getUserId();
        if (!platformAdmins.contains(userId) && !platformAdminTable.isAdmin(userId)) {
            throw ApiException.forbidden(
                    "PROMPT_ADMIN_REQUIRED", "Prompt administrator permission is required.");
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize prompt context.", ex);
        }
    }
}
