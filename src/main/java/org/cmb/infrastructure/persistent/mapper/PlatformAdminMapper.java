package org.cmb.infrastructure.persistent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * SQL access for digital_team_platform_admin (one mapper per table).
 */
@Mapper
public interface PlatformAdminMapper {

    int countByUser(@Param("userId") String userId);
}
