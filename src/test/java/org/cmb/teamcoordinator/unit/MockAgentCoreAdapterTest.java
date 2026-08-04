package org.cmb.teamcoordinator.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.cmb.teamcoordinator.agentcore.AgentRunEvent;
import org.cmb.teamcoordinator.agentcore.AgentRunRequest;
import org.cmb.teamcoordinator.agentcore.AgentRunResponse;
import org.cmb.teamcoordinator.agentcore.MockAgentCoreAdapter;
import org.cmb.teamcoordinator.config.DigitalTeamProperties;
import org.junit.jupiter.api.Test;

class MockAgentCoreAdapterTest {

    @Test
    void submitRunCreatesReplayableEvents() {
        MockAgentCoreAdapter adapter = new MockAgentCoreAdapter(new DigitalTeamProperties());
        AgentRunRequest request = new AgentRunRequest();
        request.setTaskText("analyze this plan");

        AgentRunResponse response = adapter.submitRun("expert-analysis", request);
        List<AgentRunEvent> events = adapter.streamEvents(response.getSessionId(), 0L);

        assertEquals("ACCEPTED", response.getStatus());
        assertFalse(events.isEmpty());
        assertEquals("RUN_ACCEPTED", events.get(0).getType());
        assertEquals("RUN_SUCCEEDED", events.get(events.size() - 1).getType());
    }

    @Test
    void separateSubmissionsCreateSeparateSessions() {
        MockAgentCoreAdapter adapter = new MockAgentCoreAdapter(new DigitalTeamProperties());
        AgentRunRequest request = new AgentRunRequest();
        request.setTaskText("write a report");

        AgentRunResponse first = adapter.submitRun("expert-writing", request);
        AgentRunResponse different = adapter.submitRun("expert-writing", request);

        assertNotEquals(first.getSessionId(), different.getSessionId());
    }

    @Test
    void exposesFailureTimeoutAndCancellationFinalStates() {
        MockAgentCoreAdapter adapter = new MockAgentCoreAdapter(new DigitalTeamProperties());

        AgentRunRequest failed = request("please fail");
        AgentRunResponse failedRun = adapter.submitRun("expert-analysis", failed);
        assertEquals("FAILED", adapter.getRunStatus(failedRun.getSessionId()).getStatus());

        AgentRunRequest timedOut = request("please timeout");
        AgentRunResponse timedOutRun = adapter.submitRun("expert-analysis", timedOut);
        assertEquals("TIMED_OUT", adapter.getRunStatus(timedOutRun.getSessionId()).getStatus());

        AgentRunResponse cancelledRun =
                adapter.submitRun("expert-analysis", request("long task"));
        adapter.cancelRun(cancelledRun.getSessionId());
        assertEquals("CANCELLED", adapter.getRunStatus(cancelledRun.getSessionId()).getStatus());
    }

    private AgentRunRequest request(String taskText) {
        AgentRunRequest request = new AgentRunRequest();
        request.setTaskText(taskText);
        return request;
    }
}
