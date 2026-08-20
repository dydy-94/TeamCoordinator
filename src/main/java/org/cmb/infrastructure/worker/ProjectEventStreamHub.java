package org.cmb.infrastructure.worker;
import org.cmb.common.enums.ProjectEventType;
import org.cmb.application.domain.ProjectEvent;

import java.io.IOException;
import java.util.Comparator;
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
            synchronized (subscriber) {
                for (ProjectEvent event : replaySupplier.get()) {
                    if (event.getType() == ProjectEventType.AGENT_RUN_MARKER
                            && event.getPayload() != null
                            && event.getPayload().has("sessionId")) {
                        replayAgentEvents(subscriber,
                                stringFromPayload(event.getPayload(), "expertId"),
                                event.getPayload().get("sessionId").asText(),
                                event.getSequence());
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
                            for (Subscriber subscriber : projectSubscribers) {
                                replayAgentEvents(subscriber, expertId,
                                        sessionId, event.getSequence());
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
                                    String sessionId, long markerSequence)
            throws IOException {
        List<AgentEvent> events = agentCore.streamEvents(expertId, sessionId, 0L);
        if (events == null || events.isEmpty()) {
            return;
        }
        events.sort(Comparator.comparingLong(AgentEvent::getSequence));
        for (AgentEvent ae : events) {
            if (ae.getSequence() <= subscriber.lastSequence) {
                continue;
            }
            subscriber.emitter.send(SseEmitter.event()
                    .id(Long.toString(markerSequence))
                    .name(ae.getType())
                    .data(ae));
        }
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
        SseEmitter.SseEventBuilder builder = SseEmitter.event()
                .id(Long.toString(event.getSequence()));
        if (event.getAgentEvent() != null) {
            builder.name(event.getAgentEvent().getType())
                   .data(event.getAgentEvent());
        } else {
            builder.name(event.getType().name())
                   .data(event);
        }
        subscriber.emitter.send(builder);
        subscriber.lastSequence = event.getSequence();
    }

    /**
     * 序列去重：持久化事件按单调序列过滤重放；live 事件（内存计数器命名
     * 空间）一律投递。
     */
    protected boolean shouldDeliver(Subscriber subscriber, ProjectEvent event) {
        return event.isLiveOnly()
                || event.getSequence() > subscriber.lastSequence;
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
