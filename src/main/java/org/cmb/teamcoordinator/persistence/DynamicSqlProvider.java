package org.cmb.teamcoordinator.persistence;

public final class DynamicSqlProvider {

    private DynamicSqlProvider() {
    }

    public static String sql(BoundSqlStatement statement) {
        return statement.getSql();
    }
}
