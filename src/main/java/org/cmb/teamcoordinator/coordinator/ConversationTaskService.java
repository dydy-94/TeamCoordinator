package org.cmb.teamcoordinator.coordinator;

import java.util.List;
import org.cmb.infrastructure.persistent.ConversationTaskRepository;
import org.cmb.teamcoordinator.common.ApiException;
import org.cmb.teamcoordinator.project.ProjectService;
import org.cmb.teamcoordinator.project.RequestIdentity;
import org.springframework.stereotype.Service;

@Service
public class ConversationTaskService {

    private final ProjectService projects;
    private final ConversationTaskRepository tasks;

    public ConversationTaskService(
            ProjectService projects, ConversationTaskRepository tasks) {
        this.projects = projects;
        this.tasks = tasks;
    }

    public ConversationTaskView create(
            RequestIdentity identity, String projectId,
            CreateConversationTaskRequest request) {
        projects.requireTaskInitiator(identity, projectId);
        return tasks.create(identity, projectId, request.getTitle());
    }

    public List<ConversationTaskView> list(
            RequestIdentity identity, String projectId) {
        projects.get(identity, projectId); // allow VIEWER to list
        return tasks.listByProject(identity.getTenantId(), projectId);
    }

    public void delete(
            RequestIdentity identity, String projectId, String taskId) {
        projects.requireTaskInitiator(identity, projectId);
        if (!tasks.delete(identity.getTenantId(), projectId, taskId)) {
            throw ApiException.notFound("TASK_NOT_FOUND", "Conversation task was not found.");
        }
    }

    public ConversationTaskView require(
            RequestIdentity identity, String projectId, String taskId) {
        projects.get(identity, projectId);
        ConversationTaskView task = tasks.get(identity.getTenantId(), projectId, taskId);
        if (task == null) {
            throw ApiException.notFound("TASK_NOT_FOUND", "Conversation task was not found.");
        }
        return task;
    }
}
