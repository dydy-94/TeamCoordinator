package org.cmb.teamcoordinator.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;

@Mapper
public interface DatabaseMapper {

    @SelectProvider(type = DynamicSqlProvider.class, method = "sql")
    List<Map<String, Object>> select(BoundSqlStatement statement);

    @UpdateProvider(type = DynamicSqlProvider.class, method = "sql")
    int update(BoundSqlStatement statement);
}
