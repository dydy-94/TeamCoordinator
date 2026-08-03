package org.cmb.teamcoordinator.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import org.cmb.teamcoordinator.intent.ExecutionMode;
import org.cmb.teamcoordinator.intent.TaskIntent;
import org.springframework.stereotype.Component;

@Component
public class MockPlanModelClient implements PlanModelClient {

    private final ObjectMapper objectMapper;

    public MockPlanModelClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String modelName() {
        return "mock-plan-v1";
    }

    @Override
    public String createPlan(String prompt, TaskIntent intent, int planVersion) {
        return write(build(intent, planVersion));
    }

    @Override
    public String repairPlan(
            String prompt, TaskIntent intent, String invalidOutput, int attempt) {
        return write(build(intent, 1));
    }

    private CoordinatorPlanSpec build(TaskIntent intent, int planVersion) {
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

    private String write(CoordinatorPlanSpec plan) {
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize mock plan.", ex);
        }
    }
}
