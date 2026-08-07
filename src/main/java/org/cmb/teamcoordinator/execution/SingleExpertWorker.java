package org.cmb.teamcoordinator.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.cmb.teamcoordinator.agentcore.AgentCoreAdapter;
import org.cmb.teamcoordinator.agentcore.AgentEvent;
import org.cmb.teamcoordinator.agentcore.AgentRunRequest;
import org.cmb.teamcoordinator.agentcore.AgentRunResponse;
import org.cmb.teamcoordinator.coordinator.EventVisibility;
import org.cmb.teamcoordinator.coordinator.MessageEventRepository;
import org.cmb.teamcoordinator.coordinator.ProjectEvent;
import org.cmb.teamcoordinator.coordinator.ProjectEventStreamHub;
import org.cmb.teamcoordinator.coordinator.ProjectEventType;
import org.cmb.teamcoordinator.common.ApiException;
import org.cmb.teamcoordinator.intent.CoordinatorAgentClient;
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

/**
 * 系统的执行引擎，通过500ms 定时轮询驱动所有任务，
 * SingleExpertWorker
 */
@Component
public class SingleExpertWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(SingleExpertWorker.class);

    /** AgentCore event types that indicate progress but don't change task terminal state. */
    private static final Set<String> PROGRESS_EVENT_TYPES = new HashSet<>(Arrays.asList(
            "liveStatus", "taskInQueue",
            "textDelta", "streamStart", "streamEnd",
            "thinkingStart", "thinkingDelta", "thinking", "thinkingEnd",
            "toolUsed", "toolResult",
            "planUpdate", "newPlanStep",
            "subagentThinking", "subagentChat", "subagentToolUsed", "subagentToolResult",
            "file", "directory", "streamingFile",
            "sidebarDisplay", "weblink",
            "clearBoundary", "compactBoundary", "reconnect"
    ));

    // ── Coordinator phase constants ────────────────────────────────────
    private static final String PHASE_PLANNING    = "planning";
    private static final String PHASE_DISPATCHING = "dispatching";
    private static final String PHASE_ANSWERING   = "answering";
    private static final String PHASE_WAITING     = "waiting_human";
    private static final String PHASE_COMPLETED   = "completed";
    private static final String PHASE_FAILED      = "failed";

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

    /**
     * 领取任务， 回调用process()
     */
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
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            // Fail all tasks associated with this dispatch so they don't
            // permanently consume expert concurrency slots.
            executionRepository.failTasksForMessage(
                    work.getTenantId(), work.getMessageId());
            executionRepository.completeDispatch(
                    work.getDispatchId(), "FAILED", abbreviate(msg));
            emitAgentEvent(work,
                    AgentEvent.content("coordinatorError", "Execution failed: " + abbreviate(msg),
                            "coordinator"));
        }
    }

    /**
     * 调用agentcore的stopSession停止掉AgentCore run，合成RUN_CANCELLED事件，并应用到任务记录。
     * @param identity
     * @param projectId
     * @param taskId
     * @return
     */
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
        AgentRunResponse stopped = agentCore.stopSession(
                task.getExpertId(), task.getSessionId());
        if (stopped == null) {
            throw ApiException.conflict("TASK_CANCEL_FAILED",
                    "AgentCore run was not found.");
        }
        AgentEvent event = AgentEvent.of("coordinatorRunCancelled");
        event.setSessionId(task.getSessionId());
        event.setSequence(task.getLastSequence() + 1);
        event.setEventId(task.getSessionId() + ":cancel:" + event.getSequence());
        event.setContent("AgentCore session stopped.");
        event.setTimestamp(System.currentTimeMillis());
        applyEvent(work, task, event);
        return executionRepository.findTask(identity.getTenantId(), projectId, taskId);
    }

    // ── Dispatch processing ─────────────────────────────────────────────
    /**
     * 新消息处理 + 决策分支
     * @param work
     */
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

        // Build event sink that forwards coordinator agent events to the task SSE
        // without persisting them — AgentCore events should be re-fetched on replay,
        // not stored in project_event. Only Coordinator-generated lifecycle events
        // (coordinatorPhase, coordinatorChat, etc.) are persisted.
        Consumer<AgentEvent> coordinatorEventSink = event -> {
            event.setAgentId(CoordinatorAgentClient.COORDINATOR_AGENT_ID);
            publishAgentEventLive(work, event);
        };
        // Insert AGENT_RUN_MARKER before the AgentCore call when we already
        // know the session ID (reuse from a previous message). This lets
        // cross-instance SSE subscribers discover and replay via AgentCore
        // while the current call is still streaming.
        String existingCoordSession = work.getCoordinatorSessionId();
        if (existingCoordSession != null) {
            String storedAgentId = executionRepository.loadCoordinatorAgent(
                    work.getConversationId());
            if (storedAgentId.isEmpty()) {
                storedAgentId = CoordinatorAgentClient.COORDINATOR_AGENT_ID;
            }
            insertCoordinatorMarker(identity, work, existingCoordSession, storedAgentId);
        }

        // 意图分析（传入 task 级 coordinator session 以保持跨消息上下文连续）
        CoordinatorDecision decision = analysisService.analyzeForDispatch(
                identity, work.getProjectId(), work.getConversationId(),
                work.getMessageId(), work.getBusinessSessionId(),
                existingCoordSession,
                request, coordinatorEventSink);
        if (decision == null) {
            executionRepository.releaseDispatch(work.getDispatchId());
            return;
        }
        // Persist coordinator session + insert MARKER for first-time use
        if (decision.getCoordinatorSessionId() != null) {
            String effectiveAgent = decision.getEffectiveAgentId() != null
                    ? decision.getEffectiveAgentId()
                    : CoordinatorAgentClient.COORDINATOR_AGENT_ID;
            executionRepository.saveCoordinatorSession(
                    work.getConversationId(), decision.getCoordinatorSessionId(),
                    effectiveAgent);
            if (existingCoordSession == null) {
                insertCoordinatorMarker(identity, work,
                        decision.getCoordinatorSessionId(), effectiveAgent);
            }
        }
        if (decision.getDecisionType() == DecisionType.ANSWER) {
            emitPhase(work, PHASE_ANSWERING, "Coordinator is preparing a direct answer.");
            publishAgentEvent(work,
                    AgentEvent.content("coordinatorChat", decision.getAnswer(),
                            CoordinatorAgentClient.COORDINATOR_AGENT_ID));
            emitPhase(work, PHASE_COMPLETED, "Request completed.");
            executionRepository.completeDispatch(work.getDispatchId(), "COMPLETED", null);
            return;
        }
        if (decision.getDecisionType() == DecisionType.ASK_HUMAN) {
            emitPhase(work, PHASE_WAITING, "Coordinator needs clarification from the user.");
            humanRequests.linkDispatch(
                    decision.getHumanRequestId(), work.getMessageId(), work.getDispatchId());
            AgentEvent confirmEvent = AgentEvent.of("coordinatorConfirm");
            confirmEvent.setAgentId(CoordinatorAgentClient.COORDINATOR_AGENT_ID);
            confirmEvent.setQuestionId(decision.getHumanRequestId());
            confirmEvent.setContent(decision.getQuestion());
            List<AgentEvent.Question> questions = new ArrayList<>();
            AgentEvent.Question q = new AgentEvent.Question();
            q.setQuestion(decision.getQuestion());
            q.setHeader("补充信息");
            q.setMultiSelect(false);
            questions.add(q);
            confirmEvent.setQuestions(questions);
            publishAgentEvent(work, confirmEvent);
            executionRepository.completeDispatch(work.getDispatchId(), "WAITING_HUMAN", null);
            return;
        }

        TaskIntent intent = decision.getTaskIntent();
        ProjectView project = projectService.get(identity, work.getProjectId());
        emitPhase(work, PHASE_PLANNING, "Coordinator is creating an execution plan.");
        PlanningResult planning = planningService.createPlan(intent, project, 1);
        executionRepository.createPlan(work, decision, planning);

        // Emit plan update
        AgentEvent planEvent = AgentEvent.of("coordinatorPlanUpdate");
        planEvent.setAgentId(CoordinatorAgentClient.COORDINATOR_AGENT_ID);
        List<AgentEvent.PlanTaskStatus> planTasks = new ArrayList<>();
        for (org.cmb.teamcoordinator.planning.PlannedTask pt
                : planning.getPlan().getTasks()) {
            AgentEvent.PlanTaskStatus pts = new AgentEvent.PlanTaskStatus();
            pts.setStatus("todo");
            pts.setTitle(pt.getObjective());
            pts.setStartedAt(0L);
            planTasks.add(pts);
        }
        planEvent.setTasks(planTasks);
        publishAgentEvent(work, planEvent);

        emitPhase(work, PHASE_DISPATCHING, "Dispatching tasks to experts.");
        advancePlan(work, executionRepository.findTasksForMessage(work));
    }

    // ── Plan advancement ────────────────────────────────────────────────
    /**
     * 核心方法，计划推进引擎。根据任务状态推进计划，包括处理成功、失败以及继续推进未完成的任务。
     * @param work 当前的调度工作
     * @param tasks 当前调度工作下的任务列表
     */
    private void advancePlan(DispatchWork work, List<TaskRecord> tasks) {
        // 消费各 RUNNING任务的AgentCore事件
        for (TaskRecord task : tasks) {
            if (task.getSessionId() != null && !isTerminal(task.getStatus())) {
                consumeEvents(work, task);
            }
        }
        // 重新获取任务列表，检查是否所有任务都已完成或失败
        tasks = executionRepository.findTasksForMessage(work);
        if (allSucceeded(tasks)) {
            TaskRecord last = tasks.get(tasks.size() - 1);
            String resultText = resultText(last);
            emitPhase(work, PHASE_ANSWERING, "All expert tasks completed, preparing final response.");
            publishAgentEvent(work,
                    AgentEvent.content("coordinatorChat", resultText,
                            CoordinatorAgentClient.COORDINATOR_AGENT_ID));
            emitPhase(work, PHASE_COMPLETED, "Request completed.");
            executionRepository.completePlanAndDispatch(
                    last.getPlanId(), work.getDispatchId(), "COMPLETED", null);
            return;
        }
        if (hasFailed(tasks)) {
            TaskRecord failed = firstFailed(tasks);
            emitPhase(work, PHASE_FAILED,
                    "Expert task " + failed.getTaskKey() + " failed.");
            executionRepository.completePlanAndDispatch(
                    failed.getPlanId(), work.getDispatchId(), "FAILED",
                    "Expert task " + failed.getTaskKey() + " failed.");
            return;
        }
        Map<String, TaskRecord> byKey = index(tasks);
        RequestIdentity identity = new RequestIdentity(work.getTenantId(), work.getUserId());
        ProjectView project = projectService.get(identity, work.getProjectId());
        // 遍历任务列表，启动所有满足条件的PENDING任务
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
        // 如果有任务被启动，或者并非所有任务都已成功，则释放调度锁
        if (started || !allSucceeded(tasks)) {
            executionRepository.releaseDispatch(work.getDispatchId());
        }
    }

    // ── Task start ──────────────────────────────────────────────────────
    /**
     * 启动专家任务，调用AgentCore执行，并将任务状态更新为RUNNING。
     * @param work
     * @param task
     * @param expertId
     */
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
        // Reuse existing expert session from a previous message for context continuity
        // (explicitly exclude current message to prevent parallel tasks sharing sessions)
        String existingExpertSession = executionRepository.findExpertSession(
                work.getTenantId(), work.getProjectId(),
                work.getConversationId(), expertId, work.getMessageId());
        if (existingExpertSession != null) {
            runRequest.setConversationSessionId(existingExpertSession);
        }
        AgentRunResponse response = agentCore.submitRun(expertId, runRequest);
        executionRepository.saveSession(task.getId(), response.getSessionId());

        // Insert AGENT_RUN_MARKER for replay: when a reconnecting client sees
        // this marker, it fetches the agent's events from AgentCore in real time.
        ObjectNode markerPayload = objectMapper.createObjectNode();
        markerPayload.put("sessionId", response.getSessionId());
        markerPayload.put("expertId", expertId);
        eventRepository.insertEvent(
                identity, work.getProjectId(), work.getConversationId(),
                work.getMessageId(), ProjectEventType.AGENT_RUN_MARKER,
                EventVisibility.PUBLIC, markerPayload);

        // Emit coordinatorNewPlanStep for task start
        AgentEvent stepEvent = AgentEvent.of("coordinatorNewPlanStep");
        stepEvent.setAgentId(expertId);
        stepEvent.setContent("Expert " + expertId + " accepted the task.");
        stepEvent.setTimestamp(System.currentTimeMillis());
        publishAgentEvent(work, stepEvent);
    }

    // ── Event consumption ───────────────────────────────────────────────
    /**
     * 消费AgentCore事件
     * @param work
     * @param task
     */
    private void consumeEvents(DispatchWork work, TaskRecord task) {
        List<AgentEvent> events = new ArrayList<>(
                agentCore.streamEvents(
                        task.getExpertId(), task.getSessionId(),
                        task.getLastSequence(), work.getBusinessSessionId()));
        if (events.isEmpty() && agentCore.getRunStatus(
                task.getExpertId(), task.getSessionId(),
                work.getBusinessSessionId()) == null) {
            AgentEvent lost = AgentEvent.of("coordinatorError");
            lost.setSessionId(task.getSessionId());
            lost.setSequence(task.getLastSequence() + 1);
            lost.setEventId(task.getSessionId() + ":lost");
            lost.setContent("Expert run no longer exists in AgentCore.");
            lost.setAgentId(task.getExpertId());
            lost.setTimestamp(System.currentTimeMillis());
            events.add(lost);
        }
        events.sort(Comparator.comparingLong(AgentEvent::getSequence));
        for (AgentEvent event : events) {
            // Ensure event has agentId set
            if (event.getAgentId() == null) {
                event.setAgentId(task.getExpertId());
            }
            if (event.getEventId() == null) {
                event.setEventId(event.getSessionId() + ":" + event.getSequence());
            }
            // Dedup by cursor: AgentCore streamEvents(afterSequence) already
            // filters out events with sequence <= lastSequence. No DB storage
            // needed — AgentCore persists its own event history.
            applyEvent(work, task, event);
            task.setLastSequence(Math.max(task.getLastSequence(), event.getSequence()));
        }
        if (!isTerminal(task.getStatus())) {
            executionRepository.releaseDispatch(work.getDispatchId());
        }
    }

    /**
     * Apply a single agent event to the task's state machine while
     * transparently forwarding it to the task SSE stream.
     *
     * @param work The current dispatch work context
     * @param task The task record to update
     * @param event The agent event to apply
     */
    private void applyEvent(DispatchWork work, TaskRecord task, AgentEvent event) {
        // Forward agent events live-only — no DB persistence.
        // Replay is handled by AGENT_RUN_MARKER → AgentCore re-fetch.
        publishAgentEventLive(work, event);

        String type = event.getType();

        // State machine: progress events keep task RUNNING
        if (PROGRESS_EVENT_TYPES.contains(type)) {
            executionRepository.advanceTask(
                    task.getId(), event.getSequence(), "RUNNING", null);
            return;
        }

        // Human-in-the-loop (AgentCore confirm)
        if ("confirm".equals(type)) {
            if (executionRepository.advanceTask(
                    task.getId(), event.getSequence(), "WAITING_HUMAN",
                    writeMap(event))) {
                task.setStatus("WAITING_HUMAN");
                String question = event.getContent() != null
                        ? event.getContent() : "Agent requires input.";
                String questionId = event.getQuestionId() != null
                        ? event.getQuestionId() : "agent-question-" + task.getId();
                humanRequests.createExpertClarification(
                        work.getTenantId(), work.getProjectId(), task.getId(),
                        questionId, question);
            }
            return;
        }

        // Success: chat or end
        if ("chat".equals(type) || "end".equals(type)) {
            // Skip if already terminal or if a correction is in progress
            // (failResult already created a correction task and end should
            // not override the CORRECTING status back to SUCCEEDED).
            if (isTerminal(task.getStatus())
                    || "CORRECTING".equals(task.getStatus())) {
                return;
            }
            String resultText = event.getContent();
            if (resultText == null || resultText.trim().isEmpty()) {
                failResult(work, task, event, "Expert result did not contain content.");
                return;
            }
            List<String> artifactIds = registerArtifacts(work, task, event);
            if (executionRepository.advanceTask(
                    task.getId(), event.getSequence(), "SUCCEEDED",
                    writeMap(event))) {
                task.setStatus("SUCCEEDED");
                // Persist expert session so future messages in this task
                // can reuse it for context continuity.
                executionRepository.saveExpertSession(
                        work.getTenantId(), work.getProjectId(),
                        work.getConversationId(),
                        task.getExpertId(), task.getSessionId(),
                        work.getMessageId());
                if (task.getCorrectionOf() != null) {
                    executionRepository.acceptCorrection(task, writeMap(event));
                }
            }
            return;
        }

        // Failure (AgentCore error or Coordinator synthetic error)
        if ("error".equals(type) || "coordinatorError".equals(type)) {
            String status = event.getStatus() != null ? event.getStatus() : "FAILED";
            if ("TIMED_OUT".equals(status) || "CANCELLED".equals(status) || "FAILED".equals(status)) {
                // already set by the agent
            } else {
                status = "FAILED";
            }
            if (executionRepository.advanceTask(
                    task.getId(), event.getSequence(), status, writeMap(event))) {
                task.setStatus(status);
                executionRepository.completePlanAndDispatch(
                        task.getPlanId(), work.getDispatchId(), status, event.getContent());
            }
            return;
        }

        // Synthetic: coordinatorRunCancelled (from cancel() or stopSession)
        if ("coordinatorRunCancelled".equals(type)) {
            if (executionRepository.advanceTask(
                    task.getId(), event.getSequence(), "CANCELLED", writeMap(event))) {
                task.setStatus("CANCELLED");
                executionRepository.completePlanAndDispatch(
                        task.getPlanId(), work.getDispatchId(),
                        "CANCELLED", event.getContent());
            }
            return;
        }

        // Unknown: log and mark running
        executionRepository.advanceTask(
                task.getId(), event.getSequence(), "RUNNING", null);
    }

    // ── Artifact registration ───────────────────────────────────────────

    private void failResult(
            DispatchWork work, TaskRecord task, AgentEvent event, String message) {
        TaskRecord correction = executionRepository.createCorrection(work, task);
        if (correction != null) {
            task.setStatus("CORRECTING");
            AgentEvent correctionEvent = AgentEvent.of("coordinatorNewPlanStep");
            correctionEvent.setAgentId(task.getExpertId());
            correctionEvent.setContent(message + " A correction task was scheduled.");
            publishAgentEvent(work, correctionEvent);
            return;
        }
        executionRepository.advanceTask(
                task.getId(), event.getSequence(), "FAILED", writeMap(event));
        task.setStatus("FAILED");
        AgentEvent failEvent = AgentEvent.of("coordinatorError");
        failEvent.setAgentId(task.getExpertId());
        failEvent.setContent(message);
        publishAgentEvent(work, failEvent);
        executionRepository.completePlanAndDispatch(
                task.getPlanId(), work.getDispatchId(), "FAILED", message);
    }

    private List<String> registerArtifacts(
            DispatchWork work, TaskRecord task, AgentEvent event) {
        // Extract artifact file IDs from attachments in chat/end events
        if (event.getAttachments() != null) {
            List<String> result = new ArrayList<>();
            for (AgentEvent.AttachmentInfo att : event.getAttachments()) {
                if (att.getPath() != null) {
                    result.add(artifactService.registerExpertArtifact(
                            work, task, att.getPath()));
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        return Collections.emptyList();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

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
                    .path("content").asText("Expert tasks completed.");
        } catch (Exception ex) {
            return "Expert tasks completed.";
        }
    }

    private void insertCoordinatorMarker(
            RequestIdentity identity, DispatchWork work,
            String sessionId, String effectiveAgentId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("sessionId", sessionId);
        payload.put("expertId", effectiveAgentId);
        eventRepository.insertEvent(
                identity, work.getProjectId(), work.getConversationId(),
                work.getMessageId(), ProjectEventType.AGENT_RUN_MARKER,
                EventVisibility.PUBLIC, payload);
    }

    /** Emit a coordinator phase transition event. */
    private void emitPhase(DispatchWork work, String phase, String detail) {
        AgentEvent event = AgentEvent.of("coordinatorPhase");
        event.setAgentId(CoordinatorAgentClient.COORDINATOR_AGENT_ID);
        event.setContent(detail);
        event.setStatus(phase);
        publishAgentEvent(work, event);
    }

    // Monotonic counter for live-only events (not persisted, no sequence allocation)
    private long liveEventCounter;

    /**
     * Push an AgentEvent to live SSE subscribers — no DB persistence.
     * Agent events are re-fetched from AgentCore during replay via AGENT_RUN_MARKER.
     */
    private void publishAgentEventLive(DispatchWork work, AgentEvent event) {
        if (event.getTimestamp() == 0L) {
            event.setTimestamp(System.currentTimeMillis());
        }
        ProjectEvent projectEvent = new ProjectEvent();
        projectEvent.setProjectId(work.getProjectId());
        projectEvent.setConversationId(work.getConversationId());
        projectEvent.setMessageId(work.getMessageId());
        projectEvent.setSequence(++liveEventCounter);
        projectEvent.setType(ProjectEventType.COORDINATOR_ANALYZING);
        projectEvent.setAgentEvent(event);
        streamHub.publish(
                work.getTenantId(), work.getProjectId(),
                work.getConversationId(), projectEvent);
    }

    /**
     * Publish an AgentEvent to the task SSE stream AND persist in
     * project_event for replay. Used for Coordinator-generated events.
     */
    private void publishAgentEvent(DispatchWork work, AgentEvent event) {
        if (event.getTimestamp() == 0L) {
            event.setTimestamp(System.currentTimeMillis());
        }
        RequestIdentity identity = new RequestIdentity(
                work.getTenantId(), work.getUserId());
        ProjectEvent projectEvent = eventRepository.insertEvent(
                identity,
                work.getProjectId(),
                work.getConversationId(),
                work.getMessageId(),
                ProjectEventType.COORDINATOR_ANALYZING,
                EventVisibility.PUBLIC,
                objectMapper.convertValue(event, ObjectNode.class));
        projectEvent.setAgentEvent(event);
        streamHub.publish(
                work.getTenantId(), work.getProjectId(),
                work.getConversationId(), projectEvent);
    }

    private void emitAgentEvent(DispatchWork work, AgentEvent event) {
        publishAgentEvent(work, event);
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
            throw new IllegalStateException(
                    "Could not serialize expert result.", ex);
        }
    }

    private String writeMap(AgentEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String abbreviate(String value) {
        if (value == null || value.length() <= 1024) {
            return value;
        }
        return value.substring(0, 1024);
    }
}
