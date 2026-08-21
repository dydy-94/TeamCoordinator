package org.cmb.infrastructure.worker;
import org.cmb.common.enums.ProjectEventType;
import org.cmb.application.domain.ProjectEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.cmb.common.config.DigitalTeamProperties;
import org.cmb.infrastructure.persistent.MessageEventRepository;
import org.cmb.application.domain.AgentCoreAdapter;
import org.cmb.application.domain.AgentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class ProjectEventStreamHub {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectEventStreamHub.class);
    private static final int POLL_BATCH_SIZE = 200;

    private final MessageEventRepository repository;
    private final AgentCoreAdapter agentCore;
    private final DigitalTeamProperties properties;
    private final Map<TaskKey, CopyOnWriteArrayList<Subscriber>> subscribers =
            new ConcurrentHashMap<>();

    public ProjectEventStreamHub(MessageEventRepository repository,
                                  AgentCoreAdapter agentCore,
                                  DigitalTeamProperties properties) {
        this.repository = repository;
        this.agentCore = agentCore;
        this.properties = properties;
    }

    public SseEmitter subscribe(
            String tenantId,
            String projectId,
            String taskId,
            long afterSequence,
            Supplier<List<ProjectEvent>> replaySupplier) {
        TaskKey key = new TaskKey(tenantId, projectId, taskId);
        // 无固定超时：连接生命周期由心跳扫描的不活跃判定接管。
        SseEmitter emitter = new SseEmitter(0L);
        Subscriber subscriber = new Subscriber(emitter, afterSequence);
        CopyOnWriteArrayList<Subscriber> projectSubscribers =
                subscribers.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>());
        projectSubscribers.add(subscriber);
        emitter.onCompletion(() -> remove(key, projectSubscribers, subscriber));
        emitter.onTimeout(() -> remove(key, projectSubscribers, subscriber));
        emitter.onError(error -> remove(key, projectSubscribers, subscriber));
        try {
            List<ProjectEvent> replayEvents = replaySupplier.get();
            Map<String, List<long[]>> nextStarts = markerStartBounds(replayEvents);
            synchronized (subscriber) {
                for (ProjectEvent event : replayEvents) {
                    if (event.getType() == ProjectEventType.AGENT_RUN_MARKER
                            && event.getPayload() != null
                            && event.getPayload().has("sessionId")) {
                        String sessionId = event.getPayload().get("sessionId").asText();
                        Long start = startSequenceOrNull(event.getPayload());
                        Long end = start == null
                                ? null
                                : nextEndBound(nextStarts, sessionId, event.getSequence());
                        replayAgentEvents(subscriber,
                                stringFromPayload(event.getPayload(), "expertId"),
                                sessionId,
                                event.getSequence(),
                                start, end);
                    } else {
                        send(subscriber, event);
                    }
                }
            }
        } catch (IOException | RuntimeException ex) {
            projectSubscribers.remove(subscriber);
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    public void publish(
            String tenantId, String projectId, String taskId, ProjectEvent event) {
        CopyOnWriteArrayList<Subscriber> projectSubscribers =
                subscribers.get(new TaskKey(tenantId, projectId, taskId));
        if (projectSubscribers == null) {
            return;
        }
        for (Subscriber subscriber : projectSubscribers) {
            subscriber.lastActivityAt = System.currentTimeMillis();
            try {
                synchronized (subscriber) {
                    send(subscriber, event);
                }
            } catch (IOException ex) {
                projectSubscribers.remove(subscriber);
                subscriber.emitter.completeWithError(ex);
            }
        }
    }

    /**
     * 心跳扫描：每 heartbeat-interval-ms 给每个连接发 SSE 注释帧保活；
     * 超过 inactivity-timeout-min 没有活动的连接发送 inactive 事件后强制断开。
     */
    @Scheduled(fixedDelayString = "${digital-team.events.heartbeat-interval-ms:60000}")
    public void sendHeartbeats() {
        long now = System.currentTimeMillis();
        long timeoutMillis =
                properties.getEvents().getInactivityTimeoutMin() * 60_000L;
        for (Map.Entry<TaskKey, CopyOnWriteArrayList<Subscriber>> entry
                : subscribers.entrySet()) {
            for (Subscriber subscriber : entry.getValue()) {
                synchronized (subscriber) {
                    try {
                        if (now - subscriber.lastActivityAt > timeoutMillis) {
                            sendInactiveFrame(subscriber, now);
                            try {
                                subscriber.emitter.complete();
                            } catch (RuntimeException ignored) {
                                // already completed/closed
                            }
                            // 显式移除，不依赖 completion 回调（回调在真实
                            // MVC 环境下才会触发）。
                            entry.getValue().remove(subscriber);
                        } else {
                            sendHeartbeatFrame(subscriber);
                        }
                    } catch (IOException | RuntimeException ex) {
                        entry.getValue().remove(subscriber);
                        try {
                            subscriber.emitter.completeWithError(ex);
                        } catch (RuntimeException ignored) {
                            // already completed/closed
                        }
                    }
                }
            }
            if (entry.getValue().isEmpty()) {
                subscribers.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    /** 心跳帧：SSE 注释（":ping"），客户端静默忽略，仅用于保活。 */
    protected void sendHeartbeatFrame(Subscriber subscriber) throws IOException {
        subscriber.emitter.send(SseEmitter.event().comment("ping"));
    }

    /** 不活跃通知：命名事件 inactive，随后由调用方 complete()。 */
    protected void sendInactiveFrame(Subscriber subscriber, long now)
            throws IOException {
        subscriber.emitter.send(SseEmitter.event()
                .name("inactive")
                .data(java.util.Collections.singletonMap(
                        "reason", "no activity for the configured timeout")));
    }

    /** 当前活跃连接数（运维与测试用）。 */
    public int activeSubscriberCount() {
        int count = 0;
        for (CopyOnWriteArrayList<Subscriber> list : subscribers.values()) {
            count += list.size();
        }
        return count;
    }

    @Scheduled(fixedDelayString = "${digital-team.events.database-poll-interval-ms:500}")
    public void pollDatabaseEvents() {
        for (Map.Entry<TaskKey, CopyOnWriteArrayList<Subscriber>> entry
                : subscribers.entrySet()) {
            CopyOnWriteArrayList<Subscriber> projectSubscribers = entry.getValue();
            if (projectSubscribers.isEmpty()) {
                subscribers.remove(entry.getKey(), projectSubscribers);
                continue;
            }
            try {
                long afterSequence = minimumSequence(projectSubscribers);
                List<ProjectEvent> events = repository.findPublicEvents(
                        entry.getKey().tenantId,
                        entry.getKey().projectId,
                        entry.getKey().taskId,
                        afterSequence,
                        POLL_BATCH_SIZE);
                if (!events.isEmpty()) {
                    // 有内容流动即视为活跃：包括跨实例写入的用户消息。
                    long now = System.currentTimeMillis();
                    for (Subscriber subscriber : projectSubscribers) {
                        subscriber.lastActivityAt = now;
                    }
                }
                for (ProjectEvent event : events) {
                        if (event.getType() == ProjectEventType.AGENT_RUN_MARKER
                                && event.getPayload() != null
                                && event.getPayload().has("sessionId")) {
                            // Cross-instance: another Coordinator inserted a
                            // marker. Fetch agent events from AgentCore and
                            // push to each subscriber.
                            String sessionId =
                                    event.getPayload().get("sessionId").asText();
                            String expertId = stringFromPayload(
                                    event.getPayload(), "expertId");
                            Long startSequence =
                                    startSequenceOrNull(event.getPayload());
                            Long endBound = startSequence == null
                                    ? null
                                    : nextMarkerStart(
                                            entry.getKey().tenantId,
                                            entry.getKey().taskId,
                                            sessionId,
                                            event.getSequence());
                            for (Subscriber subscriber : projectSubscribers) {
                                synchronized (subscriber) {
                                    replayAgentEvents(subscriber, expertId,
                                            sessionId, event.getSequence(),
                                            startSequence, endBound);
                                }
                            }
                            continue;
                        }
                        publish(
                                entry.getKey().tenantId,
                                entry.getKey().projectId,
                                entry.getKey().taskId,
                                event);
                }
            } catch (RuntimeException | IOException ex) {
                LOGGER.warn(
                        "Could not poll project events for project {}.",
                        entry.getKey().projectId,
                        ex);
            }
        }
    }

    /**
     * Replay agent events from AgentCore for a marker encountered during
     * SSE replay. Each agent event gets its own SSE frame with the
     * agent event's type as the event name.
     */
    private void replayAgentEvents(Subscriber subscriber, String expertId,
                                    String sessionId, long markerSequence,
                                    Long startSequence, Long endSequence)
            throws IOException {
        List<AgentEvent> events = agentCore.streamEvents(expertId, sessionId, 0L);
        if (events == null || events.isEmpty()) {
            return;
        }
        events.sort(Comparator.comparingLong(AgentEvent::getSequence));
        // 窗口 (floor, end]：end 为同会话下一 MARKER 的 startSequence，
        // 保证只下发本消息的事件；旧 MARKER 无 start 时回退会话游标。
        // floor 同时受会话游标约束：live 直转已推进游标的部分（同实例
        // 订阅者已收到）不再重复下发；跨实例订阅者游标为 0，回放补发
        // 全部窗口。
        long floor = replayFloor(startSequence,
                subscriber.agentCursors.getOrDefault(sessionId, 0L));
        long ceiling = endSequence != null ? endSequence : Long.MAX_VALUE;
        for (AgentEvent ae : filterAgentReplay(events, floor, ceiling)) {
            subscriber.emitter.send(SseEmitter.event()
                    .id(Long.toString(markerSequence))
                    .name(ae.getType())
                    .data(ae));
            subscriber.agentCursors.put(sessionId, ae.getSequence());
        }
    }

    /**
     * 回放下界：MARKER 自带 startSequence 时以其为准，但同时受会话
     * 游标约束——live 直转已推进游标（同实例订阅者已收到）的部分不再
     * 重复下发；旧 MARKER 无 start 时回退会话游标。
     */
    protected long replayFloor(Long startSequence, long sessionCursor) {
        return startSequence != null
                ? Math.max(startSequence, sessionCursor)
                : sessionCursor;
    }

    /**
     * 按窗口过滤 agent 回放：floor < sequence <= ceiling。
     */
    protected List<AgentEvent> filterAgentReplay(
            List<AgentEvent> events, long floor, long ceiling) {
        List<AgentEvent> result = new ArrayList<>();
        for (AgentEvent ae : events) {
            if (ae.getSequence() > floor && ae.getSequence() <= ceiling) {
                result.add(ae);
            }
        }
        return result;
    }

    /**
     * 预扫描重放事件：会话 → 按 persisted 顺序的 (markerSequence, start)
     * 列表，供窗口上界计算。旧 MARKER 无 start 时记 null。
     */
    private Map<String, List<long[]>> markerStartBounds(
            List<ProjectEvent> replayEvents) {
        Map<String, List<long[]>> bounds = new LinkedHashMap<>();
        for (ProjectEvent event : replayEvents) {
            if (event.getType() != ProjectEventType.AGENT_RUN_MARKER
                    || event.getPayload() == null
                    || !event.getPayload().has("sessionId")) {
                continue;
            }
            String sessionId = event.getPayload().get("sessionId").asText();
            Long start = startSequenceOrNull(event.getPayload());
            bounds.computeIfAbsent(sessionId, ignored -> new ArrayList<>())
                    .add(new long[] {event.getSequence(), start == null ? -1L : start});
        }
        return bounds;
    }

    /**
     * 窗口上界 = 同会话中下一个 MARKER（persisted 顺序）的 startSequence；
     * 无后续 MARKER（或后续 MARKER 无 start）时返回 null（无上界）。
     */
    private Long nextEndBound(
            Map<String, List<long[]>> bounds, String sessionId, long markerSequence) {
        List<long[]> pairs = bounds.get(sessionId);
        if (pairs == null) {
            return null;
        }
        for (long[] pair : pairs) {
            if (pair[0] > markerSequence) {
                return pair[1] < 0 ? null : pair[1];
            }
        }
        return null;
    }

    private Long nextMarkerStart(
            String tenantId, String conversationId, String sessionId,
            long afterSequence) {
        String payload = repository.findNextMarkerPayload(
                tenantId, conversationId, sessionId, afterSequence);
        if (payload == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"startSequence\"\\s*:\\s*(-?\\d+)")
                .matcher(payload);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    private static Long startSequenceOrNull(
            com.fasterxml.jackson.databind.JsonNode payload) {
        return payload != null && payload.has("startSequence")
                ? payload.get("startSequence").asLong() : null;
    }

    private long minimumSequence(List<Subscriber> projectSubscribers) {
        long minimum = Long.MAX_VALUE;
        for (Subscriber subscriber : projectSubscribers) {
            synchronized (subscriber) {
                minimum = Math.min(minimum, subscriber.lastSequence);
            }
        }
        return minimum == Long.MAX_VALUE ? 0L : minimum;
    }

    private static String stringFromPayload(
            com.fasterxml.jackson.databind.JsonNode payload, String field) {
        return payload != null && payload.has(field)
                ? payload.get(field).asText() : "";
    }

    private void send(Subscriber subscriber, ProjectEvent event) throws IOException {
        if (shouldDeliver(subscriber, event) == false) {
            return;
        }
        SseEmitter.SseEventBuilder builder = SseEmitter.event();
        if (event.isLiveOnly()) {
            // live 帧不落库、无数据库序列：id 沿用当前已确认的持久化游标，
            // 保证任何 EventSource 客户端重连时 Last-Event-ID 仍落在
            // 数据库序列命名空间（重放多给不丢数据）。
            builder.id(Long.toString(subscriber.lastSequence));
        } else {
            builder.id(Long.toString(event.getSequence()));
        }
        if (event.getAgentEvent() != null) {
            builder.name(event.getAgentEvent().getType())
                   .data(event.getAgentEvent());
        } else {
            builder.name(event.getType().name())
                   .data(event);
        }
        subscriber.emitter.send(builder);
        recordDelivered(subscriber, event);
    }

    /**
     * 投递确认：持久化事件推进数据库序列游标（唯一允许推进该游标的
     * 入口）；live 事件推进其 agent 会话游标。两个游标分属不同命名
     * 空间，混用会把持久化游标污染成 live 计数器值，导致后续
     * userMessage 等持久化事件被去重逻辑误丢弃。
     */
    protected void recordDelivered(Subscriber subscriber, ProjectEvent event) {
        if (event.isLiveOnly()) {
            AgentEvent agentEvent = event.getAgentEvent();
            if (agentEvent != null && agentEvent.getSessionId() != null
                    && agentEvent.getSequence() > 0L) {
                subscriber.agentCursors.merge(
                        agentEvent.getSessionId(), agentEvent.getSequence(),
                        Math::max);
            }
            return;
        }
        subscriber.lastSequence = event.getSequence();
    }

    /**
     * 序列去重：持久化事件按数据库序列过滤重放；live 事件按会话级
     * agent 游标去重——同一 agent 事件经由 live 直转与 MARKER 回放
     * 两条路径到达订阅者时只投递一次。无会话信息的 live 事件（合成
     * 通知）一律投递。
     */
    protected boolean shouldDeliver(Subscriber subscriber, ProjectEvent event) {
        if (event.isLiveOnly()) {
            AgentEvent agentEvent = event.getAgentEvent();
            if (agentEvent == null || agentEvent.getSessionId() == null
                    || agentEvent.getSequence() <= 0L) {
                return true;
            }
            long cursor = subscriber.agentCursors.getOrDefault(
                    agentEvent.getSessionId(), 0L);
            return agentEvent.getSequence() > cursor;
        }
        return event.getSequence() > subscriber.lastSequence;
    }

    private void remove(
            TaskKey key,
            CopyOnWriteArrayList<Subscriber> projectSubscribers,
            Subscriber subscriber) {
        projectSubscribers.remove(subscriber);
        if (projectSubscribers.isEmpty()) {
            subscribers.remove(key, projectSubscribers);
        }
    }

    static final class Subscriber {
        private final SseEmitter emitter;
        private long lastSequence;
        private volatile long lastActivityAt;
        /** 会话级回放游标：旧 MARKER 无 startSequence 时的窗口兜底。 */
        private final Map<String, Long> agentCursors = new ConcurrentHashMap<>();
        Subscriber(SseEmitter emitter, long lastSequence) {
            this.emitter = emitter;
            this.lastSequence = lastSequence;
            this.lastActivityAt = System.currentTimeMillis();
        }
    }

    private static final class TaskKey {
        private final String tenantId;
        private final String projectId;
        private final String taskId;

        private TaskKey(String tenantId, String projectId, String taskId) {
            this.tenantId = tenantId;
            this.projectId = projectId;
            this.taskId = taskId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TaskKey)) {
                return false;
            }
            TaskKey that = (TaskKey) other;
            return Objects.equals(tenantId, that.tenantId)
                    && Objects.equals(projectId, that.projectId)
                    && Objects.equals(taskId, that.taskId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, projectId, taskId);
        }
    }
}
