package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.cmb.application.domain.entity.PromptTemplateDO;

/**
 * SQL access for prompt templates (digital_team_prompt_template).
 * Queries that may match multiple rows return {@code List} so the
 * repository facade keeps its "first row or null" semantics.
 */
@Mapper
public interface PromptTemplateMapper {

    List<PromptTemplateDO> findPublished(@Param("promptKey") String promptKey);

    List<PromptTemplateDO> list(@Param("promptKey") String promptKey);

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

    List<PromptTemplateDO> find(@Param("id") String id);
}
