package org.cmb.teamcoordinator.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cmb.teamcoordinator.agentcore.AgentCoreAdapter;
import org.cmb.teamcoordinator.agentcore.AgentRunEvent;
import org.cmb.teamcoordinator.agentcore.AgentRunRequest;
import org.cmb.teamcoordinator.agentcore.AgentRunResponse;
import org.cmb.teamcoordinator.coordinator.EventVisibility;
import org.cmb.teamcoordinator.coordinator.MessageEventRepository;
import org.cmb.teamcoordinator.coordinator.ProjectEvent;
import org.cmb.teamcoordinator.coordinator.ProjectEventStreamHub;
import org.cmb.teamcoordinator.coordinator.ProjectEventType;
import org.cmb.teamcoordinator.common.ApiException;
import org.cmb.teamcoordinator.intent.CoordinatorDecision;
import org.cmb.teamcoordinator.intent.DecisionType;
import org.cmb.teamcoordinator.intent.IntentAnalysisRequest;
import org.cmb.teamcoordinator.intent.IntentAnalysisService;
import org.cmb.teamcoordinator.intent.TaskIntent;
import org.cmb.teamcoordinator.human.HumanRequestRepository;
import org.cmb.teamcoordinator.artifact.ArtifactRepository;
import org.cmb.teamcoordinator.artifact.ArtifactService;
import org.cmb.teamcoordinator.planning.ExpertSelector;
import org.cmb.teamcoordinator.planning.PlanningResult;
import org.cmb.teamcoordinator.planning.PlanningService;
import org.cmb.teamcoordinator.project.ProjectService;
import org.cmb.teamcoordinator.project.ProjectView;
import org.cmb.teamcoordinator.project.RequestIdentity;
import org.cmb.teamcoordinator.prompt.PromptService;
import org.cmb.teamcoordinator.prompt.RenderedPrompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SingleExpertWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(SingleExpertWorker.class);
    private final String instanceId = "coordinator-" + UUID.randomUUID();
    private final ExecutionRepository executionRepository;
    private final IntentAnalysisService analysisService;
    private final AgentCoreAdapter agentCore;
    private final ProjectService projectService;
    private final PlanningService planningService;
    private final ExpertSelector expertSelector;
    private final MessageEventRepository eventRepository;
    private final ProjectEventStreamHub streamHub;
    private final ObjectMapper objectMapper;
    private final HumanRequestRepository humanRequests;
    private final ArtifactRepository artifactRepository;
    private final ArtifactService artifactService;
    private final PromptService prompts;

    public SingleExpertWorker(
            ExecutionRepository executionRepository,
            IntentAnalysisService analysisService,
            AgentCoreAdapter agentCore,
            ProjectService projectService,
            PlanningService planningService,
            ExpertSelector expertSelector,
            MessageEventRepository eventRepository,
            ProjectEventStreamHub streamHub,
            ObjectMapper objectMapper,
            HumanRequestRepository humanRequests,
            ArtifactRepository artifactRepository,
            ArtifactService artifactService,
            PromptService prompts) {
        this.executionRepository = executionRepository;
        this.analysisService = analysisService;
        this.agentCore = agentCore;
        this.projectService = projectService;
        this.planningService = planningService;
        this.expertSelector = expertSelector;
        this.eventRepository = eventRepository;
        this.streamHub = streamHub;
        this.objectMapper = objectMapper;
        this.humanRequests = humanRequests;
        this.artifactRepository = artifactRepository;
        this.artifactService = artifactService;
        this.prompts = prompts;
    }

    @Scheduled(fixedDelayString = "${digital-team.execution.worker-interval-ms:500}")
    public void runOnce() {
        DispatchWork work = executionRepository.claimNext(instanceId, 30);
        if (work == null) {
            return;
        }
        try {
            process(work);
        } catch (RuntimeException ex) {
            LOGGER.warn("Single expert dispatch {} failed.", work.getDispatchId(), ex);
            executionRepository.completeDispatch(
                    work.getDispatchId(), "FAILED", abbreviate(ex.getMessage()));
            emit(work, ProjectEventType.TASK_FAILED, "Coordinator could not execute the task.");
        }
    }

    public TaskRecord cancel(RequestIdentity identity, String projectId, String taskId) {
        projectService.get(identity, projectId);
        TaskRecord task = executionRepository.findTask(
                identity.getTenantId(), projectId, taskId);
        if (task == null) {
            throw ApiException.notFound("TASK_NOT_FOUND", "Task was not found.");
        }
        if (isTerminal(task.getStatus())) {
            return task;
        }
        DispatchWork work = executionRepository.loadWorkForTask(
                identity.getTenantId(), projectId, taskId);
        AgentRunResponse stopped = agentCore.stopSession(task.getSessionId());
        if (stopped == null) {
            throw ApiException.conflict("TASK_CANCEL_FAILED", "AgentCore run was not found.");
        }
        AgentRunEvent event = new AgentRunEvent(
                task.getSessionId(), task.getLastSequence() + 1,
                "RUN_CANCELLED", "CANCELLED", "AgentCore session stopped.");
        event.setEventId(task.getSessionId() + ":cancel:" + event.getSequence());
        if (executionRepository.recordEvent(identity.getTenantId(), taskId, event)) {
            applyEvent(work, task, event);
        }
        return executionRepository.findTask(identity.getTenantId(), projectId, taskId);
    }

    private void process(DispatchWork work) {
        List<TaskRecord> tasks = executionRepository.findTasksForMessage(work);
        if (!tasks.isEmpty()) {
            advancePlan(work, tasks);
            return;
        }

        RequestIdentity identity = new RequestIdentity(work.getTenantId(), work.getUserId());
        IntentAnalysisRequest request = new IntentAnalysisRequest();
        request.setText(work.getText());
        request.setAttachmentRefs(work.getAttachmentRefs());
        CoordinatorDecision decision = analysisService.analyzeForDispatch(
                identity, work.getProjectId(), work.getConversationId(),
                work.getMessageId(), work.getBusinessSessionId(), request);
        if (decision == null) {
            executionRepository.releaseDispatch(work.getDispatchId());
            return;
        }
        if (decision.getDecisionType() == DecisionType.ANSWER) {
            emit(work, ProjectEventType.FINAL_RESPONSE, decision.getAnswer());
            executionRepository.completeDispatch(work.getDispatchId(), "COMPLETED", null);
            return;
        }
        if (decision.getDecisionType() == DecisionType.ASK_HUMAN) {
            humanRequests.linkDispatch(
                    decision.getHumanRequestId(), work.getMessageId(), work.getDispatchId());
            emitHumanRequest(
                    work, decision.getHumanRequestId(), "CLARIFICATION",
                    decision.getQuestion());
            executionRepository.completeDispatch(work.getDispatchId(), "WAITING_HUMAN", null);
            return;
        }

        TaskIntent intent = decision.getTaskIntent();
        ProjectView project = projectService.get(identity, work.getProjectId());
        PlanningResult planning = planningService.createPlan(intent, project, 1);
        executionRepository.createPlan(work, decision, planning);
        emit(work, ProjectEventType.PLAN_CREATED,
                "Plan V1 created with " + planning.getPlan().getTasks().size() + " tasks.");
        advancePlan(work, executionRepository.findTasksForMessage(work));
    }

    private void advancePlan(DispatchWork work, List<TaskRecord> tasks) {
        for (TaskRecord task : tasks) {
            if (task.getSessionId() != null && !isTerminal(task.getStatus())) {
                consumeEvents(work, task);
            }
        }
        tasks = executionRepository.findTasksForMessage(work);
        if (allSucceeded(tasks)) {
            TaskRecord last = tasks.get(tasks.size() - 1);
            String resultText = resultText(last);
            emit(work, ProjectEventType.FINAL_RESPONSE, resultText);
            executionRepository.completePlanAndDispatch(
                    last.getPlanId(), work.getDispatchId(), "COMPLETED", null);
            return;
        }
        if (hasFailed(tasks)) {
            TaskRecord failed = firstFailed(tasks);
            executionRepository.completePlanAndDispatch(
                    failed.getPlanId(), work.getDispatchId(), "FAILED",
                    "Expert task " + failed.getTaskKey() + " failed.");
            return;
        }
        Map<String, TaskRecord> byKey = index(tasks);
        RequestIdentity identity = new RequestIdentity(work.getTenantId(), work.getUserId());
        ProjectView project = projectService.get(identity, work.getProjectId());
        boolean started = false;
        for (TaskRecord task : tasks) {
            if ("PENDING".equals(task.getStatus()) && dependenciesSucceeded(task, byKey)) {
                String expertId = expertSelector.select(
                        project, task.getRequiredCapabilities());
                if (executionRepository.assignExpert(task.getId(), expertId)) {
                    startTask(work, task, expertId);
                    started = true;
                }
            }
        }
        if (started || !allSucceeded(tasks)) {
            executionRepository.releaseDispatch(work.getDispatchId());
        }
    }

    private void startTask(DispatchWork work, TaskRecord task, String expertId) {
        AgentRunRequest runRequest = new AgentRunRequest();
        List<String> inputRefs = new java.util.ArrayList<>();
        for (String reference : work.getAttachmentRefs()) {
            inputRefs.add(artifactRepository.resolveStorageKey(
                    work.getTenantId(), work.getProjectId(), reference));
        }
        inputRefs.addAll(artifactRepository.findAvailableStorageKeys(
                task.getPlanId(), task.getDependencies()));
        RequestIdentity identity = new RequestIdentity(work.getTenantId(), work.getUserId());
        ProjectView project = projectService.get(identity, work.getProjectId());
        Map<String, Object> promptContext = new HashMap<>();
        promptContext.put("projectName", project.getName());
        promptContext.put("projectDescription", project.getDescription());
        promptContext.put("overallRequest", work.getText());
        promptContext.put("taskKey", task.getTaskKey());
        promptContext.put("objective", task.getObjective());
        promptContext.put("expectedOutput", task.getExpectedOutput());
        promptContext.put("acceptanceCriteria", task.getAcceptanceCriteria());
        promptContext.put("dependencies", task.getDependencies());
        promptContext.put("requiredCapabilities", task.getRequiredCapabilities());
        promptContext.put("inputArtifactRefs", inputRefs);
        promptContext.put("businessSessionId", work.getBusinessSessionId());
        RenderedPrompt prompt = prompts.render(
                PromptService.EXPERT_EXECUTION, promptContext, work.getTenantId(),
                work.getProjectId(), work.getConversationId(), task.getRequestId(), expertId);
        runRequest.setSystemPrompt(prompt.getContent());
        runRequest.setTaskText(task.getObjective());
        Map<String, Object> structuredInput = new HashMap<>(promptContext);
        structuredInput.put("promptVersion", prompt.getVersion());
        structuredInput.put("promptTemplateId", prompt.getTemplateId());
        runRequest.setStructuredInput(structuredInput);
        runRequest.setAttachments(artifactService.toAgentAttachments(inputRefs));
        AgentRunResponse response = agentCore.submitRun(expertId, runRequest);
        executionRepository.saveSession(task.getId(), response.getSessionId());
        emit(work, ProjectEventType.TASK_STARTED, "Expert " + expertId + " accepted the task.");
    }

    private void consumeEvents(DispatchWork work, TaskRecord task) {
        List<AgentRunEvent> events = new ArrayList<>(
                agentCore.streamEvents(
                        task.getSessionId(), task.getLastSequence(),
                        work.getBusinessSessionId()));
        if (events.isEmpty() && agentCore.getRunStatus(
                task.getSessionId(), work.getBusinessSessionId()) == null) {
            AgentRunEvent lost = new AgentRunEvent(
                    task.getSessionId(),
                    task.getLastSequence() + 1,
                    "RUN_FAILED",
                    "FAILED",
                    "Expert run no longer exists in AgentCore.");
            lost.setEventId(task.getSessionId() + ":lost");
            events.add(lost);
        }
        events.sort(Comparator.comparingLong(AgentRunEvent::getSequence));
        for (AgentRunEvent event : events) {
            if (event.getEventId() == null) {
                event.setEventId(event.getSessionId() + ":" + event.getSequence());
            }
            if (!executionRepository.recordEvent(work.getTenantId(), task.getId(), event)) {
                continue;
            }
            applyEvent(work, task, event);
            task.setLastSequence(Math.max(task.getLastSequence(), event.getSequence()));
        }
        if (!isTerminal(task.getStatus())) {
            executionRepository.releaseDispatch(work.getDispatchId());
        }
    }

    private void applyEvent(DispatchWork work, TaskRecord task, AgentRunEvent event) {
        String type = event.getType();
        if ("RUN_ACCEPTED".equals(type) || "RUN_PROGRESS".equals(type)) {
            boolean advanced = executionRepository.advanceTask(
                    task.getId(), event.getSequence(), "RUNNING", null);
            if (advanced && "RUN_PROGRESS".equals(type)) {
                task.setStatus("RUNNING");
                emit(work, ProjectEventType.TASK_PROGRESS_UPDATED, event.getMessage());
            }
            return;
        }
        if ("RUN_WAITING_HUMAN".equals(type)) {
            if (executionRepository.advanceTask(
                    task.getId(), event.getSequence(), "WAITING_HUMAN",
                    write(event.getPayload()))) {
                task.setStatus("WAITING_HUMAN");
                String question = String.valueOf(event.getPayload().get("question"));
                String agentQuestionId =
                        String.valueOf(event.getPayload().get("questionId"));
                String requestId = humanRequests.createExpertClarification(
                        work.getTenantId(), work.getProjectId(), task.getId(),
                        agentQuestionId, question);
                emitHumanRequest(work, requestId, "CLARIFICATION", question);
            }
            return;
        }
        if ("RUN_SUCCEEDED".equals(type)) {
            Object resultValue = event.getPayload().get("resultText");
            String resultText = resultValue == null ? null : String.valueOf(resultValue);
            if (resultText == null || resultText.trim().isEmpty()) {
                failResult(work, task, event, "Expert result did not contain resultText.");
                return;
            }
            List<String> artifactIds = registerArtifacts(work, task, event);
            if (executionRepository.advanceTask(
                    task.getId(), event.getSequence(), "SUCCEEDED", write(event.getPayload()))) {
                task.setStatus("SUCCEEDED");
                emit(work, ProjectEventType.TASK_SUCCEEDED, "Expert task completed.");
                if (!artifactIds.isEmpty()) {
                    emit(work, ProjectEventType.ARTIFACT_CREATED, write(artifactIds));
                }
                if (task.getCorrectionOf() != null) {
                    executionRepository.acceptCorrection(task, write(event.getPayload()));
                }
            }
            return;
        }
        String status = "RUN_TIMED_OUT".equals(type) ? "TIMED_OUT"
                : "RUN_CANCELLED".equals(type) ? "CANCELLED" : "FAILED";
        if (executionRepository.advanceTask(
                task.getId(), event.getSequence(), status, write(event.getPayload()))) {
            task.setStatus(status);
            emit(work, ProjectEventType.TASK_FAILED, event.getMessage());
            executionRepository.completePlanAndDispatch(
                    task.getPlanId(), work.getDispatchId(), status, event.getMessage());
        }
    }

    private void failResult(
            DispatchWork work, TaskRecord task, AgentRunEvent event, String message) {
        TaskRecord correction = executionRepository.createCorrection(work, task);
        if (correction != null) {
            task.setStatus("CORRECTING");
            emit(work, ProjectEventType.TASK_FAILED,
                    message + " A correction task was scheduled.");
            return;
        }
        executionRepository.advanceTask(
                task.getId(), event.getSequence(), "FAILED", write(event.getPayload()));
        task.setStatus("FAILED");
        emit(work, ProjectEventType.TASK_FAILED, message);
        executionRepository.completePlanAndDispatch(
                task.getPlanId(), work.getDispatchId(), "FAILED", message);
    }

    private Map<String, TaskRecord> index(List<TaskRecord> tasks) {
        Map<String, TaskRecord> result = new HashMap<>();
        for (TaskRecord task : tasks) {
            result.put(task.getTaskKey(), task);
        }
        return result;
    }

    private boolean dependenciesSucceeded(
            TaskRecord task, Map<String, TaskRecord> tasks) {
        for (String key : task.getDependencies()) {
            TaskRecord dependency = tasks.get(key);
            if (dependency == null || !"SUCCEEDED".equals(dependency.getStatus())) {
                return false;
            }
        }
        return true;
    }

    private boolean allSucceeded(List<TaskRecord> tasks) {
        if (tasks.isEmpty()) {
            return false;
        }
        for (TaskRecord task : tasks) {
            if (!"SUCCEEDED".equals(task.getStatus())) {
                return false;
            }
        }
        return true;
    }

    private boolean hasFailed(List<TaskRecord> tasks) {
        return firstFailed(tasks) != null;
    }

    private TaskRecord firstFailed(List<TaskRecord> tasks) {
        for (TaskRecord task : tasks) {
            if ("FAILED".equals(task.getStatus())
                    || "CANCELLED".equals(task.getStatus())
                    || "TIMED_OUT".equals(task.getStatus())) {
                return task;
            }
        }
        return null;
    }

    private String resultText(TaskRecord task) {
        try {
            return objectMapper.readTree(task.getResultJson())
                    .path("resultText").asText("Expert tasks completed.");
        } catch (Exception ex) {
            return "Expert tasks completed.";
        }
    }

    private List<String> registerArtifacts(
            DispatchWork work, TaskRecord task, AgentRunEvent event) {
        Object registered = event.getPayload().get("artifactIds");
        if (registered instanceof List) {
            return artifactService.acceptAgentArtifacts(
                    work, task, (List<?>) registered);
        }
        Object value = event.getPayload().get("artifactFileIds");
        if (!(value instanceof List)) {
            return java.util.Collections.emptyList();
        }
        List<String> result = new java.util.ArrayList<>();
        for (Object storageKey : (List<?>) value) {
            result.add(artifactService.registerExpertArtifact(
                    work, task, String.valueOf(storageKey)));
        }
        return result;
    }

    private void emit(DispatchWork work, ProjectEventType type, String text) {
        RequestIdentity identity = new RequestIdentity(work.getTenantId(), work.getUserId());
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("messageId", work.getMessageId());
        payload.put("text", text);
        ProjectEvent event = eventRepository.insertEvent(
                identity,
                work.getProjectId(),
                work.getConversationId(),
                work.getMessageId(),
                type,
                EventVisibility.PUBLIC,
                payload);
        streamHub.publish(
                work.getTenantId(), work.getProjectId(),
                work.getConversationId(), event);
    }

    private void emitHumanRequest(
            DispatchWork work, String requestId, String requestType, String question) {
        RequestIdentity identity = new RequestIdentity(work.getTenantId(), work.getUserId());
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("messageId", work.getMessageId());
        payload.put("humanRequestId", requestId);
        payload.put("requestType", requestType);
        payload.put("text", question);
        ProjectEvent event = eventRepository.insertEvent(
                identity,
                work.getProjectId(),
                work.getConversationId(),
                work.getMessageId(),
                ProjectEventType.TASK_WAITING_HUMAN,
                EventVisibility.PUBLIC,
                payload);
        streamHub.publish(
                work.getTenantId(), work.getProjectId(),
                work.getConversationId(), event);
    }

    private boolean isTerminal(String status) {
        return "SUCCEEDED".equals(status)
                || "FAILED".equals(status)
                || "CANCELLED".equals(status)
                || "TIMED_OUT".equals(status);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize expert result.", ex);
        }
    }

    private String abbreviate(String value) {
        if (value == null || value.length() <= 1024) {
            return value;
        }
        return value.substring(0, 1024);
    }
}
