package org.cmb.teamcoordinator.intent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cmb.teamcoordinator.agentcore.AgentCoreAdapter;
import org.cmb.teamcoordinator.agentcore.AgentRunEvent;
import org.cmb.teamcoordinator.agentcore.AgentRunRequest;
import org.cmb.teamcoordinator.agentcore.AgentRunResponse;
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

    public CoordinatorAgentClient(
            AgentCoreAdapter agentCore, CoordinatorAgentRunRepository runs,
            ObjectMapper objectMapper, DigitalTeamProperties properties,
            PromptService prompts) {
        this.agentCore = agentCore;
        this.runs = runs;
        this.objectMapper = objectMapper;
        this.coordinatorAgentId = properties.getAgentCore().getCoordinatorAgentId();
        this.prompts = prompts;
    }

    public Result execute(
            RequestIdentity identity, String projectId, String messageId, String runKey,
            String businessSessionId, IntentAnalysisContext context) {
        CoordinatorAgentRun run = runs.createOrLoad(
                identity, projectId, messageId, runKey, write(context), businessSessionId);
        if ("SUCCEEDED".equals(run.getStatus()) || "FAILED".equals(run.getStatus())) {
            return new Result(true, run.getOutputJson(), "REPAIR".equals(run.getStage()));
        }
        if (run.getSessionId() == null) {
            submit(identity, projectId, run, context);
            run = runs.find(identity.getTenantId(), runKey);
        }

        List<AgentRunEvent> events = agentCore.streamEvents(
                run.getSessionId(), run.getLastSequence(), run.getBusinessSessionId());
        events.sort(Comparator.comparingLong(AgentRunEvent::getSequence));
        for (AgentRunEvent event : events) {
            if ("RUN_SUCCEEDED".equals(event.getType())) {
                String output = output(event);
                runs.complete(run.getId(), event.getSequence(), output);
                return new Result(true, output, "REPAIR".equals(run.getStage()));
            }
            if (isFailure(event)) {
                runs.fail(run.getId(), null);
                return new Result(true, null, "REPAIR".equals(run.getStage()));
            }
            runs.advance(run.getId(), event);
        }
        return new Result(false, null, "REPAIR".equals(run.getStage()));
    }

    public void prepareRepair(
            RequestIdentity identity, String runKey, String invalidOutput) {
        CoordinatorAgentRun run = runs.find(identity.getTenantId(), runKey);
        runs.prepareRepair(run.getId(), invalidOutput);
    }

    private void submit(
            RequestIdentity identity, String projectId,
            CoordinatorAgentRun run, IntentAnalysisContext context) {
        AgentRunRequest request = new AgentRunRequest();
        request.setExpertId(coordinatorAgentId);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("operation", run.getStage());
        input.put("context", objectMapper.convertValue(
                context, new TypeReference<Map<String, Object>>() { }));
        input.put("invalidOutput", run.getInvalidOutput());
        RenderedPrompt prompt = prompts.render(
                PromptService.COORDINATOR_EXECUTION, input,
                identity.getTenantId(), projectId, null,
                run.getId() + ":" + run.getStage(), coordinatorAgentId);
        request.setTaskText(prompt.getContent());
        input.put("promptVersion", prompt.getVersion());
        input.put("promptTemplateId", prompt.getTemplateId());
        request.setStructuredInput(input);
        request.setAttachmentRefs(context.getAttachmentRefs());
        request.setIdempotencyKey(run.getId() + ":" + run.getStage());
        request.setBusinessSessionId(run.getBusinessSessionId());
        AgentRunResponse response = agentCore.submitRun(request);
        runs.saveSession(run.getId(), run.getStage(), response.getSessionId());
    }

    private String output(AgentRunEvent event) {
        Object decision = event.getPayload().get("decision");
        if (decision != null) {
            return decision instanceof String ? (String) decision : write(decision);
        }
        Object resultText = event.getPayload().get("resultText");
        return resultText == null ? null : String.valueOf(resultText);
    }

    private boolean isFailure(AgentRunEvent event) {
        return "RUN_FAILED".equals(event.getType())
                || "RUN_CANCELLED".equals(event.getType())
                || "RUN_TIMED_OUT".equals(event.getType());
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize coordinator run.", ex);
        }
    }

    public static class Result {
        private final boolean complete;
        private final String output;
        private final boolean repaired;

        Result(boolean complete, String output, boolean repaired) {
            this.complete = complete;
            this.output = output;
            this.repaired = repaired;
        }

        public boolean isComplete() { return complete; }
        public String getOutput() { return output; }
        public boolean isRepaired() { return repaired; }
    }
}
