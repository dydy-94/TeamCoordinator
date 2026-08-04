package org.cmb.teamcoordinator.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.cmb.teamcoordinator.persistence.BoundSqlStatement;
import org.junit.jupiter.api.Test;

class BoundSqlStatementTest {

    @Test
    void bindsParametersWithoutReplacingQuestionMarksInsideLiterals() {
        BoundSqlStatement statement = new BoundSqlStatement(
                "SELECT '?' marker FROM project WHERE business_id = ? AND status = ?",
                "project-1",
                "ACTIVE");

        assertEquals(
                "SELECT '?' marker FROM project WHERE business_id = #{parameters[0]} "
                        + "AND status = #{parameters[1]}",
                statement.getSql());
        assertEquals("project-1", statement.getParameters().get(0));
    }

    @Test
    void rejectsMismatchedParameterCounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BoundSqlStatement(
                        "SELECT * FROM project WHERE business_id = ?", "project-1", "extra"));
    }
}
