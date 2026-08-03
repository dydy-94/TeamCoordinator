package org.cmb.teamcoordinator.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.cmb.teamcoordinator.agentcore.ExpertRegistry;
import org.cmb.teamcoordinator.intent.TaskIntent;
import org.cmb.teamcoordinator.project.ProjectView;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Service
public class PlanningService {

    public static final int MAX_REPAIR_ATTEMPTS = 2;
    private final PlanModelClient modelClient;
    private final PlanSchemaValidator schemaValidator;
    private final PlanValidator planValidator;
    private final ExpertRegistry expertRegistry;
    private final ObjectMapper objectMapper;
    private final String prompt;

    public PlanningService(
            PlanModelClient modelClient,
            PlanSchemaValidator schemaValidator,
            PlanValidator planValidator,
            ExpertRegistry expertRegistry,
            ObjectMapper objectMapper) {
        this.modelClient = modelClient;
        this.schemaValidator = schemaValidator;
        this.planValidator = planValidator;
        this.expertRegistry = expertRegistry;
        this.objectMapper = objectMapper;
        this.prompt = loadPrompt();
    }

    public PlanningResult createPlan(
            TaskIntent intent, ProjectView project, int planVersion) {
        String output = modelClient.createPlan(prompt, intent, planVersion);
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt <= MAX_REPAIR_ATTEMPTS; attempt++) {
            try {
                CoordinatorPlanSpec plan = parse(output);
                if (plan.getPlanVersion() != planVersion) {
                    throw new PlanValidationException(
                            "Expected plan_version " + planVersion);
                }
                planValidator.validate(plan, project, expertRegistry.listExperts());
                return new PlanningResult(plan, output, attempt);
            } catch (RuntimeException ex) {
                lastFailure = ex;
                if (attempt < MAX_REPAIR_ATTEMPTS) {
                    output = modelClient.repairPlan(
                            prompt, intent, output, attempt + 1);
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

    private String loadPrompt() {
        try {
            InputStream input = getClass().getResourceAsStream(
                    "/coordinator/plan-prompt-v1.txt");
            if (input == null) {
                throw new IllegalStateException("Plan prompt is missing.");
            }
            return StreamUtils.copyToString(input, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not load plan prompt.", ex);
        }
    }
}
