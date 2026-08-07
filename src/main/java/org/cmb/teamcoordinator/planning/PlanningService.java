package org.cmb.teamcoordinator.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.cmb.teamcoordinator.agentcore.AgentEvent;
import org.cmb.teamcoordinator.agentcore.ExpertRegistry;
import org.cmb.teamcoordinator.intent.TaskIntent;
import org.cmb.teamcoordinator.project.ProjectView;
import org.cmb.teamcoordinator.prompt.PromptService;
import org.cmb.teamcoordinator.prompt.RenderedPrompt;
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

    public PlanningService(
            PlanModelClient modelClient,
            PlanSchemaValidator schemaValidator,
            PlanValidator planValidator,
            ExpertRegistry expertRegistry,
            ObjectMapper objectMapper,
            PromptService prompts) {
        this.modelClient = modelClient;
        this.schemaValidator = schemaValidator;
        this.planValidator = planValidator;
        this.expertRegistry = expertRegistry;
        this.objectMapper = objectMapper;
        this.prompts = prompts;
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
        RenderedPrompt prompt = prompts.render(
                PromptService.COORDINATOR_PLANNING, context, "system", project.getId(),
                null, "plan:" + project.getId() + ":" + planVersion, agentId);
        PlanModelClient.PlanCallResult result = modelClient.createPlan(
                prompt.getContent(), intent, planVersion, agentId, eventSink);
        String output = result.getOutput();
        String planSessionId = result.getSessionId();
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt <= MAX_REPAIR_ATTEMPTS; attempt++) {
            try {
                CoordinatorPlanSpec plan = parse(output);
                if (plan.getPlanVersion() != planVersion) {
                    throw new PlanValidationException(
                            "Expected plan_version " + planVersion);
                }
                planValidator.validate(plan, project, expertRegistry.listExperts());
                return new PlanningResult(plan, output, attempt, planSessionId);
            } catch (RuntimeException ex) {
                lastFailure = ex;
                if (attempt < MAX_REPAIR_ATTEMPTS) {
                    PlanModelClient.PlanCallResult repaired = modelClient.repairPlan(
                            prompt.getContent(), intent, output, attempt + 1,
                            agentId, eventSink);
                    output = repaired.getOutput();
                    planSessionId = repaired.getSessionId();
                }
            }
        }
        throw new PlanValidationException(
                "Plan remained invalid after two repairs: " + lastFailure.getMessage());
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
