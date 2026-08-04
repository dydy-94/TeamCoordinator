package org.cmb.teamcoordinator.persistence;

import java.sql.Timestamp;
import java.util.Map;

public final class MyBatisRow {

    private final Map<String, Object> values;
    private boolean wasNull;

    MyBatisRow(Map<String, Object> values) {
        this.values = values;
    }

    public String getString(String name) {
        Object value = value(name);
        return value == null ? null : String.valueOf(value);
    }

    public long getLong(String name) {
        Object value = value(name);
        return value == null ? 0 : ((Number) value).longValue();
    }

    public int getInt(String name) {
        Object value = value(name);
        return value == null ? 0 : ((Number) value).intValue();
    }

    public boolean getBoolean(String name) {
        Object value = value(name);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null && ((Number) value).intValue() != 0;
    }

    public Timestamp getTimestamp(String name) {
        Object value = value(name);
        return value == null ? null : (Timestamp) value;
    }

    public boolean wasNull() {
        return wasNull;
    }

    private Object value(String name) {
        Object value = values.get(name);
        if (value == null && !values.containsKey(name)) {
            value = values.get(name.toUpperCase());
        }
        wasNull = value == null;
        return value;
    }
}
