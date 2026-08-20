package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.cmb.teamcoordinator.prompt.PromptTemplateView;

/**
 * SQL access for prompt management (prompt_template, prompt_execution).
 * Queries that may match multiple rows return {@code List} so the
 * repository facade keeps its "first row or null" semantics.
 */
@Mapper
public interface PromptMapper {

    List<PromptTemplateView> findPublished(@Param("promptKey") String promptKey);

    List<PromptTemplateView> list(@Param("promptKey") String promptKey);

    Integer selectNextVersion(@Param("promptKey") String promptKey);

    int insertTemplate(
            @Param("id") String id,
            @Param("promptKey") String promptKey,
            @Param("agentScope") String agentScope,
            @Param("scene") String scene,
            @Param("version") int version,
            @Param("templateContent") String templateContent,
            @Param("variablesSchema") String variablesSchema,
            @Param("createdBy") String createdBy);

    int retirePublished(@Param("promptKey") String promptKey);

    int publish(@Param("id") String id);

    List<PromptTemplateView> find(@Param("id") String id);

    int insertAudit(
            @Param("id") String id,
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("conversationId") String conversationId,
            @Param("invocationId") String invocationId,
            @Param("agentId") String agentId,
            @Param("scene") String scene,
            @Param("templateId") String templateId,
            @Param("templateVersion") int templateVersion,
            @Param("renderedPrompt") String renderedPrompt,
            @Param("variablesSnapshot") String variablesSnapshot);
}
