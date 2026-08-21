package org.cmb.infrastructure.persistent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cmb.infrastructure.persistent.mapper.ProjectConversationMapper;
import org.cmb.application.domain.entity.ConversationDO;
import org.cmb.application.domain.RequestIdentity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conversation-task persistence facade. All SQL lives in
 * {@link ProjectConversationMapper}. Deletion cascades across every table
 * that references the conversation (see the cascade statements in the XML).
 */
@Repository
public class ConversationTaskRepository {

    private final ProjectConversationMapper mapper;

    public ConversationTaskRepository(ProjectConversationMapper mapper) {
        this.mapper = mapper;
    }

    public ConversationDO create(
            RequestIdentity identity, String projectId, String title) {
        String taskId = "task-" + UUID.randomUUID();
        String sessionId = "session-" + UUID.randomUUID();
        mapper.insertConversation(
                taskId, identity.getTenantId(), projectId, sessionId, title);
        return get(identity.getTenantId(), projectId, taskId);
    }

    public java.util.List<ConversationDO> listByProject(
            String tenantId, String projectId) {
        return mapper.listByProject(tenantId, projectId);
    }

    public boolean delete(String tenantId, String projectId, String taskId) {
        return mapper.delete(tenantId, projectId, taskId) > 0;
    }

    public ConversationDO get(String tenantId, String projectId, String taskId) {
        List<ConversationDO> rows = mapper.get(tenantId, projectId, taskId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 该会话下全部专家会话映射（expert_id → session_id），供 AgentCore 侧清理。 */
    public List<Map<String, Object>> listExpertSessions(
            String tenantId, String projectId, String taskId) {
        return mapper.listExpertSessions(tenantId, projectId, taskId);
    }

    /**
     * 级联删除会话及其全部关联记录。顺序与外键依赖一致：
     * 血缘 → 产物 → 人类请求 → 子任务 → 计划 → run → 分析 → 执行票 →
     * 事件 → 消息 → 序列 → 专家会话映射 → CLI 载荷 → 提示词审计 → 会话。
     */
    @Transactional
    public void deleteConversationCascade(
            String tenantId, String projectId, String taskId) {
        mapper.deleteArtifactLineageForConversation(taskId);
        mapper.deleteArtifactsForConversation(tenantId, taskId);
        mapper.deleteHumanRequestsForConversation(tenantId, projectId, taskId);
        mapper.deleteTasksForConversation(tenantId, taskId);
        mapper.deletePlansForConversation(tenantId, taskId);
        mapper.deleteAgentRunsForConversation(tenantId, taskId);
        mapper.deleteAnalysesForConversation(projectId, taskId);
        mapper.deleteDispatchesForConversation(tenantId, taskId);
        mapper.deleteEventsForConversation(tenantId, taskId);
        mapper.deleteMessagesForConversation(tenantId, taskId);
        mapper.deleteEventSequenceForConversation(tenantId, taskId);
        mapper.deleteExpertSessionsForConversation(tenantId, taskId);
        mapper.deleteCliSubmissionsForConversation(taskId);
        mapper.deletePromptExecutionsForConversation(tenantId, taskId);
        mapper.delete(tenantId, projectId, taskId);
    }
}
