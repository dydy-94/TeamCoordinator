package org.cmb.infrastructure.persistent;

import org.cmb.infrastructure.persistent.mapper.PlatformAdminMapper;
import org.springframework.stereotype.Repository;

/**
 * 平台管理员表门面。赋权方式:直接向 digital_team_platform_admin
 * 插入 user_id 行(无管理 API,运维可操作)。
 */
@Repository
public class PlatformAdminRepository {

    private final PlatformAdminMapper mapper;

    public PlatformAdminRepository(PlatformAdminMapper mapper) {
        this.mapper = mapper;
    }

    public boolean isAdmin(String userId) {
        return mapper.countByUser(userId) > 0;
    }
}
