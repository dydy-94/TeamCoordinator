package db.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V6_1__allow_plan_versions extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String database = connection.getMetaData().getDatabaseProductName();
        try (Statement statement = connection.createStatement()) {
            if ("MySQL".equalsIgnoreCase(database)) {
                statement.execute(
                        "ALTER TABLE digital_team_coordinator_plan DROP INDEX uk_plan_message");
            } else {
                statement.execute(
                        "ALTER TABLE digital_team_coordinator_plan DROP CONSTRAINT uk_plan_message");
            }
            statement.execute(
                    "ALTER TABLE digital_team_coordinator_plan ADD CONSTRAINT uk_plan_message_version "
                            + "UNIQUE (tenant_id, project_id, message_id, plan_version)");
        }
    }
}
