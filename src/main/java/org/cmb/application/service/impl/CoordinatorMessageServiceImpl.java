package org.cmb.application.service.impl;

import org.cmb.application.service.ConversationTaskService;
import org.cmb.application.service.CoordinatorMessageService;
import org.cmb.infrastructure.worker.ProjectEventStreamHub;
import org.cmb.common.enums.ProjectEventType;
import org.cmb.common.enums.EventVisibility;
import org.cmb.application.domain.entity.MessageDO;
import org.cmb.application.dto.MessageRequest;
import org.cmb.application.domain.entity.ConversationDO;
import org.cmb.application.domain.entity.ProjectEventDO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.cmb.infrastructure.persistent.MessageEventRepository;
import org.cmb.application.domain.AgentEvent;
import org.cmb.application.component.CoordinatorAgentClient;
import org.cmb.application.service.ProjectService;
import org.cmb.application.domain.RequestIdentity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CoordinatorMessageServiceImpl implements CoordinatorMessageService {

    private final ProjectService projectService;
    private final MessageEventRepository repository;
    private final ConversationTaskService tasks;
    private final ProjectEventStreamHub streamHub;
    private final ObjectMapper objectMapper;

    public CoordinatorMessageServiceImpl(
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
    public MessageDO accept(
            RequestIdentity identity, String projectId, String taskId,
            MessageRequest request) {
        projectService.requireTaskInitiator(identity, projectId);
        ConversationDO task = tasks.require(identity, projectId, taskId);
        MessageDO duplicate = repository.findDuplicate(
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

        // Emit userMessage so all SSE subscribers see who sent what,
        // in the same AgentEvent wire format as all other events.
        AgentEvent userMessage = AgentEvent.of("userMessage");
        userMessage.setAgentId(identity.getUserId());
        userMessage.setContent(request.getText());
        userMessage.setSessionId(messageId);
        ObjectNode userPayload = objectMapper.convertValue(userMessage, ObjectNode.class);
        ProjectEventDO userEvent = repository.insertEvent(
                identity,
                projectId,
                taskId,
                messageId,
                ProjectEventType.COORDINATOR_ANALYZING,
                EventVisibility.PUBLIC,
                userPayload);
        userEvent.setAgentEvent(userMessage);
        publishAfterCommit(identity, projectId, taskId, userEvent);

        AgentEvent analyzing = AgentEvent.of("coordinatorPhase");
        analyzing.setAgentId(CoordinatorAgentClient.COORDINATOR_AGENT_ID);
        analyzing.setStatus("analyzing");
        analyzing.setContent("Coordinator is analyzing the request.");
        analyzing.setTimestamp(System.currentTimeMillis());
        ObjectNode publicPayload = objectMapper.convertValue(analyzing, ObjectNode.class);
        ProjectEventDO publicEvent = repository.insertEvent(
                identity,
                projectId,
                taskId,
                messageId,
                ProjectEventType.COORDINATOR_ANALYZING,
                EventVisibility.PUBLIC,
                publicPayload);
        publicEvent.setAgentEvent(analyzing);
        repository.insertDispatch(identity, projectId, taskId, messageId);
        publishAfterCommit(identity, projectId, taskId, publicEvent);
        return new MessageDO(
                messageId, taskId, task.getSessionId(), "ACCEPTED");
    }

    @Transactional(readOnly = true)
    public void requireEventAccess(
            RequestIdentity identity, String projectId, String taskId) {
        tasks.require(identity, projectId, taskId);
    }

    @Transactional(readOnly = true)
    public List<ProjectEventDO> replayAuthorized(
            RequestIdentity identity, String projectId, String taskId,
            long afterSequence) {
        tasks.require(identity, projectId, taskId);
        return repository.findPublicEvents(
                identity.getTenantId(), projectId, taskId, afterSequence);
    }

    private void publishAfterCommit(
            RequestIdentity identity, String projectId, String taskId,
            ProjectEventDO publicEvent) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                streamHub.publish(identity.getTenantId(), projectId, taskId, publicEvent);
            }
        });
    }
}
