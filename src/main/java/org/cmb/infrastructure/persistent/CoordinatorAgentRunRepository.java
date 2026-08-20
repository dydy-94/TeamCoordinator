package org.cmb.infrastructure.persistent;

import java.util.List;
import java.util.UUID;
import org.cmb.infrastructure.persistent.mapper.CoordinatorAgentRunMapper;
import org.cmb.application.domain.AgentEvent;
import org.cmb.application.domain.CoordinatorAgentRun;
import org.cmb.application.domain.RequestIdentity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * Coordinator-agent-run persistence facade. Owns the idempotent
 * create-or-load logic; all SQL lives in
 * {@link CoordinatorAgentRunMapper}.
 */
@Repository
public class CoordinatorAgentRunRepository {

    private final CoordinatorAgentRunMapper mapper;

    public CoordinatorAgentRunRepository(CoordinatorAgentRunMapper mapper) {
        this.mapper = mapper;
    }

    public CoordinatorAgentRun createOrLoad(
            RequestIdentity identity, String projectId, String messageId,
            String runKey, String contextJson, String businessSessionId) {
        try {
            mapper.insertRun(
                    "coordinator-run-" + UUID.randomUUID(), identity.getTenantId(),
                    projectId, messageId, runKey, contextJson, businessSessionId);
        } catch (DuplicateKeyException ignored) {
            // Another coordinator instance won creation; both load the same durable run.
        }
        return find(identity.getTenantId(), runKey);
    }

    public CoordinatorAgentRun find(String tenantId, String runKey) {
        List<CoordinatorAgentRun> rows = mapper.findByRunKey(tenantId, runKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean saveSession(String id, String stage, String sessionId) {
        int updated = mapper.saveSession(id, stage, sessionId);
        if (updated == 0) {
            // Session already exists (conversation reuse) — update stage/status
            // but preserve last_sequence so repair can skip old events
            updated = mapper.saveSessionExisting(id, stage, sessionId);
        }
        return updated == 1;
    }

    public void advance(String id, AgentEvent event) {
        String status = event.getStatus() != null ? event.getStatus() : "RUNNING";
        mapper.advance(id, event.getSequence(), status);
    }

    public void complete(String id, long sequence, String output) {
        mapper.complete(id, sequence, output);
    }

    public void prepareRepair(String id, String invalidOutput) {
        // Keep last_sequence so that when the repaired run re-uses the same
        // session, streamEvents(afterSequence) skips the original invalid
        // events and only returns the new repair events.
        mapper.prepareRepair(id, invalidOutput);
    }

    public void fail(String id, String output) {
        mapper.fail(id, output);
    }
}
