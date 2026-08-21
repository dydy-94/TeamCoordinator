package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.cmb.application.domain.entity.TenantDO;

/**
 * SQL access for digital_team_tenant (one mapper per table).
 */
@Mapper
public interface TenantMapper {

    int insertTenant(
            @Param("businessId") String businessId,
            @Param("name") String name,
            @Param("description") String description,
            @Param("ownerUserId") String ownerUserId,
            @Param("status") String status,
            @Param("createdBy") String createdBy);

    int updateTenant(
            @Param("name") String name,
            @Param("description") String description,
            @Param("ownerUserId") String ownerUserId,
            @Param("tenantId") String tenantId);

    int updateStatus(
            @Param("status") String status,
            @Param("tenantId") String tenantId);

    List<TenantDO> selectById(@Param("tenantId") String tenantId);

    List<TenantDO> selectByName(@Param("name") String name);

    List<TenantDO> listAll();

    int countProjects(@Param("tenantId") String tenantId);

    int deleteTenant(@Param("tenantId") String tenantId);
}
