package org.cmb.teamcoordinator.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.cmb.teamcoordinator.common.ApiException;
import org.cmb.teamcoordinator.config.DigitalTeamProperties;
import org.cmb.teamcoordinator.project.RequestIdentity;
import org.springframework.stereotype.Service;

@Service
public class PromptService {

    public static final String COORDINATOR_EXECUTION = "coordinator.execution";
    public static final String COORDINATOR_PLANNING = "coordinator.planning";
    public static final String EXPERT_EXECUTION = "expert.execution";
    public static final String EXPERT_RESUME = "expert.resume";
    public static final String COORDINATOR_PLAN_CHECK = "coordinator.plan_check";
    public static final String EXPERT_RESULT_CHECK = "expert.result_check";

    private final PromptRepository repository;
    private final ObjectMapper objectMapper;
    private final List<String> adminUsers;

    public PromptService(
            PromptRepository repository, ObjectMapper objectMapper,
            DigitalTeamProperties properties) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.adminUsers = properties.getPrompt().getAdminUsers();
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
        PromptTemplateView template = repository.findPublished(promptKey);
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

    public List<PromptTemplateView> list(RequestIdentity identity, String promptKey) {
        requireAdmin(identity);
        return repository.list(promptKey);
    }

    public PromptTemplateView create(
            RequestIdentity identity, CreatePromptTemplateRequest request) {
        requireAdmin(identity);
        if (!request.getTemplateContent().contains("{{context_json}}")) {
            throw ApiException.badRequest(
                    "PROMPT_CONTEXT_REQUIRED",
                    "Prompt template must contain {{context_json}}.");
        }
        return repository.create(request, identity.getUserId());
    }

    public PromptTemplateView publish(RequestIdentity identity, String id) {
        requireAdmin(identity);
        PromptTemplateView result = repository.publish(id);
        if (result == null) {
            throw ApiException.notFound("PROMPT_NOT_FOUND", "Prompt template was not found.");
        }
        return result;
    }

    private void requireAdmin(RequestIdentity identity) {
        if (!adminUsers.contains(identity.getUserId())) {
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
