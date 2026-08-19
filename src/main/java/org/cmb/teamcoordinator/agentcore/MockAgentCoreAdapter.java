package org.cmb.teamcoordinator.agentcore;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.cmb.teamcoordinator.artifact.FileStore;
import org.cmb.teamcoordinator.artifact.MockFileDescriptor;
import org.cmb.teamcoordinator.artifact.MockFileStore;
import org.cmb.teamcoordinator.config.DigitalTeamProperties;
import org.cmb.teamcoordinator.intent.IntentAnalysisContext;
import org.cmb.teamcoordinator.intent.MockIntentModelClient;
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

    @Autowired
    public MockAgentCoreAdapter(
            DigitalTeamProperties properties, FileStore fileStore,
            ObjectMapper objectMapper, MockIntentModelClient coordinatorAgent) {
        this.fileStore = fileStore;
        this.objectMapper = objectMapper;
        this.coordinatorAgent = coordinatorAgent;
        this.coordinatorAgentId = properties.getAgentCore().getCoordinatorAgentId();
    }

    /** Constructor for tests that don't need full Spring context. */
    public MockAgentCoreAdapter(DigitalTeamProperties properties) {
        this.fileStore = new MockFileStore();
        this.objectMapper = new ObjectMapper();
        this.coordinatorAgent = new MockIntentModelClient(objectMapper);
        this.coordinatorAgentId = properties.getAgentCore().getCoordinatorAgentId();
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

        // 5. Scenario-specific terminal events
        if (normalizedTask.contains("need-human")) {
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
        // Valid decisions are submitted via the submission tool, mirroring
        // the real AgentCore contract. Invalid-output scenarios deliberately
        // skip the tool call so repair paths stay exercised.
        if (decisionJson != null) {
            events.add(submissionToolEvent(
                    sessionId, ++seq,
                    AgentCoreTools.SUBMIT_COORDINATOR_DECISION, decisionJson, now));
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
