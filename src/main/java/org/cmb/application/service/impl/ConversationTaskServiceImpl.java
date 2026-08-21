package org.cmb.application.service.impl;

import org.cmb.application.service.ConversationTaskService;
import org.cmb.application.service.ProjectService;
import org.cmb.application.dto.CreateConversationTaskRequest;
import org.cmb.application.domain.entity.ConversationDO;

import java.util.List;
import java.util.Map;
import org.cmb.application.service.AgentCoreAdapter;
import org.cmb.infrastructure.persistent.ConversationTaskRepository;
import org.cmb.common.exception.ApiException;
import org.cmb.application.domain.RequestIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ConversationTaskServiceImpl implements ConversationTaskService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ConversationTaskServiceImpl.class);

    private final ProjectService projects;
    private final ConversationTaskRepository tasks;
    private final AgentCoreAdapter agentCore;

    public ConversationTaskServiceImpl(
            ProjectService projects,
            ConversationTaskRepository tasks,
            AgentCoreAdapter agentCore) {
        this.projects = projects;
        this.tasks = tasks;
        this.agentCore = agentCore;
    }

    public ConversationDO create(
            RequestIdentity identity, String projectId,
            CreateConversationTaskRequest request) {
        projects.requireTaskInitiator(identity, projectId);
        return tasks.create(identity, projectId, request.getTitle());
    }

    public List<ConversationDO> list(
            RequestIdentity identity, String projectId) {
        projects.get(identity, projectId); // allow VIEWER to list
        return tasks.listByProject(identity.getTenantId(), projectId);
    }

    /**
     * 删除会话：先级联清理本库中该会话的全部关联记录（消息、事件、计划、
     * 子任务、产物、人类请求、序列、CLI 载荷、提示词审计等），再尽力删除
     * AgentCore 侧对应会话（协调器 + 各专家 session）。
     */
    public void delete(
            RequestIdentity identity, String projectId, String taskId) {
        projects.requireTaskInitiator(identity, projectId);
        ConversationDO task = tasks.get(identity.getTenantId(), projectId, taskId);
        if (task == null) {
            throw ApiException.notFound("TASK_NOT_FOUND", "Conversation task was not found.");
        }

        // 会话号必须在级联删除前收集，之后相关行已被清理
        List<Map<String, Object>> expertSessions =
                tasks.listExpertSessions(identity.getTenantId(), projectId, taskId);

        tasks.deleteConversationCascade(identity.getTenantId(), projectId, taskId);

        if (task.getCoordinatorSessionId() != null) {
            deleteAgentSession(
                    task.getCoordinatorAgentId(), task.getCoordinatorSessionId(), taskId);
        }
        for (Map<String, Object> expert : expertSessions) {
            deleteAgentSession(
                    String.valueOf(expert.get("expert_id")),
                    String.valueOf(expert.get("session_id")),
                    taskId);
        }
    }

    /** AgentCore 侧会话删除：尽力而为，失败只记录（本库已清理完毕）。 */
    private void deleteAgentSession(String agentId, String sessionId, String taskId) {
        if (agentId == null || sessionId == null || "null".equals(sessionId)) {
            return;
        }
        try {
            agentCore.deleteSession(agentId, sessionId);
        } catch (RuntimeException ex) {
            LOGGER.warn("Could not delete AgentCore session {} (agent {}) "
                    + "for conversation {}: {}",
                    sessionId, agentId, taskId, ex.getMessage());
        }
    }

    public ConversationDO require(
            RequestIdentity identity, String projectId, String taskId) {
        projects.get(identity, projectId);
        ConversationDO task = tasks.get(identity.getTenantId(), projectId, taskId);
        if (task == null) {
            throw ApiException.notFound("TASK_NOT_FOUND", "Conversation task was not found.");
        }
        return task;
    }
}
