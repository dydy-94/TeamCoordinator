package org.cmb.application.service;

import java.util.List;
import org.cmb.application.dto.TenantMemberView;
import org.cmb.application.dto.TenantRequests.AssignMember;
import org.cmb.application.dto.TenantRequests.CreateTenant;
import org.cmb.application.dto.TenantRequests.UpdateTenant;
import org.cmb.application.dto.TenantView;

/**
 * 租户管理:我的租户列表、平台管理员租户 CRUD、租户管理员
 * 成员管理。用户身份为外部 userId,本服务不建用户表。
 */
public interface TenantService {

    /** 当前用户有权访问的租户(含用户在各租户的角色)。 */
    List<TenantView> listMyTenants(String userId);

    /** 平台管理员:全部租户。 */
    List<TenantView> listAllTenants(String actorUserId);

    /** 平台管理员:建租户(创建者自动成为 TENANT_ADMIN)。 */
    TenantView createTenant(String actorUserId, CreateTenant request);

    /** 平台管理员:完整更新(可改负责人)。 */
    TenantView updateTenant(String actorUserId, String tenantId, UpdateTenant request);

    /** 租户管理员(或平台管理员):更新名称/描述(不可改负责人/状态)。 */
    TenantView updateTenantInfo(String actorUserId, String tenantId, UpdateTenant request);

    /** 平台管理员:禁用租户(幂等)。 */
    void disableTenant(String actorUserId, String tenantId);

    /** 平台管理员:硬删除,仅当租户下无项目。 */
    void deleteTenant(String actorUserId, String tenantId);

    /** 平台管理员或租户管理员:赋权成员(建/改角色)。 */
    List<TenantMemberView> assignMember(
            String actorUserId, String tenantId, AssignMember request);

    /** 平台管理员或租户管理员:移除成员。 */
    void removeMember(String actorUserId, String tenantId, String userId);

    /** 租户成员列表。 */
    List<TenantMemberView> listMembers(String actorUserId, String tenantId);
}
