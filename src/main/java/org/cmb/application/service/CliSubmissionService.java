package org.cmb.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.cmb.application.domain.CoordinatorDecision;
import org.cmb.application.domain.CoordinatorPlanSpec;
import org.cmb.application.domain.DispatchWork;
import org.cmb.application.domain.PlanningResult;
import org.cmb.common.enums.DecisionType;
import org.cmb.common.exception.ApiException;
import org.cmb.infrastructure.persistent.CliSubmissionRepository;
import org.cmb.infrastructure.persistent.ExecutionRepository;
import org.springframework.stereotype.Service;

/**
 * Accepts structured Coordinator outputs submitted by the companion CLI and
 * stores them for the execution engine. A submitted plan additionally
 * drives the engine directly: when the conversation context is resolvable,
 * the plan is written to coordinator_plan / coordinator_task so subsequent
 * expert dispatching proceeds without streamed-text parsing.
 */
@Service
public class CliSubmissionService {

    private static final int PLAN_VERSION = 1;

    private final CliSubmissionRepository submissions;
    private final ExecutionRepository executionRepository;
    private final DecisionSchemaValidator decisionValidator;
    private final PlanSchemaValidator planValidator;
    private final ObjectMapper objectMapper;

    public CliSubmissionService(
            CliSubmissionRepository submissions,
            ExecutionRepository executionRepository,
            DecisionSchemaValidator decisionValidator,
            PlanSchemaValidator planValidator,
            ObjectMapper objectMapper) {
        this.submissions = submissions;
        this.executionRepository = executionRepository;
        this.decisionValidator = decisionValidator;
        this.planValidator = planValidator;
        this.objectMapper = objectMapper;
    }

    public void submitDecision(String sessionId, String payload) {
        JsonNode node = parse(payload);
        decisionValidator.validate(node);
        validateDecisionShape(node);
        submissions.save(sessionId, CliSubmissionRepository.KIND_DECISION, payload);
    }

    /**
     * Branch requirements beyond the JSON Schema: the schema only requires
     * decision_type, but each decision type has its own mandatory payload
     * (mirrors IntentAnalysisService.validateDecisionShape).
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

    public void submitPlan(String sessionId, String payload) {
        JsonNode node = parse(payload);
        planValidator.validate(node);
        CoordinatorPlanSpec plan = convert(node, CoordinatorPlanSpec.class);
        if (plan.getPlanVersion() != PLAN_VERSION) {
            throw ApiException.badRequest(
                    "CLI_PLAN_VERSION_MISMATCH",
                    "Expected plan_version " + PLAN_VERSION);
        }
        submissions.save(sessionId, CliSubmissionRepository.KIND_PLAN, payload);
        writePlanIfPossible(sessionId, plan, payload);
    }

    public void submitVerdict(String sessionId, String payload) {
        JsonNode node = parse(payload);
        if (!node.has("consistent") || !node.get("consistent").isBoolean()) {
            throw ApiException.badRequest(
                    "CLI_VERDICT_INVALID",
                    "Verdict payload must contain a boolean \"consistent\" field.");
        }
        submissions.save(sessionId, CliSubmissionRepository.KIND_VERDICT, payload);
    }

    /**
     * Best-effort plan activation: resolve session → conversation → pending
     * dispatch → CREATE_PLAN decision, then write the plan. When the
     * decision has not been submitted yet, the plan stays stored and the
     * worker activates it via the same path once the decision arrives.
     */
    private void writePlanIfPossible(
            String sessionId, CoordinatorPlanSpec plan, String rawJson) {
        List<String> conversations =
                executionRepository.findConversationByCoordinatorSession(sessionId);
        if (conversations.isEmpty()) {
            return;
        }
        String conversationId = conversations.get(0);
        List<String> dispatchIds =
                executionRepository.findDispatchForConversation(conversationId);
        if (dispatchIds.isEmpty()) {
            return;
        }
        DispatchWork work = executionRepository.loadWork(dispatchIds.get(0));
        if (work == null) {
            return;
        }
        String decisionJson =
                submissions.find(sessionId, CliSubmissionRepository.KIND_DECISION);
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
                new PlanningResult(plan, rawJson, 0, sessionId));
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
