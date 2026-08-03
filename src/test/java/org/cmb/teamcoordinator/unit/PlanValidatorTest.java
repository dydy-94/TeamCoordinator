package org.cmb.teamcoordinator.unit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import javax.validation.Validation;
import org.cmb.teamcoordinator.agentcore.ExpertDescriptor;
import org.cmb.teamcoordinator.planning.CoordinatorPlanSpec;
import org.cmb.teamcoordinator.planning.PlanValidationException;
import org.cmb.teamcoordinator.planning.PlanValidator;
import org.cmb.teamcoordinator.planning.PlannedTask;
import org.cmb.teamcoordinator.project.ProjectView;
import org.junit.jupiter.api.Test;

class PlanValidatorTest {

    private final PlanValidator validator = new PlanValidator(
            Validation.buildDefaultValidatorFactory().getValidator());
    private final ExpertDescriptor analyst = new ExpertDescriptor(
            "analyst", "Analyst", Collections.singletonList("analysis"));

    @Test
    void acceptsValidDagAndRejectsUnknownDependency() {
        CoordinatorPlanSpec valid = plan(
                task("analyze"),
                task("report", "analyze"));
        assertDoesNotThrow(() -> validator.validate(
                valid, new ProjectView(), Collections.singletonList(analyst)));

        CoordinatorPlanSpec invalid = plan(task("report", "missing"));
        assertThrows(PlanValidationException.class, () -> validator.validate(
                invalid, new ProjectView(), Collections.singletonList(analyst)));
    }

    @Test
    void rejectsDuplicateKeysCyclesAndDepthAboveTwo() {
        assertThrows(PlanValidationException.class, () -> validator.validate(
                plan(task("same"), task("same")),
                new ProjectView(), Collections.singletonList(analyst)));
        assertThrows(PlanValidationException.class, () -> validator.validate(
                plan(task("a", "b"), task("b", "a")),
                new ProjectView(), Collections.singletonList(analyst)));
        assertThrows(PlanValidationException.class, () -> validator.validate(
                plan(task("a"), task("b", "a"), task("c", "b"), task("d", "c")),
                new ProjectView(), Collections.singletonList(analyst)));
    }

    @Test
    void rejectsTaskWithoutMatchingProjectCapability() {
        PlannedTask task = task("write");
        task.setRequiredCapabilities(Collections.singletonList("writing"));
        assertThrows(PlanValidationException.class, () -> validator.validate(
                plan(task), new ProjectView(), Collections.singletonList(analyst)));
    }

    private CoordinatorPlanSpec plan(PlannedTask... tasks) {
        CoordinatorPlanSpec plan = new CoordinatorPlanSpec();
        plan.setTasks(Arrays.asList(tasks));
        return plan;
    }

    private PlannedTask task(String key, String... dependencies) {
        PlannedTask task = new PlannedTask();
        task.setTaskKey(key);
        task.setObjective("Objective " + key);
        task.setDependencies(Arrays.asList(dependencies));
        task.setExpectedOutput("Output " + key);
        task.setAcceptanceCriteria("Non-empty resultText");
        task.setRequiredCapabilities(Collections.singletonList("analysis"));
        return task;
    }
}
