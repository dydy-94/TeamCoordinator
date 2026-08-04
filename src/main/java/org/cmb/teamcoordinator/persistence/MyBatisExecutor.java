package org.cmb.teamcoordinator.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MyBatisExecutor {

    private final DatabaseMapper mapper;

    public MyBatisExecutor(DatabaseMapper mapper) {
        this.mapper = mapper;
    }

    public int update(String sql, Object... parameters) {
        return mapper.update(new BoundSqlStatement(sql, parameters));
    }

    public <T> List<T> query(
            String sql, MyBatisRowMapper<T> rowMapper, Object... parameters) {
        List<Map<String, Object>> rows =
                mapper.select(new BoundSqlStatement(sql, parameters));
        List<T> result = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            try {
                result.add(rowMapper.mapRow(new MyBatisRow(rows.get(index)), index));
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException("Could not map MyBatis result row.", ex);
            }
        }
        return result;
    }

    public <T> T queryForObject(String sql, Class<T> type, Object... parameters) {
        List<T> rows = queryForList(sql, type, parameters);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public <T> T queryForObject(
            String sql, MyBatisRowMapper<T> rowMapper, Object... parameters) {
        List<T> rows = query(sql, rowMapper, parameters);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public <T> List<T> queryForList(String sql, Class<T> type, Object... parameters) {
        List<Map<String, Object>> rows =
                mapper.select(new BoundSqlStatement(sql, parameters));
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object value = row.values().iterator().next();
            result.add(convert(value, type));
        }
        return result;
    }

    public List<Map<String, Object>> queryForList(String sql, Object... parameters) {
        return mapper.select(new BoundSqlStatement(sql, parameters));
    }

    public Map<String, Object> queryForMap(String sql, Object... parameters) {
        List<Map<String, Object>> rows = queryForList(sql, parameters);
        return rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
    }

    @SuppressWarnings("unchecked")
    private <T> T convert(Object value, Class<T> type) {
        if (value == null || type.isInstance(value)) {
            return (T) value;
        }
        if (type == Long.class && value instanceof Number) {
            return (T) Long.valueOf(((Number) value).longValue());
        }
        if (type == Integer.class && value instanceof Number) {
            return (T) Integer.valueOf(((Number) value).intValue());
        }
        if (type == String.class) {
            return (T) String.valueOf(value);
        }
        throw new IllegalArgumentException("Cannot convert query result to " + type.getName());
    }
}
