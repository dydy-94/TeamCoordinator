package org.cmb.application.domain.entity;

import java.time.Instant;

/**
 * Row shape of digital_team_platform_admin — 平台管理员(userId 直接插入
 * 表项即可赋权,无管理 API)。运行时校验 = 本表 ∪ PLATFORM_ADMIN_USERS。
 */
public record PlatformAdminDO(
        Long id,
        String userId,
        Instant createdAt) {
}
