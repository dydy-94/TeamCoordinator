package org.cmb.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.cmb.application.domain.AgentEvent;
import org.cmb.application.domain.AgentRunRequest;
import org.cmb.application.domain.AgentRunResponse;
import org.cmb.infrastructure.remoteaccess.MockAgentCoreAdapter;
import org.cmb.common.config.DigitalTeamProperties;
import org.junit.jupiter.api.Test;

class MockAgentCoreAdapterTest {

    @Test
    void submitRunCreatesReplayableEvents() {
        MockAgentCoreAdapter adapter = new MockAgentCoreAdapter(new DigitalTeamProperties());
        AgentRunRequest request = new AgentRunRequest();
        request.setTaskText("analyze this plan");

        AgentRunResponse response = adapter.submitRun("expert-analysis", request);
        List<AgentEvent> events = adapter.streamEvents(
                "expert-analysis", response.getSessionId(), 0L);

        assertEquals("ACCEPTED", response.getStatus());
        assertFalse(events.isEmpty());
        assertEquals("taskInQueue", events.get(0).getType());
        assertEquals("end", events.get(events.size() - 1).getType());
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
        assertEquals("error", adapter.getRunStatus(
                "expert-analysis", failedRun.getSessionId()).getType());

        AgentRunRequest timedOut = request("please timeout");
        AgentRunResponse timedOutRun = adapter.submitRun("expert-analysis", timedOut);
        assertEquals("error", adapter.getRunStatus(
                "expert-analysis", timedOutRun.getSessionId()).getType());

        AgentRunResponse cancelledRun =
                adapter.submitRun("expert-analysis", request("long task"));
        adapter.cancelRun("expert-analysis", cancelledRun.getSessionId());
        assertEquals("RUN_CANCELLED",
                adapter.getRunStatus(
                        "expert-analysis", cancelledRun.getSessionId()).getType());
    }

    private AgentRunRequest request(String taskText) {
        AgentRunRequest request = new AgentRunRequest();
        request.setTaskText(taskText);
        return request;
    }
}
