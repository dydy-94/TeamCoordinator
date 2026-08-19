package org.cmb.teamcoordinator.semantic;

import java.util.function.Consumer;
import org.cmb.teamcoordinator.agentcore.AgentEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock review: always passes. Keeps mock-mode execution paths unchanged
 * while the real client judges through AgentCore.
 */
@Component
@ConditionalOnProperty(prefix = "digital-team.agent-core", name = "mock-enabled",
        havingValue = "true", matchIfMissing = true)
public class MockSemanticCheckClient implements SemanticCheckClient {

    @Override
    public SemanticCheckResult check(
            String prompt, String agentId, Consumer<AgentEvent> eventSink) {
        return new SemanticCheckResult(true, true, "",
                "{\"consistent\":true,\"reason\":\"\"}");
    }
}
