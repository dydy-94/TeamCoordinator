package org.cmb.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.cmb.application.domain.ExpertDescriptor;
import org.cmb.application.domain.ExpertRegistry;
import org.cmb.infrastructure.persistent.ExecutionRepository;
import org.cmb.application.service.ExpertSelector;
import org.cmb.application.dto.ProjectView;
import org.junit.jupiter.api.Test;

class ExpertSelectorTest {

    private static final ExpertDescriptor ANALYSIS = new ExpertDescriptor(
            "expert-analysis", "Analysis Expert", Arrays.asList("analysis"));
    private static final ExpertDescriptor WRITING = new ExpertDescriptor(
            "expert-writing", "Writing Expert", Arrays.asList("writing"));

    private static ExecutionRepository loadRepository(Map<String, Integer> loads) {
        return new ExecutionRepository(null, new ObjectMapper()) {
            @Override
            public int activeTaskCount(String expertId) {
                return loads.getOrDefault(expertId, 0);
            }
        };
    }

    private static ExpertRegistry registryOf(ExpertDescriptor... experts) {
        List<ExpertDescriptor> list = Arrays.asList(experts);
        return () -> list;
    }

    private static ProjectView project() {
        ProjectView project = new ProjectView();
        project.setExperts(Collections.emptyList());
        return project;
    }

    @Test
    void picksLeastLoadedMatchingExpert() {
        Map<String, Integer> loads = new HashMap<>();
        loads.put("expert-analysis", 1);
        loads.put("expert-writing", 0);
        ExpertSelector selector = new ExpertSelector(
                registryOf(ANALYSIS, WRITING), loadRepository(loads));
        // Both match (analysis via capabilities, writing only for the
        // empty-capabilities case below), least loaded wins.
        assertEquals("expert-analysis",
                selector.select(project(), Collections.singletonList("analysis")));
        assertEquals("expert-writing", selector.select(project(), Collections.emptyList()));
    }

    @Test
    void returnsNullInsteadOfThrowingWhenNoCandidateAvailable() {
        Map<String, Integer> atLimit = new HashMap<>();
        atLimit.put("expert-analysis", 2);
        ExpertSelector selector = new ExpertSelector(
                registryOf(ANALYSIS), loadRepository(atLimit));
        // Load equals the concurrency limit -> retryable null, not an exception.
        assertNull(selector.select(project(), Collections.singletonList("analysis")));

        ExpertDescriptor busy = new ExpertDescriptor(
                "expert-writing", "Writing Expert", Arrays.asList("writing"));
        busy.setAvailable(false);
        ExpertSelector unavailable = new ExpertSelector(
                registryOf(busy), loadRepository(new HashMap<>()));
        assertNull(unavailable.select(project(), Collections.singletonList("writing")));
    }
}
