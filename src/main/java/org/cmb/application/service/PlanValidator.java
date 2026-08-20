package org.cmb.application.service;
import org.cmb.common.exception.PlanValidationException;
import org.cmb.application.domain.PlannedTask;
import org.cmb.application.domain.CoordinatorPlanSpec;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import org.cmb.application.domain.ExpertDescriptor;
import org.cmb.application.domain.ProjectExpert;
import org.cmb.application.dto.ProjectView;
import org.springframework.stereotype.Component;

@Component
public class PlanValidator {

    private static final int MAX_DEPTH = 2;
    private final Validator validator;

    public PlanValidator(Validator validator) {
        this.validator = validator;
    }

    public void validate(
            CoordinatorPlanSpec plan,
            ProjectView project,
            List<ExpertDescriptor> experts) {
        Set<ConstraintViolation<CoordinatorPlanSpec>> violations = validator.validate(plan);
        if (!violations.isEmpty()) {
            throw new PlanValidationException("Plan constraints failed: " + violations);
        }
        Map<String, PlannedTask> tasks = indexTasks(plan);
        validateDependencies(tasks);
        validateDepth(tasks);
        validateCapabilities(tasks.values(), project, experts);
    }

    private Map<String, PlannedTask> indexTasks(CoordinatorPlanSpec plan) {
        Map<String, PlannedTask> tasks = new HashMap<>();
        for (PlannedTask task : plan.getTasks()) {
            if (tasks.put(task.getTaskKey(), task) != null) {
                throw new PlanValidationException(
                        "Duplicate task_key: " + task.getTaskKey());
            }
        }
        return tasks;
    }

    private void validateDependencies(Map<String, PlannedTask> tasks) {
        for (PlannedTask task : tasks.values()) {
            Set<String> unique = new HashSet<>();
            for (String dependency : task.getDependencies()) {
                if (!tasks.containsKey(dependency)) {
                    throw new PlanValidationException(
                            "Unknown dependency " + dependency + " for " + task.getTaskKey());
                }
                if (!unique.add(dependency)) {
                    throw new PlanValidationException(
                            "Duplicate dependency " + dependency + " for " + task.getTaskKey());
                }
            }
        }
    }

    private void validateDepth(Map<String, PlannedTask> tasks) {
        Map<String, Integer> depths = new HashMap<>();
        Set<String> visiting = new HashSet<>();
        for (String key : tasks.keySet()) {
            int depth = depth(key, tasks, depths, visiting);
            if (depth > MAX_DEPTH) {
                throw new PlanValidationException(
                        "Dependency depth exceeds " + MAX_DEPTH + " at " + key);
            }
        }
    }

    private int depth(
            String key,
            Map<String, PlannedTask> tasks,
            Map<String, Integer> depths,
            Set<String> visiting) {
        Integer known = depths.get(key);
        if (known != null) {
            return known;
        }
        if (!visiting.add(key)) {
            throw new PlanValidationException("Plan contains a dependency cycle at " + key);
        }
        int depth = 0;
        for (String dependency : tasks.get(key).getDependencies()) {
            depth = Math.max(depth, depth(dependency, tasks, depths, visiting) + 1);
        }
        visiting.remove(key);
        depths.put(key, depth);
        return depth;
    }

    private void validateCapabilities(
            Iterable<PlannedTask> tasks,
            ProjectView project,
            List<ExpertDescriptor> experts) {
        Set<String> allowedExperts = new HashSet<>();
        for (ProjectExpert expert : project.getExperts()) {
            if (expert.isEnabled()) {
                allowedExperts.add(expert.getExpertId());
            }
        }
        for (PlannedTask task : tasks) {
            boolean matched = false;
            for (ExpertDescriptor expert : experts) {
                boolean projectAllows = allowedExperts.isEmpty()
                        || allowedExperts.contains(expert.getExpertId());
                if (projectAllows && expert.isEnabled() && expert.isAvailable()
                        && expert.getCapabilities().containsAll(
                                task.getRequiredCapabilities())) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                throw new PlanValidationException(
                        "No project expert matches task " + task.getTaskKey()
                                + " capabilities " + task.getRequiredCapabilities());
            }
        }
    }
}
