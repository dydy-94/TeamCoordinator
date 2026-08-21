package org.cmb.infrastructure.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.cmb.application.domain.AgentEvent;
import org.cmb.application.domain.AgentRunRequest;
import org.cmb.application.domain.AgentRunResponse;
import org.cmb.application.domain.entity.ProjectEventDO;
import org.cmb.common.config.DigitalTeamProperties;
import org.cmb.common.enums.ProjectEventType;
import org.cmb.infrastructure.persistent.MessageEventRepository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.cmb.application.service.impl.MockAgentCoreAdapter;
import org.junit.jupiter.api.Test;

/**
 * Covers the SSE lifecycle sweep: heartbeat frames keep active connections
 * alive, connections without recent activity receive an inactive event and
 * are closed. The frame-sending methods are overridden in a test subclass
 * so no real emitter IO is involved.
 */
class ProjectEventStreamHubTest {

    private static MessageEventRepository stubRepository() {
        return new MessageEventRepository(null, null, null, null, new ObjectMapper());
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

    /** Captures agentStart/agentEnd boundary frames instead of real IO. */
    private static final class BoundaryRecordingHub extends ProjectEventStreamHub {
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger ends = new AtomicInteger();

        BoundaryRecordingHub(DigitalTeamProperties properties, MockAgentCoreAdapter agentCore) {
            super(stubRepository(), agentCore, properties);
        }

        @Override
        protected void sendAgentBoundary(Subscriber subscriber, long markerSequence,
                String boundaryType, String agentId, String sessionId) {
            if ("agentStart".equals(boundaryType)) {
                starts.incrementAndGet();
            } else {
                ends.incrementAndGet();
            }
        }
    }

    private static ProjectEventDO marker(String sessionId, String expertId,
            long markerSequence, long startSequence) {
        ProjectEventDO marker = new ProjectEventDO();
        marker.setSequence(markerSequence);
        marker.setType(ProjectEventType.AGENT_RUN_MARKER);
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("sessionId", sessionId);
        payload.put("expertId", expertId);
        payload.put("startSequence", startSequence);
        marker.setPayload(payload);
        return marker;
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

        // 第一条消息的 MARKER（start=0，end=6）：只下发 1..6
        assertEquals(6, hub.filterAgentReplay(events, 0L, 6L).size());
        // 第二条消息的 MARKER（start=6，end=无界）：只下发 7..12
        java.util.List<AgentEvent> session = new java.util.ArrayList<>(events);
        for (int i = 7; i <= 12; i++) {
            AgentEvent ae = AgentEvent.of("thinkingDelta");
            ae.setSequence(i);
            session.add(ae);
        }
        assertEquals(6, hub.filterAgentReplay(session, 6L, Long.MAX_VALUE).size());
        // 无上界 + floor=0（旧数据游标兜底语义）：整段下发
        assertEquals(12, hub.filterAgentReplay(session, 0L, Long.MAX_VALUE).size());
        // 上界生效：floor=0、end=6 只下发 1..6
        assertEquals(6, hub.filterAgentReplay(session, 0L, 6L).size());
    }

    @Test
    void liveEventsBypassSequenceDedupWhilePersistedEventsDoNot() {
        RecordingHub hub = new RecordingHub(properties(30));
        ProjectEventStreamHub.Subscriber subscriber =
                new ProjectEventStreamHub.Subscriber(new SseEmitter(0L), 10L);

        ProjectEventDO persisted = new ProjectEventDO();
        persisted.setSequence(5);
        ProjectEventDO live = new ProjectEventDO();
        live.setSequence(1);
        live.setLiveOnly(true);

        assertTrue(hub.shouldDeliver(subscriber, live),
                "live agent events must never be deduped against DB sequences");
        assertEquals(false, hub.shouldDeliver(subscriber, persisted),
                "persisted events must respect the replay cursor");
    }

    @Test
    void liveEventsAreDedupedByAgentSessionCursor() {
        RecordingHub hub = new RecordingHub(properties(30));
        ProjectEventStreamHub.Subscriber subscriber =
                new ProjectEventStreamHub.Subscriber(new SseEmitter(0L), 10L);

        ProjectEventDO live = new ProjectEventDO();
        live.setSequence(99L); // live 事件的持久化命名空间序列与去重无关
        live.setLiveOnly(true);
        AgentEvent seen = AgentEvent.of("liveStatus");
        seen.setSessionId("session-a");
        seen.setSequence(5L);
        live.setAgentEvent(seen);

        assertTrue(hub.shouldDeliver(subscriber, live));
        hub.recordDelivered(subscriber, live);

        // 同会话更小序列（MARKER 回放先到、live 后到）被去重
        ProjectEventDO dup = new ProjectEventDO();
        dup.setLiveOnly(true);
        dup.setAgentEvent(agentWithSession("session-a", 4L));
        assertEquals(false, hub.shouldDeliver(subscriber, dup));

        // 同会话更大序列正常投递
        ProjectEventDO next = new ProjectEventDO();
        next.setLiveOnly(true);
        next.setAgentEvent(agentWithSession("session-a", 6L));
        assertTrue(hub.shouldDeliver(subscriber, next));

        // 不同会话互不影响
        ProjectEventDO other = new ProjectEventDO();
        other.setLiveOnly(true);
        other.setAgentEvent(agentWithSession("session-b", 1L));
        assertTrue(hub.shouldDeliver(subscriber, other));

        // 无会话信息的 live 事件（合成通知）一律投递
        ProjectEventDO bare = new ProjectEventDO();
        bare.setLiveOnly(true);
        bare.setAgentEvent(AgentEvent.of("liveStatus"));
        assertTrue(hub.shouldDeliver(subscriber, bare));

        // live 投递不推进持久化游标：序列 11 的持久化事件仍可投递
        ProjectEventDO persisted = new ProjectEventDO();
        persisted.setSequence(11L);
        assertTrue(hub.shouldDeliver(subscriber, persisted));
        hub.recordDelivered(subscriber, persisted);
        assertEquals(false, hub.shouldDeliver(subscriber, persisted),
                "持久化事件投递一次后必须被持久化游标去重");
    }

    @Test
    void replayFloorIsBoundedBySessionCursor() {
        RecordingHub hub = new RecordingHub(properties(30));
        // live 直转未推进游标（跨实例订阅者）：以 MARKER startSequence 为准
        assertEquals(24L, hub.replayFloor(24L, 0L));
        // live 直转已推进游标（同实例订阅者已收到）：不再重复下发
        assertEquals(30L, hub.replayFloor(24L, 30L));
        // 旧 MARKER 无 startSequence：回退会话游标
        assertEquals(0L, hub.replayFloor(null, 0L));
        assertEquals(30L, hub.replayFloor(null, 30L));
    }

    private static AgentEvent agentWithSession(String sessionId, long sequence) {
        AgentEvent event = AgentEvent.of("liveStatus");
        event.setSessionId(sessionId);
        event.setSequence(sequence);
        return event;
    }

    @Test
    void agentReplayBurstIsBracketedByStartAndEndBoundaries() throws Exception {
        MockAgentCoreAdapter agentCore = new MockAgentCoreAdapter(new DigitalTeamProperties());
        BoundaryRecordingHub hub = new BoundaryRecordingHub(properties(30), agentCore);
        AgentRunRequest request = new AgentRunRequest();
        request.setTaskText("produce a report");
        AgentRunResponse run = agentCore.submitRun("expert-analysis", request);

        hub.subscribe("tenant", "project", "task", 0L,
                () -> Collections.singletonList(marker(
                        run.getSessionId(), "expert-analysis", 8L, 0L)));

        assertEquals(1, hub.starts.get(), "one agentStart per replay burst");
        assertEquals(1, hub.ends.get(), "one agentEnd per replay burst");
    }

    @Test
    void emptyReplayWindowEmitsNoBoundaries() throws Exception {
        MockAgentCoreAdapter agentCore = new MockAgentCoreAdapter(new DigitalTeamProperties());
        BoundaryRecordingHub hub = new BoundaryRecordingHub(properties(30), agentCore);
        AgentRunRequest request = new AgentRunRequest();
        request.setTaskText("produce a report");
        AgentRunResponse run = agentCore.submitRun("expert-analysis", request);

        hub.subscribe("tenant", "project", "task", 0L,
                () -> Collections.singletonList(marker(
                        run.getSessionId(), "expert-analysis", 8L, 9999L)));

        assertEquals(0, hub.starts.get(), "empty window must not emit agentStart");
        assertEquals(0, hub.ends.get(), "empty window must not emit agentEnd");
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
