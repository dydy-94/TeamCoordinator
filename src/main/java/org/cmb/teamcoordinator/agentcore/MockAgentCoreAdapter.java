package org.cmb.teamcoordinator.agentcore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cmb.teamcoordinator.artifact.MockFileDescriptor;
import org.cmb.teamcoordinator.artifact.FileStore;
import org.cmb.teamcoordinator.artifact.MockFileStore;
import org.cmb.teamcoordinator.config.DigitalTeamProperties;
import org.cmb.teamcoordinator.intent.IntentAnalysisContext;
import org.cmb.teamcoordinator.intent.MockIntentModelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "digital-team.agent-core", name = "mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockAgentCoreAdapter implements AgentCoreAdapter {

    private final Map<String, List<AgentRunEvent>> eventsBySessionId = new ConcurrentHashMap<>();
    private final Map<String, AgentRunResponse> responsesByIdempotencyKey = new ConcurrentHashMap<>();
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

    public MockAgentCoreAdapter(DigitalTeamProperties properties) {
        this.fileStore = new MockFileStore();
        this.objectMapper = new ObjectMapper();
        this.coordinatorAgent = new MockIntentModelClient(objectMapper);
        this.coordinatorAgentId = properties.getAgentCore().getCoordinatorAgentId();
    }

    @Override
    public AgentRunResponse submitRun(AgentRunRequest request) {
        if (request.getIdempotencyKey() != null) {
            AgentRunResponse existing = responsesByIdempotencyKey.get(request.getIdempotencyKey());
            if (existing != null) {
                return existing;
            }
        }

        String sessionId = "mock-run-" + UUID.randomUUID().toString();
        List<AgentRunEvent> events = new ArrayList<>();
        AgentRunEvent accepted = new AgentRunEvent(
                sessionId, 1, "RUN_ACCEPTED", "ACCEPTED",
                "Mock expert run accepted.");
        accepted.getPayload().put("businessSessionId", request.getBusinessSessionId());
        events.add(accepted);
        events.add(new AgentRunEvent(sessionId, 2, "RUN_PROGRESS", "RUNNING", "Mock expert is processing the task."));

        Object objective = request.getStructuredInput() == null
                ? null : request.getStructuredInput().get("objective");
        String effectiveTask = objective == null
                ? request.getTaskText() : String.valueOf(objective);
        String normalizedTask = effectiveTask == null ? "" : effectiveTask.toLowerCase();
        if (coordinatorAgentId.equals(request.getExpertId())) {
            addCoordinatorEvents(request, sessionId, events);
            eventsBySessionId.put(sessionId, events);
            AgentRunResponse response = new AgentRunResponse(sessionId, "ACCEPTED");
            if (request.getIdempotencyKey() != null) {
                responsesByIdempotencyKey.put(request.getIdempotencyKey(), response);
            }
            return response;
        }
        if (normalizedTask.contains("need-human")) {
            AgentRunEvent waiting = new AgentRunEvent(
                    sessionId, 3, "RUN_WAITING_HUMAN", "WAITING_HUMAN",
                    "Mock expert needs clarification.");
            waiting.getPayload().put("question", "Please provide the missing expert input.");
            waiting.getPayload().put("requestType", "CLARIFICATION");
            events.add(waiting);
        } else if (normalizedTask.contains("timeout")) {
            events.add(new AgentRunEvent(sessionId, 3, "RUN_TIMED_OUT", "TIMED_OUT", "Mock expert reached its execution deadline."));
        } else if (normalizedTask.contains("fail")) {
            events.add(new AgentRunEvent(sessionId, 3, "RUN_FAILED", "FAILED", "Mock expert failed by requested scenario."));
        } else {
            AgentRunEvent result = new AgentRunEvent(sessionId, 3, "RUN_SUCCEEDED", "SUCCEEDED", "Mock expert completed the task.");
            result.getPayload().put("expertId", request.getExpertId());
            result.getPayload().put(
                    "businessSessionId", request.getBusinessSessionId());
            if (!normalizedTask.contains("invalid-result")) {
                result.getPayload().put("resultText", "Mock result for: " + effectiveTask);
            }
            result.getPayload().put("attachmentRefs", request.getAttachmentRefs());
            List<String> attachmentContents = new ArrayList<>();
            for (String attachmentRef : request.getAttachmentRefs()) {
                byte[] attachment = fileStore.getContent(attachmentRef);
                if (attachment != null) {
                    attachmentContents.add(new String(attachment, StandardCharsets.UTF_8));
                }
            }
            result.getPayload().put("attachmentContents", attachmentContents);
            MockFileDescriptor artifact = fileStore.reserve("result.txt", "text/plain");
            String artifactContent = "Mock result for: " + effectiveTask
                    + (attachmentContents.isEmpty() ? "" : "\nInput: " + attachmentContents.get(0));
            fileStore.put(artifact.getFileId(), artifactContent.getBytes(StandardCharsets.UTF_8));
            result.getPayload().put("artifactRefs", Collections.singletonList(artifact.getDownloadUrl()));
            result.getPayload().put("artifactFileIds", Collections.singletonList(artifact.getFileId()));
            events.add(result);
        }

        eventsBySessionId.put(sessionId, events);
        AgentRunResponse response = new AgentRunResponse(sessionId, "ACCEPTED");
        if (request.getIdempotencyKey() != null) {
            responsesByIdempotencyKey.put(request.getIdempotencyKey(), response);
        }
        return response;
    }

    private void addCoordinatorEvents(
            AgentRunRequest request, String sessionId, List<AgentRunEvent> events) {
        AgentRunEvent result = new AgentRunEvent(
                sessionId, 3, "RUN_SUCCEEDED", "SUCCEEDED",
                "Mock coordinator completed intent analysis.");
        try {
            Object rawContext = request.getStructuredInput().get("context");
            IntentAnalysisContext context = objectMapper.convertValue(
                    rawContext, IntentAnalysisContext.class);
            String operation = String.valueOf(
                    request.getStructuredInput().get("operation"));
            if (context.getText().startsWith("__always_invalid__")) {
                result.getPayload().put("resultText", "{still-invalid");
            } else if ("ANALYZE".equals(operation)
                    && context.getText().startsWith("__invalid_once__")) {
                result.getPayload().put("resultText", "{invalid");
            } else {
                result.getPayload().put(
                        "decision", coordinatorAgent.classify(context));
            }
        } catch (RuntimeException ex) {
            result.setType("RUN_FAILED");
            result.setStatus("FAILED");
            result.setMessage("Mock coordinator could not parse structured input.");
        }
        events.add(result);
    }

    @Override
    public AgentRunEvent getRunStatus(String sessionId) {
        List<AgentRunEvent> events = eventsBySessionId.get(sessionId);
        return events == null || events.isEmpty() ? null : events.get(events.size() - 1);
    }

    @Override
    public List<AgentRunEvent> streamEvents(String sessionId, Long afterSequence) {
        List<AgentRunEvent> events = eventsBySessionId.get(sessionId);
        if (events == null) {
            return Collections.emptyList();
        }

        long cursor = afterSequence == null ? 0L : afterSequence;
        List<AgentRunEvent> filtered = new ArrayList<>();
        for (AgentRunEvent event : events) {
            if (event.getSequence() > cursor) {
                filtered.add(event);
            }
        }
        return filtered;
    }

    @Override
    public AgentRunEvent cancelRun(String sessionId) {
        List<AgentRunEvent> events = eventsBySessionId.get(sessionId);
        if (events == null) {
            return null;
        }
        AgentRunEvent cancelled = new AgentRunEvent(sessionId, events.size() + 1L, "RUN_CANCELLED", "CANCELLED", "Mock expert run cancelled.");
        events.add(cancelled);
        return cancelled;
    }

    @Override
    public AgentRunResponse resumeRun(
            String sessionId, String humanResponse, String idempotencyKey) {
        List<AgentRunEvent> events = eventsBySessionId.get(sessionId);
        if (events == null) {
            return null;
        }
        long sequence = events.get(events.size() - 1).getSequence();
        events.add(new AgentRunEvent(
                sessionId, sequence + 1, "RUN_PROGRESS", "RUNNING",
                "Mock expert resumed with human input."));
        AgentRunEvent result = new AgentRunEvent(
                sessionId, sequence + 2, "RUN_SUCCEEDED", "SUCCEEDED",
                "Mock expert completed after human input.");
        result.getPayload().put("resultText", "Mock resumed result: " + humanResponse);
        events.add(result);
        return new AgentRunResponse(sessionId, "RUNNING");
    }
}
