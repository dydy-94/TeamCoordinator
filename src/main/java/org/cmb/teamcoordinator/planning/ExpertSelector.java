package org.cmb.teamcoordinator.planning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.cmb.teamcoordinator.agentcore.ExpertDescriptor;
import org.cmb.teamcoordinator.agentcore.ExpertRegistry;
import org.cmb.teamcoordinator.execution.ExecutionRepository;
import org.cmb.teamcoordinator.project.ProjectExpert;
import org.cmb.teamcoordinator.project.ProjectView;
import org.springframework.stereotype.Component;

@Component
public class ExpertSelector {

    private final ExpertRegistry registry;
    private final ExecutionRepository repository;

    public ExpertSelector(ExpertRegistry registry, ExecutionRepository repository) {
        this.registry = registry;
        this.repository = repository;
    }

    public String select(ProjectView project, List<String> capabilities) {
        List<Candidate> candidates = new ArrayList<>();
        for (ExpertDescriptor expert : registry.listExperts()) {
            if (expert.isEnabled() && expert.isAvailable()
                    && projectAllows(project, expert.getExpertId())
                    && expert.getCapabilities().containsAll(capabilities)) {
                int load = repository.activeTaskCount(expert.getExpertId());
                if (load < expert.getConcurrencyLimit()) {
                    candidates.add(new Candidate(expert.getExpertId(), load));
                }
            }
        }
        candidates.sort(Comparator.comparingInt(Candidate::getLoad)
                .thenComparing(Candidate::getExpertId));
        if (candidates.isEmpty()) {
            throw new PlanValidationException(
                    "No available expert matches capabilities " + capabilities);
        }
        return candidates.get(0).expertId;
    }

    private boolean projectAllows(ProjectView project, String expertId) {
        if (project.getExperts().isEmpty()) {
            return true;
        }
        for (ProjectExpert expert : project.getExperts()) {
            if (expert.isEnabled() && expertId.equals(expert.getExpertId())) {
                return true;
            }
        }
        return false;
    }

    private static final class Candidate {
        private final String expertId;
        private final int load;

        private Candidate(String expertId, int load) {
            this.expertId = expertId;
            this.load = load;
        }

        private String getExpertId() { return expertId; }
        private int getLoad() { return load; }
    }
}
