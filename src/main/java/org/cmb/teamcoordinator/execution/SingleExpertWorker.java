package org.cmb.teamcoordinator.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.annotation.PreDestroy;
import org.cmb.teamcoordinator.agentcore.AgentCoreAdapter;
import org.cmb.teamcoordinator.agentcore.AgentEvent;
import org.cmb.teamcoordinator.agentcore.AgentRunRequest;
import org.cmb.teamcoordinator.agentcore.AgentRunResponse;
import org.cmb.teamcoordinator.config.DigitalTeamProperties;
import org.cmb.teamcoordinator.coordinator.EventVisibility;
import org.cmb.infrastructure.persistent.MessageEventRepository;
import org.cmb.infrastructure.persistent.ExecutionRepository;
import org.cmb.infrastructure.persistent.HumanRequestRepository;
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
import org.cmb.infrastructure.persistent.ArtifactRepository;
import org.cmb.teamcoordinator.artifact.ArtifactService;
import org.cmb.teamcoordinator.planning.ExpertSelector;
import org.cmb.teamcoordinator.planning.PlanningResult;
import org.cmb.teamcoordinator.planning.PlanningService;
import org.cmb.teamcoordinator.project.ProjectService;
import org.cmb.teamcoordinator.project.ProjectView;
import org.cmb.teamcoordinator.project.RequestIdentity;
import org.cmb.teamcoordinator.project.Skill;
import org.cmb.infrastructure.persistent.SkillRepository;
import org.cmb.teamcoordinator.prompt.PromptService;
import org.cmb.teamcoordinator.prompt.RenderedPrompt;
import org.cmb.teamcoordinator.semantic.SemanticCheckClient;
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

    /** Dispatch lease length; renewed in the background while process() runs. */
    private static final int DISPATCH_LEASE_SECONDS = 30;

    /**
     * Age after which a STARTING task with no session is considered stranded
     * (its starting process died) and reset to PENDING. Well above the
     * seconds-long legitimate window between assignExpert and saveSession,
     * and above the lease expiry that gates cross-instance recovery.
     */
    private static final int STARTING_RECOVERY_AGE_SECONDS = 60;

    /**
     * Background lease renewal for the dispatch currently being processed.
     * A dedicated daemon thread is used because the Spring task scheduler
     * is blocked by the synchronous process() call.
     */
    private final ScheduledExecutorService leaseKeeper = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "dispatch-lease-keeper");
                    thread.setDaemon(true);
                    return thread;
                }
            });

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
    private final SkillRepository skillRepository;
    private final SemanticCheckClient semanticChecks;
    private final DigitalTeamProperties properties;

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
            PromptService prompts,
            SkillRepository skillRepository,
            SemanticCheckClient semanticChecks,
            DigitalTeamProperties properties) {
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
        this.skillRepository = skillRepository;
        this.semanticChecks = semanticChecks;
        this.properties = properties;
    }

    /**
     * 领取任务， 回调用process()
     */
    @Scheduled(fixedDelayString = "${digital-team.execution.worker-interval-ms:500}")
    public void runOnce() {
        DispatchWork work = executionRepository.claimNext(instanceId, DISPATCH_LEASE_SECONDS);
        if (work == null) {
            return;
        }
        // process() can block for minutes (coordinator run, plan generation
        // with repairs). Without renewal the lease would expire and another
        // instance would claim the same dispatch concurrently.
        ScheduledFuture<?> leaseRenewal = leaseKeeper.scheduleAtFixedRate(
                () -> executionRepository.renewLease(
                        work.getDispatchId(), instanceId, DISPATCH_LEASE_SECONDS),
                DISPATCH_LEASE_SECONDS / 3,
                DISPATCH_LEASE_SECONDS / 3,
                TimeUnit.SECONDS);
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
        } finally {
            leaseRenewal.cancel(false);
        }
    }

    @PreDestroy
    public void shutdown() {
        leaseKeeper.shutdownNow();
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
        publishAgentEventLive(work, event);
        // Use the status-only transition instead of applyEvent: the
        // sequence-guarded advanceTask could reject the synthetic cancel
        // event when a real event with the same sequence was consumed
        // concurrently, leaving the task RUNNING against a dead session.
        if (!executionRepository.cancelTask(
                task.getId(), "CANCELLED", writeMap(event))) {
            TaskRecord current = executionRepository.findTask(
                    identity.getTenantId(), projectId, taskId);
            if (isTerminal(current.getStatus())) {
                return current;
            }
            throw ApiException.conflict("TASK_CANCEL_FAILED",
                    "Task state changed concurrently; cancel was not applied.");
        }
        if (work != null) {
            executionRepository.completePlanAndDispatch(
                    task.getPlanId(), work.getDispatchId(), "CANCELLED", event.getContent());
        }
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
        String planAgentId = decision.getEffectiveAgentId() != null
                ? decision.getEffectiveAgentId()
                : CoordinatorAgentClient.COORDINATOR_AGENT_ID;
        // Forward planning agent events to SSE (live-only, MARKER for replay)
        Consumer<AgentEvent> planEventSink = event -> {
            event.setAgentId(planAgentId);
            publishAgentEventLive(work, event);
        };
        PlanningResult planning = planningService.createPlan(
                intent, project, 1, planAgentId, planEventSink);
        // Insert AGENT_RUN_MARKER for the planning AgentCore call
        if (planning.getSessionId() != null) {
            ObjectNode planMarkerPayload = objectMapper.createObjectNode();
            planMarkerPayload.put("sessionId", planning.getSessionId());
            planMarkerPayload.put("expertId", planAgentId);
            eventRepository.insertEvent(
                    identity, work.getProjectId(), work.getConversationId(),
                    work.getMessageId(), ProjectEventType.AGENT_RUN_MARKER,
                    EventVisibility.PUBLIC, planMarkerPayload);
        }
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
        // 恢复卡死的 STARTING 任务：submitRun 与 saveSession 之间进程崩溃时，
        // 任务会永久停在 STARTING + 无 session，这里重置回 PENDING 重新调度。
        int recovered = executionRepository.recoverStaleStartingTasks(
                work.getTenantId(),
                work.getMessageId(),
                Timestamp.from(Instant.now().minusSeconds(STARTING_RECOVERY_AGE_SECONDS)));
        if (recovered > 0) {
            LOGGER.warn("Recovered {} stranded STARTING task(s) for message {}.",
                    recovered, work.getMessageId());
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
                // No candidate right now (experts busy or unavailable) is a
                // retryable condition — skip this round instead of failing
                // the whole message.
                String expertId = expertSelector.select(
                        project, task.getRequiredCapabilities());
                if (expertId == null) {
                    LOGGER.warn("No expert available for task {} (capabilities {}); "
                                    + "will retry on the next poll.",
                            task.getTaskKey(), task.getRequiredCapabilities());
                    continue;
                }
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
        promptContext.put("projectId", work.getProjectId());
        promptContext.put("taskId", task.getId());
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
        // Attach project skills to the expert run request
        List<String> skillNames = new java.util.ArrayList<>();
        for (Skill skill : skillRepository.findByProject(
                work.getTenantId(), work.getProjectId())) {
            if (skill.isEnabled()) {
                skillNames.add(skill.getId());
            }
        }
        if (!skillNames.isEmpty()) {
            runRequest.setSkillNames(skillNames);
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
        List<AgentEvent> events;
        try {
            events = new ArrayList<>(
                    agentCore.streamEvents(
                            task.getExpertId(), task.getSessionId(),
                            task.getLastSequence(), work.getBusinessSessionId()));
        } catch (RuntimeException ex) {
            // Transient AgentCore outage (5xx, connection failure): tolerate
            // it up to the threshold instead of failing the whole message.
            handleAgentCoreFailure(work, task,
                    "AgentCore stream failed: " + abbreviate(ex.getMessage()));
            return;
        }
        if (events.isEmpty()) {
            AgentEvent status;
            try {
                status = agentCore.getRunStatus(
                        task.getExpertId(), task.getSessionId(),
                        work.getBusinessSessionId());
            } catch (RuntimeException ex) {
                handleAgentCoreFailure(work, task,
                        "AgentCore status failed: " + abbreviate(ex.getMessage()));
                return;
            }
            // Empty stream AND no run record. This can be a genuinely lost
            // run, but also a just-submitted run not yet visible — require
            // consecutive observations before failing the task.
            if (status == null) {
                handleAgentCoreFailure(work, task,
                        "Expert run no longer exists in AgentCore.");
                return;
            }
        }
        executionRepository.resetConsecutiveFailures(task.getId());
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
     * Record one AgentCore failure tick. Below the threshold the task is
     * left untouched and the dispatch released so the next poll retries;
     * at the threshold a synthetic coordinatorError fails the task.
     */
    private void handleAgentCoreFailure(
            DispatchWork work, TaskRecord task, String message) {
        int failures = executionRepository.incrementConsecutiveFailures(task.getId());
        LOGGER.warn("AgentCore failure {} for task {} (session {}): {}",
                failures, task.getTaskKey(), task.getSessionId(), message);
        if (failures < properties.getExecution().getAgentcoreFailureThreshold()) {
            executionRepository.releaseDispatch(work.getDispatchId());
            return;
        }
        AgentEvent lost = AgentEvent.of("coordinatorError");
        lost.setSessionId(task.getSessionId());
        lost.setSequence(task.getLastSequence() + 1);
        lost.setEventId(task.getSessionId() + ":lost");
        lost.setContent(message);
        lost.setAgentId(task.getExpertId());
        lost.setTimestamp(System.currentTimeMillis());
        applyEvent(work, task, lost);
        task.setLastSequence(Math.max(task.getLastSequence(), lost.getSequence()));
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
            // Second-pass semantic review: judge whether resultText actually
            // satisfies the objective and acceptance criteria before success.
            SemanticCheckClient.SemanticCheckResult review =
                    reviewExpertResult(work, task, resultText);
            if (review.isConclusive() && !review.isConsistent()) {
                failResult(work, task, event,
                        "Expert result failed semantic review: " + review.getReason());
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

    /**
     * Ask the Coordinator to judge whether the expert result actually
     * satisfies the subtask. Best-effort: an inconclusive review passes the
     * result through — only an explicit rejection fails it.
     */
    private SemanticCheckClient.SemanticCheckResult reviewExpertResult(
            DispatchWork work, TaskRecord task, String resultText) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("overallRequest", work.getText());
        context.put("taskKey", task.getTaskKey());
        context.put("objective", task.getObjective());
        context.put("expectedOutput", task.getExpectedOutput());
        context.put("acceptanceCriteria", task.getAcceptanceCriteria());
        context.put("resultText", resultText);
        String judgeAgent = judgeAgentId(work);
        RenderedPrompt prompt = prompts.render(
                PromptService.EXPERT_RESULT_CHECK, context,
                work.getTenantId(), work.getProjectId(), work.getConversationId(),
                task.getRequestId() + ":review", judgeAgent);
        return semanticChecks.check(prompt.getContent(), judgeAgent, null);
    }

    /** Resolve the review judge: project coordinator override > global default. */
    private String judgeAgentId(DispatchWork work) {
        RequestIdentity identity =
                new RequestIdentity(work.getTenantId(), work.getUserId());
        ProjectView project = projectService.get(identity, work.getProjectId());
        String override = project.getCoordinatorAgentId();
        return (override != null && !override.trim().isEmpty())
                ? override : properties.getAgentCore().getCoordinatorAgentId();
    }

    private void failResult(
            DispatchWork work, TaskRecord task, AgentEvent event, String message) {
        TaskRecord correction = executionRepository.createCorrection(work, task, message);
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
