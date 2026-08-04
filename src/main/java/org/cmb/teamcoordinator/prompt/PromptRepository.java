package org.cmb.teamcoordinator.prompt;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.cmb.teamcoordinator.persistence.MyBatisExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PromptRepository {

    private final MyBatisExecutor jdbc;

    public PromptRepository(MyBatisExecutor jdbc) {
        this.jdbc = jdbc;
    }

    public PromptTemplateView findPublished(String promptKey) {
        List<PromptTemplateView> rows = jdbc.query(
                "SELECT * FROM prompt_template WHERE prompt_key = ? "
                        + "AND status = 'PUBLISHED' ORDER BY version DESC",
                (rs, rowNum) -> map(rs), promptKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<PromptTemplateView> list(String promptKey) {
        if (promptKey == null || promptKey.trim().isEmpty()) {
            return jdbc.query(
                    "SELECT * FROM prompt_template ORDER BY prompt_key, version DESC",
                    (rs, rowNum) -> map(rs));
        }
        return jdbc.query(
                "SELECT * FROM prompt_template WHERE prompt_key = ? ORDER BY version DESC",
                (rs, rowNum) -> map(rs), promptKey);
    }

    public PromptTemplateView create(
            CreatePromptTemplateRequest request, String createdBy) {
        Integer next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version), 0) + 1 FROM prompt_template "
                        + "WHERE prompt_key = ?",
                Integer.class, request.getPromptKey());
        String id = "prompt-" + UUID.randomUUID();
        try {
            jdbc.update(
                    "INSERT INTO prompt_template "
                            + "(business_id, prompt_key, agent_scope, scene, version, status, "
                            + "template_content, variables_schema, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?)",
                    id, request.getPromptKey(), request.getAgentScope(),
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
        jdbc.update(
                "UPDATE prompt_template SET status = 'RETIRED' "
                        + "WHERE prompt_key = ? AND status = 'PUBLISHED'",
                target.getPromptKey());
        jdbc.update(
                "UPDATE prompt_template SET status = 'PUBLISHED', "
                        + "published_at = CURRENT_TIMESTAMP WHERE business_id = ?",
                id);
        return find(id);
    }

    public PromptTemplateView find(String id) {
        List<PromptTemplateView> rows = jdbc.query(
                "SELECT * FROM prompt_template WHERE business_id = ?",
                (rs, rowNum) -> map(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void audit(
            String tenantId, String projectId, String conversationId,
            String invocationId, String agentId, PromptTemplateView template,
            String renderedPrompt, String variablesSnapshot) {
        try {
            jdbc.update(
                    "INSERT INTO prompt_execution "
                            + "(business_id, tenant_id, project_id, conversation_id, invocation_id, "
                            + "agent_id, scene, prompt_template_id, prompt_version, "
                            + "rendered_prompt, variables_snapshot) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    "prompt-exec-" + UUID.randomUUID(), tenantId, projectId,
                    conversationId, invocationId, agentId, template.getScene(),
                    template.getId(), template.getVersion(), renderedPrompt,
                    variablesSnapshot);
        } catch (DuplicateKeyException ignored) {
            // Retries reuse the prompt snapshot already recorded for this invocation.
        }
    }

    private PromptTemplateView map(org.cmb.teamcoordinator.persistence.MyBatisRow rs) {
        PromptTemplateView value = new PromptTemplateView();
        value.setId(rs.getString("business_id"));
        value.setPromptKey(rs.getString("prompt_key"));
        value.setAgentScope(rs.getString("agent_scope"));
        value.setScene(rs.getString("scene"));
        value.setVersion(rs.getInt("version"));
        value.setStatus(rs.getString("status"));
        value.setTemplateContent(rs.getString("template_content"));
        value.setVariablesSchema(rs.getString("variables_schema"));
        Timestamp created = rs.getTimestamp("created_at");
        value.setCreatedAt(created == null ? null : created.toInstant());
        Timestamp published = rs.getTimestamp("published_at");
        value.setPublishedAt(published == null ? null : published.toInstant());
        return value;
    }
}
