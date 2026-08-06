package org.cmb.teamcoordinator.coordinator;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.cmb.teamcoordinator.agentcore.AgentCoreAdapter;
import org.cmb.teamcoordinator.agentcore.AgentEvent;
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
    private final Map<TaskKey, CopyOnWriteArrayList<Subscriber>> subscribers =
            new ConcurrentHashMap<>();

    public ProjectEventStreamHub(MessageEventRepository repository,
                                  AgentCoreAdapter agentCore) {
        this.repository = repository;
        this.agentCore = agentCore;
    }

    public SseEmitter subscribe(
            String tenantId,
            String projectId,
            String taskId,
            long afterSequence,
            Supplier<List<ProjectEvent>> replaySupplier) {
        TaskKey key = new TaskKey(tenantId, projectId, taskId);
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
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
        if (event.getSequence() <= subscriber.lastSequence) {
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

    private void remove(
            TaskKey key,
            CopyOnWriteArrayList<Subscriber> projectSubscribers,
            Subscriber subscriber) {
        projectSubscribers.remove(subscriber);
        if (projectSubscribers.isEmpty()) {
            subscribers.remove(key, projectSubscribers);
        }
    }

    private static final class Subscriber {
        private final SseEmitter emitter;
        private long lastSequence;

        private Subscriber(SseEmitter emitter, long lastSequence) {
            this.emitter = emitter;
            this.lastSequence = lastSequence;
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
