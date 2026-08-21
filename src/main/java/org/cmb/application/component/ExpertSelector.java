package org.cmb.application.component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.cmb.application.domain.ExpertDescriptor;
import org.cmb.application.service.ExpertRegistry;
import org.cmb.infrastructure.persistent.ExecutionRepository;
import org.cmb.application.domain.entity.ProjectExpertDO;
import org.cmb.application.dto.ProjectView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ExpertSelector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpertSelector.class);

    private final ExpertRegistry registry;
    private final ExecutionRepository repository;

    public ExpertSelector(ExpertRegistry registry, ExecutionRepository repository) {
        this.registry = registry;
        this.repository = repository;
    }

    /**
     * Pick the least-loaded matching expert, or return {@code null} when no
     * candidate is currently available (busy at concurrency limit, disabled,
     * or temporarily unavailable). A null result is a retryable condition —
     * callers must skip the round instead of treating it as a failure.
     */
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
            List<String> expertIds = new ArrayList<>();
            for (ProjectExpertDO pe : project.getExperts()) {
                expertIds.add(pe.getExpertId() + (pe.isEnabled() ? ":on" : ":off"));
            }
            LOGGER.warn("No available expert matches {} (retryable). Project experts: {}. Registry: {}",
                    capabilities, expertIds, describeRegistry());
            return null;
        }
        return candidates.get(0).expertId;
    }

    private String describeRegistry() {
        StringBuilder builder = new StringBuilder();
        for (ExpertDescriptor expert : registry.listExperts()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(expert.getExpertId())
                    .append(expert.getCapabilities())
                    .append(" enabled=").append(expert.isEnabled())
                    .append(" avail=").append(expert.isAvailable());
        }
        return builder.toString();
    }

    private boolean projectAllows(ProjectView project, String expertId) {
        if (project.getExperts().isEmpty()) {
            return true;
        }
        for (ProjectExpertDO expert : project.getExperts()) {
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
