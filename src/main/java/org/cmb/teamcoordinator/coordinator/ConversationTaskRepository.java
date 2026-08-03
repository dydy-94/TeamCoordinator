package org.cmb.teamcoordinator.coordinator;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.cmb.teamcoordinator.project.RequestIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConversationTaskRepository {

    private final JdbcTemplate jdbc;

    public ConversationTaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ConversationTaskView create(
            RequestIdentity identity, String projectId, String title) {
        String taskId = "task-" + UUID.randomUUID();
        String sessionId = "session-" + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO project_conversation "
                        + "(id, tenant_id, project_id, session_id, title, status) "
                        + "VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
                taskId, identity.getTenantId(), projectId, sessionId, title);
        return get(identity.getTenantId(), projectId, taskId);
    }

    public ConversationTaskView get(String tenantId, String projectId, String taskId) {
        List<ConversationTaskView> rows = jdbc.query(
                "SELECT id, project_id, session_id, title, status, created_at "
                        + "FROM project_conversation WHERE tenant_id = ? "
                        + "AND project_id = ? AND id = ?",
                (rs, row) -> {
                    ConversationTaskView view = new ConversationTaskView();
                    view.setTaskId(rs.getString("id"));
                    view.setProjectId(rs.getString("project_id"));
                    view.setSessionId(rs.getString("session_id"));
                    view.setTitle(rs.getString("title"));
                    view.setStatus(rs.getString("status"));
                    Timestamp created = rs.getTimestamp("created_at");
                    view.setCreatedAt(created == null ? null : created.toInstant());
                    return view;
                }, tenantId, projectId, taskId);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
