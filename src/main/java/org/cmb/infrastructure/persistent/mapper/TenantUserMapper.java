package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.cmb.application.domain.entity.TenantUserDO;

/**
 * SQL access for digital_team_tenant_user (one mapper per table).
 */
@Mapper
public interface TenantUserMapper {

    int insertMember(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("role") String role);

    int upsertMember(
            @Param("role") String role,
            @Param("tenantId") String tenantId,
            @Param("userId") String userId);

    int deleteMember(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId);

    List<TenantUserDO> listMembers(@Param("tenantId") String tenantId);

    List<TenantUserDO> findMembership(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId);

    List<TenantUserDO> listByUser(@Param("userId") String userId);

    int countMembers(@Param("tenantId") String tenantId);

    int countAdmins(@Param("tenantId") String tenantId);

    int deleteByTenant(@Param("tenantId") String tenantId);
}
