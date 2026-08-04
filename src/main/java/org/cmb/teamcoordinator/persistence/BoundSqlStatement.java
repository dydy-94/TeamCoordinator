package org.cmb.teamcoordinator.persistence;

import java.util.Arrays;
import java.util.List;

public final class BoundSqlStatement {

    private final String sql;
    private final List<Object> parameters;

    public BoundSqlStatement(String sql, Object... parameters) {
        this.parameters = Arrays.asList(parameters);
        this.sql = bindParameters(sql, this.parameters.size());
    }

    public String getSql() {
        return sql;
    }

    public List<Object> getParameters() {
        return parameters;
    }

    private static String bindParameters(String sql, int parameterCount) {
        StringBuilder result = new StringBuilder();
        boolean quoted = false;
        int parameter = 0;
        for (int index = 0; index < sql.length(); index++) {
            char character = sql.charAt(index);
            if (character == '\'') {
                quoted = !quoted;
            }
            if (character == '?' && !quoted) {
                if (parameter >= parameterCount) {
                    throw new IllegalArgumentException("SQL has more placeholders than parameters.");
                }
                result.append("#{parameters[").append(parameter++).append("]}");
            } else {
                result.append(character);
            }
        }
        if (parameter != parameterCount) {
            throw new IllegalArgumentException("SQL parameter count does not match placeholders.");
        }
        return result.toString();
    }
}
