package org.cmb.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cmb.application.domain.AgentRunAttachment;
import org.cmb.application.domain.CoordinatorDecision;
import org.cmb.application.domain.CoordinatorPlanSpec;
import org.cmb.application.domain.DispatchWork;
import org.cmb.application.domain.PlanningResult;
import org.cmb.application.domain.TaskRecord;
import org.cmb.common.enums.DecisionType;
import org.cmb.common.exception.ApiException;
import org.cmb.infrastructure.persistent.ArtifactRepository;
import org.cmb.infrastructure.persistent.CliSubmissionRepository;
import org.cmb.infrastructure.persistent.ExecutionRepository;
import org.springframework.stereotype.Service;

/**
 * Accepts structured Coordinator outputs submitted by the companion CLI.
 * Everything is keyed by the task id — the only identifier the AgentCore
 * runtime and the CLI reliably share. Coordinator outputs (decision / plan /
 * verdict) are keyed by the conversation task id; expert-side operations
 * (task detail fetch, result write-back) by the coordinator task id.
 */
@Service
public class CliSubmissionService {

    private static final int PLAN_VERSION = 1;

    private final CliSubmissionRepository submissions;
    private final ExecutionRepository executionRepository;
    private final ArtifactRepository artifactRepository;
    private final ArtifactService artifactService;
    private final PromptService prompts;
    private final DecisionSchemaValidator decisionValidator;
    private final PlanSchemaValidator planValidator;
    private final ObjectMapper objectMapper;

    public CliSubmissionService(
            CliSubmissionRepository submissions,
            ExecutionRepository executionRepository,
            ArtifactRepository artifactRepository,
            ArtifactService artifactService,
            PromptService prompts,
            DecisionSchemaValidator decisionValidator,
            PlanSchemaValidator planValidator,
            ObjectMapper objectMapper) {
        this.submissions = submissions;
        this.executionRepository = executionRepository;
        this.artifactRepository = artifactRepository;
        this.artifactService = artifactService;
        this.prompts = prompts;
        this.decisionValidator = decisionValidator;
        this.planValidator = planValidator;
        this.objectMapper = objectMapper;
    }

    // ── Coordinator outputs (conversation task id) ─────────────────────

    public void submitDecision(String taskId, String payload) {
        JsonNode node = parse(payload);
        decisionValidator.validate(node);
        validateDecisionShape(node);
        submissions.save(taskId, CliSubmissionRepository.KIND_DECISION, payload);
    }

    public void submitPlan(String taskId, String payload) {
        JsonNode node = parse(payload);
        planValidator.validate(node);
        CoordinatorPlanSpec plan = convert(node, CoordinatorPlanSpec.class);
        if (plan.getPlanVersion() != PLAN_VERSION) {
            throw ApiException.badRequest(
                    "CLI_PLAN_VERSION_MISMATCH",
                    "Expected plan_version " + PLAN_VERSION);
        }
        submissions.save(taskId, CliSubmissionRepository.KIND_PLAN, payload);
        writePlanIfPossible(taskId, plan, payload);
    }

    public void submitVerdict(String taskId, String payload) {
        JsonNode node = parse(payload);
        if (!node.has("consistent") || !node.get("consistent").isBoolean()) {
            throw ApiException.badRequest(
                    "CLI_VERDICT_INVALID",
                    "Verdict payload must contain a boolean \"consistent\" field.");
        }
        submissions.save(taskId, CliSubmissionRepository.KIND_VERDICT, payload);
    }

    /**
     * Best-effort plan activation: the task id IS the conversation id, so
     * the pending dispatch can be resolved directly. When the decision has
     * not been submitted yet, the plan stays stored and the worker
     * activates it via the same path once the decision arrives.
     */
    private void writePlanIfPossible(
            String taskId, CoordinatorPlanSpec plan, String rawJson) {
        List<String> dispatchIds =
                executionRepository.findDispatchForConversation(taskId);
        if (dispatchIds.isEmpty()) {
            return;
        }
        DispatchWork work = executionRepository.loadWork(dispatchIds.get(0));
        if (work == null) {
            return;
        }
        String decisionJson =
                submissions.find(taskId, CliSubmissionRepository.KIND_DECISION);
        if (decisionJson == null) {
            return;
        }
        CoordinatorDecision decision;
        try {
            decision = objectMapper.readValue(decisionJson, CoordinatorDecision.class);
        } catch (Exception ex) {
            return;
        }
        if (decision.getDecisionType() != DecisionType.CREATE_PLAN) {
            return;
        }
        executionRepository.createPlan(work, decision,
                new PlanningResult(plan, rawJson, 0, null));
    }

    // ── Expert-side operations (coordinator task id) ───────────────────

    /**
     * The task detail an expert agent pulls after being dispatched with
     * nothing but the task id: rendered execution prompt plus the raw
     * contract fields and upstream artifacts.
     */
    public Map<String, Object> getTaskDetail(String taskId) {
        Map<String, Object> detail = executionRepository.findTaskDetail(taskId);
        if (detail == null) {
            throw ApiException.notFound(
                    "TASK_NOT_FOUND", "Task was not found: " + taskId);
        }
        List<String> dependencies = readList((String) detail.get("dependencies"));
        List<String> capabilities =
                readList((String) detail.get("required_capabilities"));
        String planId = (String) detail.get("plan_id");
        List<String> inputRefs =
                artifactRepository.findAvailableStorageKeys(planId, dependencies);
        List<AgentRunAttachment> attachments =
                artifactService.toAgentAttachments(inputRefs);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("projectName", detail.get("project_name"));
        context.put("projectDescription", detail.get("project_description"));
        context.put("projectId", detail.get("project_id"));
        context.put("taskId", taskId);
        context.put("overallRequest", detail.get("overall_request"));
        context.put("taskKey", detail.get("task_key"));
        context.put("objective", detail.get("objective"));
        context.put("expectedOutput", detail.get("expected_output"));
        context.put("acceptanceCriteria", detail.get("acceptance_criteria"));
        context.put("dependencies", dependencies);
        context.put("requiredCapabilities", capabilities);
        context.put("inputArtifactRefs", inputRefs);
        context.put("businessSessionId", detail.get("business_session_id"));
        org.cmb.application.dto.RenderedPrompt prompt = prompts.render(
                PromptService.EXPERT_EXECUTION, context,
                (String) detail.get("tenant_id"),
                (String) detail.get("project_id"),
                (String) detail.get("conversation_id"),
                taskId + ":cli-pull",
                (String) detail.get("expert_id"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", taskId);
        response.put("taskKey", detail.get("task_key"));
        response.put("objective", detail.get("objective"));
        response.put("expectedOutput", detail.get("expected_output"));
        response.put("acceptanceCriteria", detail.get("acceptance_criteria"));
        response.put("dependencies", dependencies);
        response.put("requiredCapabilities", capabilities);
        response.put("systemPrompt", prompt.getContent());
        response.put("attachments", attachments);
        return response;
    }

    /**
     * Expert result write-back: marks the task SUCCEEDED with the result
     * text. The task-level plan completion is left to the worker's next
     * advancePlan tick, exactly as with the event-driven path.
     */
    public void submitResult(String taskId, String resultText) {
        if (resultText == null || resultText.trim().isEmpty()) {
            throw ApiException.badRequest(
                    "CLI_RESULT_EMPTY", "Result text must not be empty.");
        }
        TaskRecord task = executionRepository.findTaskByBusinessId(taskId);
        if (task == null) {
            throw ApiException.notFound(
                    "TASK_NOT_FOUND", "Task was not found: " + taskId);
        }
        String resultJson = "{\"content\":" + writeQuoted(resultText) + "}";
        if (!executionRepository.markTaskSucceeded(taskId, resultJson)) {
            throw ApiException.conflict(
                    "TASK_NOT_RUNNING",
                    "Task is already in a terminal state: " + task.getStatus());
        }
        if (task.getCorrectionOf() != null) {
            executionRepository.acceptCorrection(task, resultJson);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /**
     * Branch requirements beyond the JSON Schema (mirrors
     * IntentAnalysisService.validateDecisionShape).
     */
    private void validateDecisionShape(JsonNode node) {
        String type = node.path("decision_type").asText();
        if ("ANSWER".equals(type) && isBlank(node.path("answer").asText())) {
            throw ApiException.badRequest(
                    "CLI_DECISION_INVALID", "ANSWER requires a non-empty answer.");
        }
        if ("ASK_HUMAN".equals(type) && isBlank(node.path("question").asText())) {
            throw ApiException.badRequest(
                    "CLI_DECISION_INVALID", "ASK_HUMAN requires a non-empty question.");
        }
        if ("CREATE_PLAN".equals(type)
                && (node.path("task_intent").isMissingNode()
                || node.path("task_intent").isNull())) {
            throw ApiException.badRequest(
                    "CLI_DECISION_INVALID", "CREATE_PLAN requires task_intent.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private List<String> readList(String json) {
        if (json == null) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private String writeQuoted(String text) {
        try {
            return objectMapper.writeValueAsString(text);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize result text.", ex);
        }
    }

    private JsonNode parse(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw ApiException.badRequest(
                    "CLI_PAYLOAD_NOT_JSON", "Payload is not valid JSON.");
        }
    }

    private <T> T convert(JsonNode node, Class<T> type) {
        try {
            return objectMapper.treeToValue(node, type);
        } catch (Exception ex) {
            throw ApiException.badRequest(
                    "CLI_PAYLOAD_INVALID", "Payload does not match the expected structure.");
        }
    }
}
