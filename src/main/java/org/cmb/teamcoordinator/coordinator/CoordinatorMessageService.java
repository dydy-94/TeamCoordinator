package org.cmb.teamcoordinator.coordinator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.cmb.teamcoordinator.project.ProjectService;
import org.cmb.teamcoordinator.project.RequestIdentity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CoordinatorMessageService {

    private final ProjectService projectService;
    private final MessageEventRepository repository;
    private final ConversationTaskService tasks;
    private final ProjectEventStreamHub streamHub;
    private final ObjectMapper objectMapper;

    public CoordinatorMessageService(
            ProjectService projectService,
            MessageEventRepository repository,
            ConversationTaskService tasks,
            ProjectEventStreamHub streamHub,
            ObjectMapper objectMapper) {
        this.projectService = projectService;
        this.repository = repository;
        this.tasks = tasks;
        this.streamHub = streamHub;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MessageAcceptedResponse accept(
            RequestIdentity identity, String projectId, String taskId,
            MessageRequest request) {
        projectService.requireTaskInitiator(identity, projectId);
        ConversationTaskView task = tasks.require(identity, projectId, taskId);
        MessageAcceptedResponse duplicate = repository.findDuplicate(
                identity, projectId, taskId, request);
        if (duplicate != null) {
            return duplicate;
        }

        String messageId = "message-" + UUID.randomUUID();
        try {
            repository.insertMessage(identity, projectId, taskId, messageId, request);
        } catch (DuplicateKeyException ex) {
            return repository.findDuplicate(identity, projectId, taskId, request);
        }

        ObjectNode internalPayload = objectMapper.createObjectNode();
        internalPayload.put("messageId", messageId);
        repository.insertEvent(
                identity,
                projectId,
                taskId,
                messageId,
                ProjectEventType.MESSAGE_ACCEPTED_INTERNAL,
                EventVisibility.INTERNAL,
                internalPayload);

        ObjectNode publicPayload = objectMapper.createObjectNode();
        publicPayload.put("messageId", messageId);
        publicPayload.put("text", "Coordinator is analyzing the request.");
        ProjectEvent publicEvent = repository.insertEvent(
                identity,
                projectId,
                taskId,
                messageId,
                ProjectEventType.COORDINATOR_ANALYZING,
                EventVisibility.PUBLIC,
                publicPayload);
        repository.insertDispatch(identity, projectId, taskId, messageId);
        publishAfterCommit(identity, projectId, taskId, publicEvent);
        return new MessageAcceptedResponse(
                messageId, taskId, task.getSessionId(), "ACCEPTED");
    }

    @Transactional(readOnly = true)
    public void requireEventAccess(
            RequestIdentity identity, String projectId, String taskId) {
        tasks.require(identity, projectId, taskId);
    }

    @Transactional(readOnly = true)
    public List<ProjectEvent> replayAuthorized(
            RequestIdentity identity, String projectId, String taskId,
            long afterSequence) {
        tasks.require(identity, projectId, taskId);
        return repository.findPublicEvents(
                identity.getTenantId(), projectId, taskId, afterSequence);
    }

    private void publishAfterCommit(
            RequestIdentity identity, String projectId, String taskId,
            ProjectEvent publicEvent) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                streamHub.publish(identity.getTenantId(), projectId, taskId, publicEvent);
            }
        });
    }
}
