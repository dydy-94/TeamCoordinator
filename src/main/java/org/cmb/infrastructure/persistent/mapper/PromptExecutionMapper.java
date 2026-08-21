package org.cmb.infrastructure.persistent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * SQL access for prompt render audits (digital_team_prompt_execution).
 */
@Mapper
public interface PromptExecutionMapper {

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

    int deleteByTemplate(@Param("templateId") String templateId);
}
