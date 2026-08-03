package org.cmb.teamcoordinator.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.cmb.teamcoordinator.agentcore.AgentRunEvent;
import org.cmb.teamcoordinator.agentcore.AgentRunEventNormalizer;
import org.junit.jupiter.api.Test;

class AgentRunEventNormalizerTest {

    @Test
    void sortsAndDeduplicatesEventsBySequence() {
        AgentRunEvent second = new AgentRunEvent("run-1", 2, "RUN_PROGRESS", "RUNNING", "progress");
        AgentRunEvent first = new AgentRunEvent("run-1", 1, "RUN_ACCEPTED", "ACCEPTED", "accepted");
        AgentRunEvent duplicate = new AgentRunEvent("run-1", 2, "RUN_PROGRESS", "RUNNING", "duplicate");

        List<AgentRunEvent> normalized =
                new AgentRunEventNormalizer().normalize(Arrays.asList(second, first, duplicate));

        assertEquals(2, normalized.size());
        assertEquals(1, normalized.get(0).getSequence());
        assertEquals(2, normalized.get(1).getSequence());
    }
}
