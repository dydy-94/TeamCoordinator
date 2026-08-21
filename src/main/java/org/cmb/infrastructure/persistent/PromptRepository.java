package org.cmb.infrastructure.persistent;

import java.util.List;
import java.util.UUID;
import org.cmb.infrastructure.persistent.mapper.PromptExecutionMapper;
import org.cmb.infrastructure.persistent.mapper.PromptTemplateMapper;
import org.cmb.application.dto.CreatePromptTemplateRequest;
import org.cmb.application.dto.PromptTemplateView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prompt-template persistence facade. All SQL lives in
 * {@link PromptTemplateMapper} and {@link PromptExecutionMapper}.
 */
@Repository
public class PromptRepository {

    private final PromptTemplateMapper mapper;
    private final PromptExecutionMapper executionMapper;

    public PromptRepository(PromptTemplateMapper mapper, PromptExecutionMapper executionMapper) {
        this.mapper = mapper;
        this.executionMapper = executionMapper;
    }

    public PromptTemplateView findPublished(String promptKey) {
        List<PromptTemplateView> rows = mapper.findPublished(promptKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<PromptTemplateView> list(String promptKey) {
        return mapper.list(promptKey);
    }

    public PromptTemplateView create(
            CreatePromptTemplateRequest request, String createdBy) {
        Integer next = mapper.selectNextVersion(request.getPromptKey());
        String id = "prompt-" + UUID.randomUUID();
        try {
            mapper.insertTemplate(id, request.getPromptKey(), request.getAgentScope(),
                    request.getScene(), next, request.getTemplateContent(),
                    request.getVariablesSchema(), createdBy);
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("Concurrent prompt version creation failed.", ex);
        }
        return find(id);
    }

    @Transactional
    public PromptTemplateView publish(String id) {
        PromptTemplateView target = find(id);
        if (target == null) {
            return null;
        }
        mapper.retirePublished(target.getPromptKey());
        mapper.publish(id);
        return find(id);
    }

    public PromptTemplateView find(String id) {
        List<PromptTemplateView> rows = mapper.find(id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void audit(
            String tenantId, String projectId, String conversationId,
            String invocationId, String agentId, PromptTemplateView template,
            String renderedPrompt, String variablesSnapshot) {
        try {
            executionMapper.insertAudit("prompt-exec-" + UUID.randomUUID(), tenantId, projectId,
                    conversationId, invocationId, agentId, template.getScene(),
                    template.getId(), template.getVersion(), renderedPrompt,
                    variablesSnapshot);
        } catch (DuplicateKeyException ignored) {
            // Retries reuse the prompt snapshot already recorded for this invocation.
        }
    }
}
