package org.cmb.teamcoordinator.intent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.cmb.teamcoordinator.agentcore.AgentCoreAdapter;
import org.cmb.teamcoordinator.agentcore.AgentEvent;
import org.cmb.teamcoordinator.agentcore.AgentRunRequest;
import org.cmb.teamcoordinator.agentcore.AgentRunResponse;
import org.cmb.teamcoordinator.artifact.ArtifactRepository;
import org.cmb.teamcoordinator.artifact.ArtifactService;
import org.cmb.teamcoordinator.config.DigitalTeamProperties;
import org.cmb.teamcoordinator.prompt.PromptService;
import org.cmb.teamcoordinator.prompt.RenderedPrompt;
import org.cmb.teamcoordinator.project.RequestIdentity;
import org.springframework.stereotype.Component;

@Component
public class CoordinatorAgentClient {

    public static final String COORDINATOR_AGENT_ID = "coordinator";

    private final AgentCoreAdapter agentCore;
    private final CoordinatorAgentRunRepository runs;
    private final ObjectMapper objectMapper;
    private final String coordinatorAgentId;
    private final PromptService prompts;
    private final ArtifactRepository artifacts;
    private final ArtifactService artifactService;

    public CoordinatorAgentClient(
            AgentCoreAdapter agentCore, CoordinatorAgentRunRepository runs,
            ObjectMapper objectMapper, DigitalTeamProperties properties,
            PromptService prompts, ArtifactRepository artifacts,
            ArtifactService artifactService) {
        this.agentCore = agentCore;
        this.runs = runs;
        this.objectMapper = objectMapper;
        this.coordinatorAgentId = properties.getAgentCore().getCoordinatorAgentId();
        this.prompts = prompts;
        this.artifacts = artifacts;
        this.artifactService = artifactService;
    }

    /**
     * Execute a coordinator agent run, forwarding intermediate events to
     * {@code eventSink} so they can be relayed to the task SSE stream.
     *
     * @param coordinatorSessionId task-level coordinator session for context reuse
     *                             across messages; may be {@code null} for the
     *                             first message in a task
     * @param eventSink optional consumer for non-terminal agent events;
     *                  may be {@code null} for callers that only need the
     *                  final decision (e.g. direct intent analysis)
     */
    public Result execute(
            RequestIdentity identity, String projectId, String messageId, String runKey,
            String businessSessionId, String coordinatorSessionId,
            IntentAnalysisContext context,
            Consumer<AgentEvent> eventSink) {
        String effectiveAgent = effectiveCoordinatorAgent(context);
        CoordinatorAgentRun run = runs.createOrLoad(
                identity, projectId, messageId, runKey, write(context), businessSessionId);
        if ("SUCCEEDED".equals(run.getStatus()) || "FAILED".equals(run.getStatus())) {
            String sid = run.getSessionId();
            return new Result(true, run.getOutputJson(), sid, effectiveAgent,
                    "REPAIR".equals(run.getStage()));
        }
        // Submit if no session yet, or if repairing (need new events within same conversation)
        if (run.getSessionId() == null || "REPAIR".equals(run.getStage())) {
            submit(identity, projectId, run, context, coordinatorSessionId);
            run = runs.find(identity.getTenantId(), runKey);
        }

        List<AgentEvent> events = agentCore.streamEvents(
                effectiveAgent, run.getSessionId(),
                run.getLastSequence(), run.getBusinessSessionId());
        events.sort(Comparator.comparingLong(AgentEvent::getSequence));
        for (AgentEvent event : events) {
            // Forward intermediate events to the task SSE stream
            if (eventSink != null) {
                event.setAgentId(effectiveAgent);
                eventSink.accept(event);
            }

            if ("end".equals(event.getType())) {
                String output = event.getContent();
                runs.complete(run.getId(), event.getSequence(), output);
                return new Result(true, output, run.getSessionId(), effectiveAgent,
                        "REPAIR".equals(run.getStage()));
            }
            if (isFailure(event)) {
                runs.fail(run.getId(), event.getContent());
                return new Result(true, null, run.getSessionId(), effectiveAgent,
                        "REPAIR".equals(run.getStage()));
            }
            runs.advance(run.getId(), event);
        }
        return new Result(false, null, run.getSessionId(), effectiveAgent,
                "REPAIR".equals(run.getStage()));
    }

    /**
     * Execute without forwarding events (for direct intent analysis calls).
     */
    public Result execute(
            RequestIdentity identity, String projectId, String messageId, String runKey,
            String businessSessionId, IntentAnalysisContext context) {
        return execute(identity, projectId, messageId, runKey,
                businessSessionId, null, context, null);
    }

    public void prepareRepair(
            RequestIdentity identity, String runKey, String invalidOutput) {
        CoordinatorAgentRun run = runs.find(identity.getTenantId(), runKey);
        runs.prepareRepair(run.getId(), invalidOutput);
    }

    /** Resolve the effective coordinator agent ID: project override > global config. */
    private String effectiveCoordinatorAgent(IntentAnalysisContext context) {
        String projectOverride = context.getCoordinatorAgentId();
        return (projectOverride != null && !projectOverride.trim().isEmpty())
                ? projectOverride : coordinatorAgentId;
    }

    private void submit(
            RequestIdentity identity, String projectId,
            CoordinatorAgentRun run, IntentAnalysisContext context,
            String coordinatorSessionId) {
        String effectiveAgentId = effectiveCoordinatorAgent(context);
        AgentRunRequest request = new AgentRunRequest();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("operation", run.getStage());
        input.put("context", objectMapper.convertValue(
                context, new TypeReference<Map<String, Object>>() { }));
        input.put("invalidOutput", run.getInvalidOutput());
        RenderedPrompt prompt = prompts.render(
                PromptService.COORDINATOR_EXECUTION, input,
                identity.getTenantId(), projectId, null,
                run.getId() + ":" + run.getStage(), effectiveAgentId);
        request.setSystemPrompt(prompt.getContent());
        request.setTaskText(context.getText());
        input.put("promptVersion", prompt.getVersion());
        input.put("promptTemplateId", prompt.getTemplateId());
        request.setStructuredInput(input);
        List<String> storageKeys = new java.util.ArrayList<>();
        for (String reference : context.getAttachmentRefs()) {
            storageKeys.add(artifacts.resolveStorageKey(
                    identity.getTenantId(), projectId, reference));
        }
        request.setAttachments(artifactService.toAgentAttachments(storageKeys));
        // Session reuse priority:
        // 1. Repair: reuse the run's own session (same message, same run)
        // 2. Cross-message: reuse task-level coordinator session for context continuity
        if (run.getSessionId() != null) {
            request.setConversationSessionId(run.getSessionId());
        } else if (coordinatorSessionId != null) {
            request.setConversationSessionId(coordinatorSessionId);
        }
        AgentRunResponse response = agentCore.submitRun(effectiveAgentId, request);
        runs.saveSession(run.getId(), run.getStage(), response.getSessionId());
    }

    private boolean isFailure(AgentEvent event) {
        return "error".equals(event.getType());
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Could not serialize coordinator run.", ex);
        }
    }

    public static class Result {
        private final boolean complete;
        private final String output;
        private final String sessionId;
        private final String effectiveAgentId;
        private final boolean repaired;

        Result(boolean complete, String output, String sessionId,
                String effectiveAgentId, boolean repaired) {
            this.complete = complete;
            this.output = output;
            this.sessionId = sessionId;
            this.effectiveAgentId = effectiveAgentId;
            this.repaired = repaired;
        }

        public boolean isComplete() { return complete; }
        public String getOutput() { return output; }
        public String getSessionId() { return sessionId; }
        public String getEffectiveAgentId() { return effectiveAgentId; }
        public boolean isRepaired() { return repaired; }
    }
}
