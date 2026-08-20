package org.cmb.infrastructure.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.cmb.application.domain.AgentEvent;
import org.cmb.application.domain.ProjectEvent;
import org.cmb.common.config.DigitalTeamProperties;
import org.cmb.infrastructure.persistent.MessageEventRepository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.cmb.infrastructure.remoteaccess.MockAgentCoreAdapter;
import org.junit.jupiter.api.Test;

/**
 * Covers the SSE lifecycle sweep: heartbeat frames keep active connections
 * alive, connections without recent activity receive an inactive event and
 * are closed. The frame-sending methods are overridden in a test subclass
 * so no real emitter IO is involved.
 */
class ProjectEventStreamHubTest {

    private static MessageEventRepository stubRepository() {
        return new MessageEventRepository(null, new ObjectMapper());
    }

    private static DigitalTeamProperties properties(int inactivityTimeoutMin) {
        DigitalTeamProperties props = new DigitalTeamProperties();
        props.getEvents().setInactivityTimeoutMin(inactivityTimeoutMin);
        return props;
    }

    /** Captures sweep calls instead of touching a real SseEmitter. */
    private static final class RecordingHub extends ProjectEventStreamHub {
        final AtomicInteger heartbeats = new AtomicInteger();
        final AtomicInteger inactive = new AtomicInteger();

        RecordingHub(DigitalTeamProperties properties) {
            super(stubRepository(),
                    new MockAgentCoreAdapter(new DigitalTeamProperties()),
                    properties);
        }

        @Override
        protected void sendHeartbeatFrame(Subscriber subscriber) {
            heartbeats.incrementAndGet();
        }

        @Override
        protected void sendInactiveFrame(Subscriber subscriber, long now) {
            inactive.incrementAndGet();
        }
    }

    @Test
    void activeConnectionsReceiveHeartbeatsAndStayOpen() {
        RecordingHub hub = new RecordingHub(properties(30));
        hub.subscribe("tenant", "project", "task", 0L, Collections::emptyList);
        assertEquals(1, hub.activeSubscriberCount());

        hub.sendHeartbeats();

        assertEquals(1, hub.heartbeats.get(), "active connection must get a heartbeat");
        assertEquals(0, hub.inactive.get());
        assertEquals(1, hub.activeSubscriberCount(), "active connection must stay open");
    }

    @Test
    void agentReplayIsWindowedByMarkerStartSequence() {
        RecordingHub hub = new RecordingHub(properties(30));

        java.util.List<AgentEvent> events = new java.util.ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            AgentEvent ae = AgentEvent.of("liveStatus");
            ae.setSequence(i);
            events.add(ae);
        }

        // 第一条消息的 MARKER（startSequence=0）：完整下发 1..6
        assertEquals(6, hub.filterAgentReplay(events, 0L).size());
        // 第二条消息的 MARKER（startSequence=6）：只下发本消息的 7..12
        java.util.List<AgentEvent> session = new java.util.ArrayList<>(events);
        for (int i = 7; i <= 12; i++) {
            AgentEvent ae = AgentEvent.of("thinkingDelta");
            ae.setSequence(i);
            session.add(ae);
        }
        assertEquals(6, hub.filterAgentReplay(session, 6L).size());
    }

    @Test
    void liveEventsBypassSequenceDedupWhilePersistedEventsDoNot() {
        RecordingHub hub = new RecordingHub(properties(30));
        ProjectEventStreamHub.Subscriber subscriber =
                new ProjectEventStreamHub.Subscriber(new SseEmitter(0L), 10L);

        ProjectEvent persisted = new ProjectEvent();
        persisted.setSequence(5);
        ProjectEvent live = new ProjectEvent();
        live.setSequence(1);
        live.setLiveOnly(true);

        assertTrue(hub.shouldDeliver(subscriber, live),
                "live agent events must never be deduped against DB sequences");
        assertEquals(false, hub.shouldDeliver(subscriber, persisted),
                "persisted events must respect the replay cursor");
    }

    @Test
    void inactiveConnectionsAreClosedWithInactiveEvent() throws Exception {
        RecordingHub hub = new RecordingHub(properties(0));
        hub.subscribe("tenant", "project", "task", 0L, Collections::emptyList);
        // The zero-minute timeout makes the fresh timestamp instantly stale.
        Thread.sleep(2);

        hub.sendHeartbeats();

        assertEquals(0, hub.heartbeats.get());
        assertTrue(hub.inactive.get() >= 1, "inactive connection must be notified");
        assertEquals(0, hub.activeSubscriberCount(), "inactive connection must be closed");
    }
}
