package org.cmb.infrastructure.remoteaccess;
import org.cmb.application.domain.Skill;
import org.cmb.application.domain.AgentCoreTools;
import org.cmb.application.domain.AgentRunAttachment;
import org.cmb.application.domain.AgentRunResponse;
import org.cmb.application.domain.AgentRunRequest;
import org.cmb.application.domain.AgentEvent;
import org.cmb.application.domain.AgentCoreAdapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.cmb.application.domain.TaskRecord;
import org.cmb.application.domain.FileStore;
import org.cmb.infrastructure.persistent.ExecutionRepository;
import org.cmb.application.service.CliSubmissionService;
import org.cmb.application.domain.TaskIntent;
import org.cmb.application.domain.CoordinatorPlanSpec;
import org.cmb.application.domain.PlannedTask;
import org.cmb.application.domain.CoordinatorDecision;
import org.cmb.common.enums.DecisionType;
import org.cmb.common.enums.ExecutionMode;
import org.cmb.application.domain.MockFileDescriptor;
import org.cmb.infrastructure.remoteaccess.MockFileStore;
import org.cmb.common.config.DigitalTeamProperties;
import org.cmb.application.domain.IntentAnalysisContext;
import org.cmb.infrastructure.remoteaccess.MockIntentModelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "digital-team.agent-core", name = "mock-enabled",
        havingValue = "true", matchIfMissing = true)
public class MockAgentCoreAdapter implements AgentCoreAdapter {

    private final Map<String, List<AgentEvent>> eventsBySessionId =
            new ConcurrentHashMap<>();
    private final FileStore fileStore;
    private final ObjectMapper objectMapper;
    private final MockIntentModelClient coordinatorAgent;
    private final String coordinatorAgentId;
    private final ExecutionRepository executionRepository;
    private final CliSubmissionService cliSubmissions;
    private final Map<String, String> expertTaskBySession = new ConcurrentHashMap<>();
    /**
     * Deferred CLI actions, executed on the next streamEvents call — i.e.
     * after the worker persisted the run session, mirroring the real
     * agent's timing (the CLI is called while the run is already RUNNING).
     */
    private final Map<String, Runnable> pendingActionsBySession =
            new ConcurrentHashMap<>();

    @Autowired
    public MockAgentCoreAdapter(
            DigitalTeamProperties properties, FileStore fileStore,
            ObjectMapper objectMapper, MockIntentModelClient coordinatorAgent,
            ExecutionRepository executionRepository,
            CliSubmissionService cliSubmissions) {
        this.fileStore = fileStore;
        this.objectMapper = objectMapper;
        this.coordinatorAgent = coordinatorAgent;
        this.coordinatorAgentId = properties.getAgentCore().getCoordinatorAgentId();
        this.executionRepository = executionRepository;
        this.cliSubmissions = cliSubmissions;
    }

    /** Constructor for tests that don't need full Spring context. */
    public MockAgentCoreAdapter(DigitalTeamProperties properties) {
        this.fileStore = new MockFileStore();
        this.objectMapper = new ObjectMapper();
        this.coordinatorAgent = new MockIntentModelClient(objectMapper);
        this.coordinatorAgentId = properties.getAgentCore().getCoordinatorAgentId();
        this.executionRepository = null;
        this.cliSubmissions = null;
    }

    @Override
    public AgentRunResponse submitRun(String targetAgentId, AgentRunRequest request) {
        // Reuse conversation sessionId when continuing an existing conversation
        String sessionId = request.getConversationSessionId() != null
                ? request.getConversationSessionId()
                : "mock-run-" + UUID.randomUUID();
        List<AgentEvent> events = new ArrayList<>();
        long seq = 0;
        long now = System.currentTimeMillis();

        Object objective = request.getStructuredInput() == null
                ? null : request.getStructuredInput().get("objective");
        String effectiveTask = objective == null
                ? request.getTaskText() : String.valueOf(objective);
        // taskId-only dispatch: pull the task contract from the Coordinator,
        // mirroring the real agent's tc get-task flow.
        if (executionRepository != null
                && request.getStructuredInput() != null
                && request.getStructuredInput().get("taskId") != null
                && objective == null) {
            TaskRecord pulled = executionRepository.findTaskByBusinessId(
                    String.valueOf(request.getStructuredInput().get("taskId")));
            if (pulled != null) {
                effectiveTask = String.valueOf(pulled.getObjective());
                if (pulled.getAcceptanceCriteria() != null) {
                    effectiveTask += " " + pulled.getAcceptanceCriteria();
                }
            }
        }
        String normalizedTask = effectiveTask == null ? "" : effectiveTask.toLowerCase();

        // Coordinator agent: use MockIntentModelClient to generate a decision
        if (coordinatorAgentId.equals(targetAgentId)) {
            events.addAll(coordinatorEvents(request, sessionId, seq, now));
            appendOrStore(sessionId, events);
            return new AgentRunResponse(sessionId, "ACCEPTED");
        }

        // ── Expert agent: realistic event sequence ─────────────────

        // 1. taskInQueue
        events.add(queueEvent(sessionId, ++seq, now));

        // 2. liveStatus sequence
        for (String status : new String[]{"初始化", "思考中", "请求模型", "模型思考中"}) {
            events.add(liveStatusEvent(sessionId, ++seq, status, now));
        }

        // 3. thinking phase
        events.add(thinkingStartEvent(sessionId, ++seq, now));
        events.add(thinkingDeltaEvent(sessionId, ++seq,
                "分析任务: " + effectiveTask, now));
        events.add(thinkingDeltaEvent(sessionId, ++seq,
                "制定解决方案...", now));
        events.add(thinkingEndEvent(sessionId, ++seq, now));

        // 4. streaming text phase
        events.add(streamStartEvent(sessionId, ++seq, now));
        String resultContent = "Task completed: " + effectiveTask;
        events.add(textDeltaEvent(sessionId, ++seq,
                resultContent, now));
        events.add(streamEndEvent(sessionId, ++seq, now));

        // 5. Scenario-specific terminal behavior, mirroring the real agent's
        // CLI interaction: results and human questions are submitted through
        // the submission service, not the event stream.
        String cliTaskId = request.getStructuredInput() == null
                ? null : String.valueOf(request.getStructuredInput().get("taskId"));
        if (cliTaskId != null && !"null".equals(cliTaskId)) {
            expertTaskBySession.put(sessionId, cliTaskId);
        }
        if (normalizedTask.contains("need-human")) {
            if (cliSubmissions != null && cliTaskId != null) {
                pendingActionsBySession.put(sessionId, () ->
                        cliSubmissions.askHuman(cliTaskId, "请补充任务所需信息"));
            }
            events.add(confirmEvent(sessionId, ++seq, now));
        } else if (normalizedTask.contains("timeout")) {
            AgentEvent timeoutErr = errorEvent(sessionId, ++seq,
                    "Mock expert reached deadline.", now);
            timeoutErr.setStatus("TIMED_OUT");
            events.add(timeoutErr);
        } else if (normalizedTask.contains("fail")) {
            AgentEvent failErr = errorEvent(sessionId, ++seq,
                    "Mock expert failed by request.", now);
            failErr.setStatus("FAILED");
            events.add(failErr);
        } else if (normalizedTask.contains("plan")) {
            events.add(planUpdateEvent(sessionId, ++seq, now));
            events.add(newPlanStepEvent(sessionId, ++seq,
                    "执行: " + effectiveTask, now));
            events.add(liveStatusEvent(sessionId, ++seq, "工具调用中", now));
            events.add(toolUsedEvent(sessionId, ++seq,
                    "Skill", "icode:cmb-ui-design",
                    "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24),
                    now));
            events.add(toolResultEvent(sessionId, ++seq,
                    "Skill", "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24),
                    "Tool execution completed.", now));
            // Always end with chat + end after plan events
            AgentEvent planChat = chatEvent(sessionId, ++seq, now);
            planChat.setContent(resultContent);
            AgentEvent.UsageInfo planUsage = new AgentEvent.UsageInfo();
            planUsage.setInputTokens(800);
            planUsage.setOutputTokens(300);
            planChat.setUsage(planUsage);
            events.add(planChat);
            AgentEvent planEnd = endEvent(sessionId, ++seq, now);
            planEnd.setContent(resultContent);
            planEnd.setUsage(planUsage);
            events.add(planEnd);
            if (cliSubmissions != null && cliTaskId != null) {
                pendingActionsBySession.put(sessionId, () ->
                        submitResultTolerant(cliTaskId, resultContent));
            }
        } else {
            // Normal success: chat + end
            events.add(liveStatusEvent(sessionId, ++seq, "模型响应中", now));
            AgentEvent chatEvent = chatEvent(sessionId, ++seq, now);
            if (!normalizedTask.contains("invalid-result")) {
                chatEvent.setContent(resultContent);
            }
            // Attach input file content if present
            List<String> attachmentContents = new ArrayList<>();
            for (AgentRunAttachment runAttachment : request.getAttachments()) {
                String attachmentRef = storageKey(runAttachment.getFileDownloadUrl());
                byte[] content = fileStore.getContent(attachmentRef);
                if (content != null) {
                    attachmentContents.add(new String(content, StandardCharsets.UTF_8));
                }
            }
            if (!attachmentContents.isEmpty()) {
                chatEvent.setContent(resultContent + "\nInput: " + attachmentContents.get(0));
            }
            // Create a mock output artifact
            MockFileDescriptor artifact = fileStore.reserve("result.txt", "text/plain");
            String artifactContent = resultContent
                    + (attachmentContents.isEmpty() ? ""
                            : "\nInput: " + attachmentContents.get(0));
            fileStore.put(artifact.getFileId(),
                    artifactContent.getBytes(StandardCharsets.UTF_8));
            List<AgentEvent.AttachmentInfo> atts = new ArrayList<>();
            AgentEvent.AttachmentInfo att = new AgentEvent.AttachmentInfo();
            att.setFileName("result.txt");
            att.setContentType("text/plain");
            att.setPathType("output");
            att.setPath(artifact.getFileId());
            atts.add(att);
            chatEvent.setAttachments(atts);
            chatEvent.setFileType("common");
            AgentEvent.UsageInfo usage = new AgentEvent.UsageInfo();
            usage.setInputTokens(500);
            usage.setOutputTokens(200);
            chatEvent.setUsage(usage);
            events.add(chatEvent);

            AgentEvent endEvent = endEvent(sessionId, ++seq, now);
            endEvent.setContent(resultContent);
            endEvent.setUsage(usage);
            endEvent.setAttachments(atts);
            endEvent.setFileType("common");
            events.add(endEvent);
            if (cliSubmissions != null && cliTaskId != null) {
                pendingActionsBySession.put(sessionId, () ->
                        submitResultTolerant(cliTaskId, resultContent));
            }
        }

        appendOrStore(sessionId, events);
        return new AgentRunResponse(sessionId, "ACCEPTED");
    }

    private void appendOrStore(String sessionId, List<AgentEvent> events) {
        List<AgentEvent> existing = eventsBySessionId.get(sessionId);
        if (existing != null) {
            long offset = existing.get(existing.size() - 1).getSequence();
            for (AgentEvent e : events) {
                e.setSequence(++offset);
                e.setEventId(sessionId + ":" + offset);
            }
            existing.addAll(events);
        } else {
            eventsBySessionId.put(sessionId, events);
        }
    }

    // ── Coordinator event generation ────────────────────────────────────

    private List<AgentEvent> coordinatorEvents(
            AgentRunRequest request, String sessionId, long seqBase, long now) {
        List<AgentEvent> events = new ArrayList<>();
        long seq = seqBase;

        events.add(liveStatusEvent(sessionId, ++seq, "初始化", now));
        events.add(liveStatusEvent(sessionId, ++seq, "思考中", now));
        events.add(thinkingStartEvent(sessionId, ++seq, now));
        events.add(thinkingDeltaEvent(sessionId, ++seq, "分析用户意图...", now));
        events.add(thinkingEndEvent(sessionId, ++seq, now));

        String decisionJson = null;
        String invalidContent = null;
        try {
            Object rawContext = request.getStructuredInput().get("context");
            IntentAnalysisContext context = objectMapper.convertValue(
                    rawContext, IntentAnalysisContext.class);
            String operation = String.valueOf(
                    request.getStructuredInput().get("operation"));
            if (context.getText().startsWith("__always_invalid__")) {
                invalidContent = "{still-invalid";
            } else if ("ANALYZE".equals(operation)
                    && context.getText().startsWith("__invalid_once__")) {
                invalidContent = "{invalid";
            } else {
                decisionJson = objectMapper.writeValueAsString(
                        coordinatorAgent.classify(context));
            }
        } catch (RuntimeException ex) {
            events.add(errorEvent(sessionId, ++seq,
                    "Coordinator parse error: " + ex.getMessage(), now));
            return events;
        } catch (Exception ex) {
            events.add(errorEvent(sessionId, ++seq,
                    "Coordinator error: " + ex.getMessage(), now));
            return events;
        }
        // The decision (and, for CREATE_PLAN, the plan) is submitted through
        // the submission service — the same endpoints the tc CLI hits.
        if (decisionJson != null && cliSubmissions != null) {
            Object rawContext = request.getStructuredInput().get("context");
            IntentAnalysisContext ctx = objectMapper.convertValue(
                    rawContext, IntentAnalysisContext.class);
            String conversationTaskId = ctx.getConversationTaskId();
            if (conversationTaskId != null) {
                cliSubmissions.submitDecision(conversationTaskId, decisionJson);
                try {
                    CoordinatorDecision decision = objectMapper.readValue(
                            decisionJson, CoordinatorDecision.class);
                    if (decision.getDecisionType() == DecisionType.CREATE_PLAN
                            && decision.getTaskIntent() != null) {
                        String planJson = objectMapper.writeValueAsString(
                                buildPlan(decision.getTaskIntent(), 1));
                        cliSubmissions.submitPlan(conversationTaskId, planJson);
                    }
                } catch (Exception ex) {
                    // The service validates payloads; a failure here is a
                    // mock-side bug surfaced as a failed run.
                    events.add(errorEvent(sessionId, ++seq,
                            "Mock plan submission failed: " + ex.getMessage(), now));
                    return events;
                }
            }
        }
        AgentEvent endEvent = endEvent(sessionId, ++seq, now);
        if (invalidContent != null) {
            endEvent.setContent(invalidContent);
        } else if (decisionJson != null) {
            endEvent.setContent(decisionJson);
        }
        events.add(endEvent);
        return events;
    }

    // ── Plan generation (former MockPlanModelClient) ────────────────────

    private CoordinatorPlanSpec buildPlan(TaskIntent intent, int planVersion) {
        CoordinatorPlanSpec plan = new CoordinatorPlanSpec();
        plan.setPlanVersion(planVersion);
        if (intent.getExecutionMode() == ExecutionMode.MULTI_EXPERT) {
            if (intent.getObjective().contains("并行")
                    || intent.getObjective().toLowerCase().contains("parallel")) {
                PlannedTask first = task(
                        "analyze-a", "Analyze aspect A: " + intent.getObjective(),
                        Collections.<String>emptyList(), "Analysis A",
                        "Analysis A is complete", Collections.singletonList("analysis"));
                PlannedTask second = task(
                        "analyze-b", "Analyze aspect B: " + intent.getObjective(),
                        Collections.<String>emptyList(), "Analysis B",
                        "Analysis B is complete", Collections.singletonList("analysis"));
                PlannedTask summary = task(
                        "write-summary", "Summarize both analyses",
                        Arrays.asList("analyze-a", "analyze-b"), "Final summary",
                        "Summary uses both analyses", Collections.singletonList("writing"));
                plan.setTasks(Arrays.asList(first, second, summary));
                return plan;
            }
            PlannedTask analysis = task(
                    "analyze",
                    "Analyze the request: " + intent.getObjective(),
                    Collections.<String>emptyList(),
                    "Structured analysis",
                    "Analysis addresses the stated objective",
                    Collections.singletonList("analysis"));
            PlannedTask writing = task(
                    "write-report",
                    "Write the requested report using the analysis",
                    Collections.singletonList("analyze"),
                    "Final report",
                    "Report is complete and grounded in the analysis",
                    Collections.singletonList("writing"));
            plan.setTasks(Arrays.asList(analysis, writing));
        } else {
            plan.setTasks(Collections.singletonList(task(
                    "single-task",
                    intent.getObjective(),
                    Collections.<String>emptyList(),
                    intent.getExpectedOutputs().isEmpty()
                            ? "Task result" : intent.getExpectedOutputs().get(0),
                    "Result contains a non-empty resultText",
                    intent.getRequiredCapabilities())));
        }
        return plan;
    }

    private PlannedTask task(
            String key,
            String objective,
            java.util.List<String> dependencies,
            String output,
            String criteria,
            java.util.List<String> capabilities) {
        PlannedTask task = new PlannedTask();
        task.setTaskKey(key);
        task.setObjective(objective);
        task.setDependencies(dependencies);
        task.setExpectedOutput(output);
        task.setAcceptanceCriteria(criteria);
        task.setRequiredCapabilities(capabilities);
        return task;
    }

    /**
     * 提交专家结果并上传产物；若结果已由其他通道写回（如测试直连端点
     * 或真实 UI 手动确认），忽略冲突，保持幂等。
     */
    private void submitResultTolerant(String taskId, String content) {
        try {
            uploadResult(taskId, content);
            cliSubmissions.submitResult(taskId, content);
        } catch (org.cmb.common.exception.ApiException ignored) {
            // result already written by another channel
        }
    }

    /** Mirror the real agent's tc upload-artifact call. */
    private void uploadResult(String taskId, String content) {
        cliSubmissions.uploadArtifact(
                taskId, "result.txt", "text/plain",
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // ── Event factory methods ───────────────────────────────────────────

    private AgentEvent queueEvent(String sessionId, long seq, long now) {
        AgentEvent e = AgentEvent.of("taskInQueue");
        e.setSessionId(sessionId);
        e.setSequence(seq);
        e.setEventId(sessionId + ":" + seq);
        e.setContent("您排在第1位，请耐心等待。");
        e.setTimestamp(now);
        return e;
    }

    private AgentEvent liveStatusEvent(String sessionId, long seq,
                                        String status, long now) {
        AgentEvent e = AgentEvent.of("liveStatus");
        e.setSessionId(sessionId);
        e.setSequence(seq);
        e.setEventId(sessionId + ":" + seq);
        e.setContent(status);
        e.setTimestamp(now);
        return e;
    }

    private AgentEvent thinkingStartEvent(String sessionId, long seq, long now) {
        AgentEvent e = AgentEvent.of("thinkingStart");
        e.setSessionId(sessionId);
        e.setSequence(seq);
        e.setEventId(sessionId + ":" + seq);
        e.setBlockType("text");
        e.setTimestamp(now);
        return e;
    }

    private AgentEvent thinkingDeltaEvent(String sessionId, long seq,
                                           String text, long now) {
        AgentEvent e = AgentEvent.of("thinkingDelta");
        e.setSessionId(sessionId);
        e.setSequence(seq);
        e.setEventId(sessionId + ":" + seq);
        e.setText(text);
        e.setTimestamp(now);
        return e;
    }

    private AgentEvent thinkingEndEvent(String sessionId, long seq, long now) {
        AgentEvent e = AgentEvent.of("thinkingEnd");
        e.setSessionId(sessionId);
        e.setSequence(seq);
        e.setEventId(sessionId + ":" + seq);
        e.setTotalTime(1500);
        e.setTimestamp(now);
        return e;
    }

    private AgentEvent streamStartEvent(String sessionId, long seq, long now) {
        AgentEvent e = AgentEvent.of("streamStart");
        e.setSessionId(sessionId);
        e.setSequence(seq);
        e.setEventId(sessionId + ":" + seq);
        e.setBlockType("text");
        e.setTimestamp(now);
        return e;
    }

    private AgentEvent textDeltaEvent(String sessionId, long seq,
                                       String text, long now) {
        AgentEvent e = AgentEvent.of("textDelta");
        e.setSessionId(sessionId);
        e.setSequence(seq);
        e.setEventId(sessionId + ":" + seq);
        e.setText(text);
        e.setTimestamp(now);
        return e;
    }

    private AgentEvent streamEndEvent(String sessionId, long seq, long now) {
        AgentEvent e = AgentEvent.of("streamEnd");
        e.setSessionId(sessionId);
        e.setSequence(seq);
        e.setEventId(sessionId + ":" + seq);
        e.setTotalTime(800);
        e.setTimestamp(now);
        return e;
    }

    private AgentEvent chatEvent(String sessionId, long seq, long now) {
        AgentEvent e = AgentEvent.of("chat");
        e.setSessionId(sessionId);
        e.setSequence(seq);
        e.setEventId(sessionId + ":" + seq);
        e.setTimestamp(now);
        return e;
    }

    private AgentEvent endEvent(String sessionId, long seq, long now) {
        AgentEvent e = AgentEvent.of("end");
        e.setSessionId(sessionId);
        e.setSequence(seq);
        e.setEventId(sessionId + ":" + seq);
        e.setTimestamp(now);
        return e;
    }

    private AgentEvent errorEvent(String sessionId, long seq,
                                   String message, long now) {
        AgentEvent e = AgentEvent.of("error");
        e.setSessionId(sessionId);
        e.setSequence(seq);
        e.setEventId(sessionId + ":" + seq);
        e.setContent(message);
        e.setTimestamp(now);
        return e;
    }

    private AgentEvent confirmEvent(String sessionId, long seq, long now) {
        AgentEvent e = AgentEvent.of("confirm");
        e.setSessionId(sessionId);
        e.setSequence(seq);
        e.setEventId(sessionId + ":" + seq);
        e.setQuestionId("mock-question-" + sessionId);
        e.setContent("Mock expert needs clarification.");
        List<AgentEvent.Question> questions = new ArrayList<>();
        AgentEvent.Question q = new AgentEvent.Question();
        q.setQuestion("请提供缺失的输入信息。");
        q.setHeader("补充信息");
        q.setMultiSelect(false);
        questions.add(q);
        e.setQuestions(questions);
        e.setTimestamp(now);
        return e;
    }

    private AgentEvent planUpdateEvent(String sessionId, long seq, long now) {
        AgentEvent e = AgentEvent.of("planUpdate");
        e.setSessionId(sessionId);
        e.setSequence(seq);
        e.setEventId(sessionId + ":" + seq);
        List<AgentEvent.PlanTaskStatus> tasks = new ArrayList<>();
        AgentEvent.PlanTaskStatus t = new AgentEvent.PlanTaskStatus();
        t.setStatus("doing");
        t.setTitle("执行计划任务");
        t.setStartedAt(now);
        tasks.add(t);
        e.setTasks(tasks);
        e.setTimestamp(now);
        return e;
    }

    private AgentEvent newPlanStepEvent(String sessionId, long seq,
                                         String step, long now) {
        AgentEvent e = AgentEvent.of("newPlanStep");
        e.setSessionId(sessionId);
        e.setSequence(seq);
        e.setEventId(sessionId + ":" + seq);
        e.setContent(step);
        e.setTimestamp(now);
        return e;
    }

    private AgentEvent toolUsedEvent(String sessionId, long seq,
                                      String tool, String skillName,
                                      String toolUseId, long now) {
        AgentEvent e = AgentEvent.of("toolUsed");
        e.setSessionId(sessionId);
        e.setSequence(seq);
        e.setEventId(sessionId + ":" + seq);
        e.setContent("正在使用工具 " + tool);
        e.setTool(tool);
        e.setInput(Collections.singletonMap("skill", skillName));
        e.setToolUseId(toolUseId);
        e.setTimestamp(now);
        return e;
    }

    /** Simulate a submission tool call whose input is the given JSON. */
    private AgentEvent submissionToolEvent(String sessionId, long seq,
                                           String tool, String inputJson, long now) {
        AgentEvent e = AgentEvent.of("toolUsed");
        e.setSessionId(sessionId);
        e.setSequence(seq);
        e.setEventId(sessionId + ":" + seq);
        e.setTool(tool);
        e.setToolUseId("call_" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 24));
        e.setTimestamp(now);
        try {
            e.setInput(objectMapper.convertValue(
                    objectMapper.readTree(inputJson), Map.class));
        } catch (Exception ex) {
            e.setInput(Collections.singletonMap("raw", inputJson));
        }
        return e;
    }

    private AgentEvent toolResultEvent(String sessionId, long seq,
                                        String tool, String toolUseId,
                                        String output, long now) {
        AgentEvent e = AgentEvent.of("toolResult");
        e.setSessionId(sessionId);
        e.setSequence(seq);
        e.setEventId(sessionId + ":" + seq);
        e.setTool(tool);
        e.setToolUseId(toolUseId);
        e.setOutput(output);
        e.setTimestamp(now);
        return e;
    }

    // ── Interface methods ───────────────────────────────────────────────

    @Override
    public AgentEvent getRunStatus(String targetAgentId, String sessionId) {
        List<AgentEvent> events = eventsBySessionId.get(sessionId);
        return events == null || events.isEmpty() ? null : events.get(events.size() - 1);
    }

    @Override
    public List<AgentEvent> streamEvents(String targetAgentId, String sessionId, Long afterSequence) {
        // Execute deferred CLI actions now that the run is live.
        Runnable pending = pendingActionsBySession.remove(sessionId);
        if (pending != null) {
            pending.run();
        }
        List<AgentEvent> events = eventsBySessionId.get(sessionId);
        if (events == null) {
            return Collections.emptyList();
        }
        long cursor = afterSequence == null ? 0L : afterSequence;
        List<AgentEvent> filtered = new ArrayList<>();
        for (AgentEvent event : events) {
            if (event.getSequence() > cursor) {
                filtered.add(event);
            }
        }
        return filtered;
    }

    @Override
    public AgentEvent cancelRun(String targetAgentId, String sessionId) {
        List<AgentEvent> events = eventsBySessionId.get(sessionId);
        if (events == null) {
            return null;
        }
        AgentEvent cancelled = AgentEvent.of("RUN_CANCELLED");
        cancelled.setSessionId(sessionId);
        cancelled.setSequence(events.size() + 1L);
        cancelled.setEventId(sessionId + ":" + cancelled.getSequence());
        cancelled.setContent("Mock run cancelled.");
        cancelled.setTimestamp(System.currentTimeMillis());
        events.add(cancelled);
        return cancelled;
    }

    @Override
    public AgentRunResponse resumeRun(
            String targetAgentId, String sessionId,
            String humanResponse, String idempotencyKey) {
        List<AgentEvent> events = eventsBySessionId.get(sessionId);
        if (events == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        long seq = events.get(events.size() - 1).getSequence();

        events.add(liveStatusEvent(sessionId, ++seq, "模型思考中", now));
        events.add(thinkingStartEvent(sessionId, ++seq, now));
        events.add(thinkingDeltaEvent(sessionId, ++seq,
                "根据用户补充信息重新分析...", now));
        events.add(thinkingEndEvent(sessionId, ++seq, now));
        events.add(streamStartEvent(sessionId, ++seq, now));
        events.add(textDeltaEvent(sessionId, ++seq,
                "已根据您的回复完成: " + humanResponse, now));
        events.add(streamEndEvent(sessionId, ++seq, now));

        AgentEvent chatE = chatEvent(sessionId, ++seq, now);
        chatE.setContent("已根据您的回复完成: " + humanResponse);
        events.add(chatE);
        String cliTaskId = expertTaskBySession.get(sessionId);
        if (cliSubmissions != null && cliTaskId != null) {
            pendingActionsBySession.put(sessionId, () ->
                    cliSubmissions.submitResult(cliTaskId,
                            "已根据您的回复完成: " + humanResponse));
        }

        AgentEvent endE = endEvent(sessionId, ++seq, now);
        endE.setContent("已根据您的回复完成: " + humanResponse);
        events.add(endE);

        return new AgentRunResponse(sessionId, "RUNNING");
    }

    @Override
    public AgentRunResponse answerQuestion(
            String targetAgentId, String sessionId,
            String questionId, Map<String, String> answers) {
        return resumeRun(targetAgentId, sessionId,
                String.valueOf(answers), questionId);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private String storageKey(String downloadUrl) {
        if (downloadUrl == null) {
            return "";
        }
        int marker = downloadUrl.indexOf("/mock/files/");
        if (marker < 0) {
            return downloadUrl;
        }
        String suffix = downloadUrl.substring(marker + "/mock/files/".length());
        return suffix.endsWith("/content")
                ? suffix.substring(0, suffix.length() - "/content".length()) : suffix;
    }
}
