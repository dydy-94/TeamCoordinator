package org.cmb.infrastructure.persistent;

import java.util.List;
import java.util.UUID;
import org.cmb.infrastructure.persistent.mapper.ConversationTaskMapper;
import org.cmb.teamcoordinator.coordinator.ConversationTaskView;
import org.cmb.teamcoordinator.project.RequestIdentity;
import org.springframework.stereotype.Repository;

/**
 * Conversation-task persistence facade. All SQL lives in
 * {@link ConversationTaskMapper}.
 */
@Repository
public class ConversationTaskRepository {

    private final ConversationTaskMapper mapper;

    public ConversationTaskRepository(ConversationTaskMapper mapper) {
        this.mapper = mapper;
    }

    public ConversationTaskView create(
            RequestIdentity identity, String projectId, String title) {
        String taskId = "task-" + UUID.randomUUID();
        String sessionId = "session-" + UUID.randomUUID();
        mapper.insertConversation(
                taskId, identity.getTenantId(), projectId, sessionId, title);
        return get(identity.getTenantId(), projectId, taskId);
    }

    public java.util.List<ConversationTaskView> listByProject(
            String tenantId, String projectId) {
        return mapper.listByProject(tenantId, projectId);
    }

    public boolean delete(String tenantId, String projectId, String taskId) {
        return mapper.delete(tenantId, projectId, taskId) > 0;
    }

    public ConversationTaskView get(String tenantId, String projectId, String taskId) {
        List<ConversationTaskView> rows = mapper.get(tenantId, projectId, taskId);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
