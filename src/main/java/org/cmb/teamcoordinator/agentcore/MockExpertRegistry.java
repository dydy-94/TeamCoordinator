package org.cmb.teamcoordinator.agentcore;
import org.cmb.application.domain.ExpertRegistry;
import org.cmb.application.domain.ExpertDescriptor;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "digital-team.agent-core", name = "mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockExpertRegistry implements ExpertRegistry {

    @Override
    public List<ExpertDescriptor> listExperts() {
        return Arrays.asList(
                new ExpertDescriptor("expert-analysis", "Analysis Expert", Arrays.asList("analysis", "risk_review")),
                new ExpertDescriptor("expert-writing", "Writing Expert", Arrays.asList("writing", "report")),
                new ExpertDescriptor("expert-file", "File Expert", Arrays.asList("file_processing", "artifact")),
                new ExpertDescriptor("expert-ui", "UI Design Expert", Arrays.asList("ui_design", "frontend")),
                new ExpertDescriptor("expert-backend", "Backend Expert", Arrays.asList("backend", "api_design"))
        );
    }
}
