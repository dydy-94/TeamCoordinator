package db.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V7_1__allow_resumed_run_sequences extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String database = connection.getMetaData().getDatabaseProductName();
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE INDEX idx_task_event_task_id "
                            + "ON coordinator_task_event (task_id)");
            if ("MySQL".equalsIgnoreCase(database)) {
                statement.execute(
                        "ALTER TABLE coordinator_task_event "
                                + "DROP INDEX uk_task_event_sequence");
            } else {
                statement.execute(
                        "ALTER TABLE coordinator_task_event "
                                + "DROP CONSTRAINT uk_task_event_sequence");
            }
        }
    }
}
