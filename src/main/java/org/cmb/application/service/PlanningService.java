package org.cmb.application.service;
import org.cmb.application.service.PlanValidator;
import org.cmb.application.service.PlanSchemaValidator;
import org.cmb.common.exception.PlanValidationException;
import org.cmb.application.domain.PlanModelClient;
import org.cmb.application.domain.PlanningResult;
import org.cmb.application.domain.CoordinatorPlanSpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.cmb.application.domain.AgentEvent;
import org.cmb.application.domain.ExpertRegistry;
import org.cmb.application.service.OutputSchemaProvider;
import org.cmb.application.domain.TaskIntent;
import org.cmb.application.dto.ProjectView;
import org.cmb.application.service.PromptService;
import org.cmb.application.dto.RenderedPrompt;
import org.cmb.application.domain.SemanticCheckClient;
import org.springframework.stereotype.Service;

@Service
public class PlanningService {

    public static final int MAX_REPAIR_ATTEMPTS = 2;
    private final PlanModelClient modelClient;
    private final PlanSchemaValidator schemaValidator;
    private final PlanValidator planValidator;
    private final ExpertRegistry expertRegistry;
    private final ObjectMapper objectMapper;
    private final PromptService prompts;
    private final OutputSchemaProvider outputSchemas;
    private final SemanticCheckClient semanticChecks;

    public PlanningService(
            PlanModelClient modelClient,
            PlanSchemaValidator schemaValidator,
            PlanValidator planValidator,
            ExpertRegistry expertRegistry,
            ObjectMapper objectMapper,
            PromptService prompts,
            OutputSchemaProvider outputSchemas,
            SemanticCheckClient semanticChecks) {
        this.modelClient = modelClient;
        this.schemaValidator = schemaValidator;
        this.planValidator = planValidator;
        this.expertRegistry = expertRegistry;
        this.objectMapper = objectMapper;
        this.prompts = prompts;
        this.outputSchemas = outputSchemas;
        this.semanticChecks = semanticChecks;
    }

    public PlanningResult createPlan(
            TaskIntent intent, ProjectView project, int planVersion,
            String agentId, Consumer<AgentEvent> eventSink) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("projectName", project.getName());
        context.put("projectDescription", project.getDescription());
        context.put("taskIntent", intent);
        context.put("availableExperts", project.getExperts());
        context.put("planVersion", planVersion);
        String invocationKey = "plan:" + project.getId() + ":" + planVersion;
        // Inject the full plan JSON Schema so the output contract is explicit
        // in the prompt instead of relying on agent-side configuration.
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("output_schema", outputSchemas.planSchema());
        RenderedPrompt prompt = prompts.render(
                PromptService.COORDINATOR_PLANNING, context, variables, "system",
                project.getId(), null, invocationKey, agentId);
        PlanModelClient.PlanCallResult result = modelClient.createPlan(
                prompt.getContent(), intent, planVersion, agentId, invocationKey, eventSink);
        String output = result.getOutput();
        String planSessionId = result.getSessionId();
        long lastSequence = result.getLastSequence();
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt <= MAX_REPAIR_ATTEMPTS; attempt++) {
            // Default repair feedback: the invalid plan itself. Replaced below
            // when a semantic review rejected an otherwise valid plan.
            String invalidOutput = output;
            try {
                CoordinatorPlanSpec plan = parse(output);
                if (plan.getPlanVersion() != planVersion) {
                    throw new PlanValidationException(
                            "Expected plan_version " + planVersion);
                }
                planValidator.validate(plan, project, expertRegistry.listExperts());
                SemanticCheckClient.SemanticCheckResult check = semanticChecks.check(
                        renderPlanCheck(intent, project, output, agentId),
                        agentId, eventSink);
                if (check.isConclusive() && !check.isConsistent()) {
                    invalidOutput = "Semantic review rejected the plan: "
                            + check.getReason() + "\nRejected plan:\n" + output;
                    throw new PlanValidationException(
                            "Semantic check failed: " + check.getReason());
                }
                return new PlanningResult(plan, output, attempt, planSessionId);
            } catch (RuntimeException ex) {
                lastFailure = ex;
                if (attempt < MAX_REPAIR_ATTEMPTS) {
                    PlanModelClient.PlanCallResult repaired = modelClient.repairPlan(
                            prompt.getContent(), intent, invalidOutput,
                            planSessionId, lastSequence, planVersion, attempt + 1,
                            agentId, invocationKey, eventSink);
                    output = repaired.getOutput();
                    planSessionId = repaired.getSessionId();
                    lastSequence = repaired.getLastSequence();
                }
            }
        }
        throw new PlanValidationException(
                "Plan remained invalid after two repairs: " + lastFailure.getMessage());
    }

    /**
     * Render the second-pass review prompt that judges whether the generated
     * plan actually serves the task intent.
     */
    private String renderPlanCheck(
            TaskIntent intent, ProjectView project, String planJson, String agentId) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("projectName", project.getName());
        context.put("projectDescription", project.getDescription());
        context.put("taskIntent", intent);
        context.put("planJson", planJson);
        RenderedPrompt prompt = prompts.render(
                PromptService.COORDINATOR_PLAN_CHECK, context, "system", project.getId(),
                null, "plan:" + project.getId() + ":check", agentId);
        return prompt.getContent();
    }

    private CoordinatorPlanSpec parse(String output) {
        try {
            JsonNode node = objectMapper.readTree(output);
            schemaValidator.validate(node);
            return objectMapper.treeToValue(node, CoordinatorPlanSpec.class);
        } catch (IOException ex) {
            throw new PlanValidationException("Plan output is not valid JSON.");
        }
    }

}
