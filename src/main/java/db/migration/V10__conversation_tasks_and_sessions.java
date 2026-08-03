package db.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V10__conversation_tasks_and_sessions extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        boolean mysql = "MySQL".equalsIgnoreCase(
                connection.getMetaData().getDatabaseProductName());
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "ALTER TABLE project_conversation ADD COLUMN session_id VARCHAR(128)");
            statement.execute(
                    "ALTER TABLE project_conversation ADD COLUMN title VARCHAR(128)");
            statement.execute(
                    "ALTER TABLE project_conversation ADD COLUMN status VARCHAR(32) "
                            + "NOT NULL DEFAULT 'ACTIVE'");
            statement.execute(
                    "UPDATE project_conversation SET session_id = CONCAT('session-', id) "
                            + "WHERE session_id IS NULL");
            statement.execute(mysql
                    ? "ALTER TABLE project_conversation MODIFY COLUMN session_id "
                            + "VARCHAR(128) NOT NULL"
                    : "ALTER TABLE project_conversation ALTER COLUMN session_id "
                            + "VARCHAR(128) NOT NULL");
            if (mysql) {
                statement.execute(
                        "ALTER TABLE project_conversation DROP INDEX uk_conversation_project");
                statement.execute(
                        "ALTER TABLE project_event DROP INDEX uk_project_event_sequence");
            } else {
                statement.execute(
                        "ALTER TABLE project_conversation "
                                + "DROP CONSTRAINT uk_conversation_project");
                statement.execute(
                        "ALTER TABLE project_event "
                                + "DROP CONSTRAINT uk_project_event_sequence");
            }
            statement.execute(
                    "ALTER TABLE project_conversation ADD CONSTRAINT "
                            + "uk_conversation_session UNIQUE (tenant_id, session_id)");
            statement.execute(
                    "ALTER TABLE project_event ADD CONSTRAINT uk_task_event_sequence "
                            + "UNIQUE (tenant_id, conversation_id, sequence)");
            statement.execute(
                    "CREATE TABLE conversation_event_sequence ("
                            + "tenant_id VARCHAR(64) NOT NULL, "
                            + "conversation_id VARCHAR(64) NOT NULL, "
                            + "next_sequence BIGINT NOT NULL, "
                            + "PRIMARY KEY (tenant_id, conversation_id), "
                            + "CONSTRAINT fk_conversation_event_sequence FOREIGN KEY "
                            + "(conversation_id) REFERENCES project_conversation (id))");
            statement.execute(
                    "INSERT INTO conversation_event_sequence "
                            + "(tenant_id, conversation_id, next_sequence) "
                            + "SELECT tenant_id, conversation_id, MAX(sequence) + 1 "
                            + "FROM project_event GROUP BY tenant_id, conversation_id");
            statement.execute(
                    "ALTER TABLE coordinator_agent_run "
                            + "ADD COLUMN business_session_id VARCHAR(128)");
        }
    }
}
