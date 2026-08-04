package org.cmb.teamcoordinator.intent;

import java.util.List;
import java.util.UUID;
import org.cmb.teamcoordinator.agentcore.AgentRunEvent;
import org.cmb.teamcoordinator.project.RequestIdentity;
import org.springframework.dao.DuplicateKeyException;
import org.cmb.teamcoordinator.persistence.MyBatisExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class CoordinatorAgentRunRepository {

    private final MyBatisExecutor jdbc;

    public CoordinatorAgentRunRepository(MyBatisExecutor jdbc) {
        this.jdbc = jdbc;
    }

    public CoordinatorAgentRun createOrLoad(
            RequestIdentity identity, String projectId, String messageId,
            String runKey, String contextJson, String businessSessionId) {
        try {
            jdbc.update(
                    "INSERT INTO coordinator_agent_run "
                            + "(business_id, tenant_id, project_id, message_id, run_key, context_json, "
                            + "business_session_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    "coordinator-run-" + UUID.randomUUID(), identity.getTenantId(), projectId,
                    messageId, runKey, contextJson, businessSessionId);
        } catch (DuplicateKeyException ignored) {
            // Another coordinator instance won creation; both load the same durable run.
        }
        return find(identity.getTenantId(), runKey);
    }

    public CoordinatorAgentRun find(String tenantId, String runKey) {
        List<CoordinatorAgentRun> rows = jdbc.query(
                "SELECT id AS database_id, business_id, session_id, stage, status, last_sequence, context_json, "
                        + "business_session_id, "
                        + "invalid_output, output_json FROM coordinator_agent_run "
                        + "WHERE tenant_id = ? AND run_key = ?",
                (rs, row) -> {
                    CoordinatorAgentRun run = new CoordinatorAgentRun();
                    run.setDatabaseId(rs.getLong("database_id"));
                    run.setBusinessId(rs.getString("business_id"));
                    run.setSessionId(rs.getString("session_id"));
                    run.setStage(rs.getString("stage"));
                    run.setStatus(rs.getString("status"));
                    run.setLastSequence(rs.getLong("last_sequence"));
                    run.setContextJson(rs.getString("context_json"));
                    run.setInvalidOutput(rs.getString("invalid_output"));
                    run.setOutputJson(rs.getString("output_json"));
                    run.setBusinessSessionId(rs.getString("business_session_id"));
                    return run;
                }, tenantId, runKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean saveSession(String id, String stage, String sessionId) {
        return jdbc.update(
                "UPDATE coordinator_agent_run SET session_id = ?, stage = ?, status = 'RUNNING', "
                        + "last_sequence = 0, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE business_id = ? AND session_id IS NULL",
                sessionId, stage, id) == 1;
    }

    public void advance(String id, AgentRunEvent event) {
        jdbc.update(
                "UPDATE coordinator_agent_run SET last_sequence = ?, status = ?, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE business_id = ? AND last_sequence < ?",
                event.getSequence(), event.getStatus(), id, event.getSequence());
    }

    public void complete(String id, long sequence, String output) {
        jdbc.update(
                "UPDATE coordinator_agent_run SET last_sequence = ?, status = 'SUCCEEDED', "
                        + "output_json = ?, updated_at = CURRENT_TIMESTAMP WHERE business_id = ?",
                sequence, output, id);
    }

    public void prepareRepair(String id, String invalidOutput) {
        jdbc.update(
                "UPDATE coordinator_agent_run SET session_id = NULL, stage = 'REPAIR', "
                        + "status = 'PENDING', last_sequence = 0, invalid_output = ?, "
                        + "output_json = NULL, updated_at = CURRENT_TIMESTAMP WHERE business_id = ?",
                invalidOutput, id);
    }

    public void fail(String id, String output) {
        jdbc.update(
                "UPDATE coordinator_agent_run SET status = 'FAILED', output_json = ?, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE business_id = ?",
                output, id);
    }
}
