package org.cmb.infrastructure.persistent;

import java.util.List;
import org.cmb.application.domain.entity.TenantDO;
import org.cmb.application.domain.entity.TenantUserDO;
import org.cmb.common.enums.TenantRole;
import org.cmb.common.enums.TenantStatus;
import org.cmb.infrastructure.persistent.mapper.TenantMapper;
import org.cmb.infrastructure.persistent.mapper.TenantUserMapper;
import org.springframework.stereotype.Repository;

/**
 * Tenant persistence facade. All SQL lives in {@link TenantMapper} and
 * {@link TenantUserMapper}. Tenant-user rows reference external user ids —
 * this service stores no user entity.
 */
@Repository
public class TenantRepository {

    private final TenantMapper tenantMapper;
    private final TenantUserMapper tenantUserMapper;

    public TenantRepository(
            TenantMapper tenantMapper, TenantUserMapper tenantUserMapper) {
        this.tenantMapper = tenantMapper;
        this.tenantUserMapper = tenantUserMapper;
    }

    public TenantDO findById(String tenantId) {
        List<TenantDO> rows = tenantMapper.selectById(tenantId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public TenantDO findByName(String name) {
        List<TenantDO> rows = tenantMapper.selectByName(name);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<TenantDO> listAll() {
        return tenantMapper.listAll();
    }

    public void insert(TenantDO tenant) {
        tenantMapper.insertTenant(
                tenant.getBusinessId(),
                tenant.getName(),
                tenant.getDescription(),
                tenant.getOwnerUserId(),
                tenant.getStatus().name(),
                tenant.getCreatedBy());
    }

    public void update(
            String tenantId, String name, String description, String ownerUserId) {
        tenantMapper.updateTenant(name, description, ownerUserId, tenantId);
    }

    public void updateStatus(String tenantId, TenantStatus status) {
        tenantMapper.updateStatus(status.name(), tenantId);
    }

    public int countProjects(String tenantId) {
        return tenantMapper.countProjects(tenantId);
    }

    public void delete(String tenantId) {
        tenantUserMapper.deleteByTenant(tenantId);
        tenantMapper.deleteTenant(tenantId);
    }

    public TenantUserDO findMembership(String tenantId, String userId) {
        List<TenantUserDO> rows = tenantUserMapper.findMembership(tenantId, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void upsertMember(String tenantId, String userId, TenantRole role) {
        tenantUserMapper.upsertMember(role.name(), tenantId, userId);
    }

    public void deleteMember(String tenantId, String userId) {
        tenantUserMapper.deleteMember(tenantId, userId);
    }

    public List<TenantUserDO> listMembers(String tenantId) {
        return tenantUserMapper.listMembers(tenantId);
    }

    /** 该用户的全部租户成员关系(含角色)。 */
    public List<TenantUserDO> listMembershipsByUser(String userId) {
        return tenantUserMapper.listByUser(userId);
    }

    public int countMembers(String tenantId) {
        return tenantUserMapper.countMembers(tenantId);
    }

    public int countAdmins(String tenantId) {
        return tenantUserMapper.countAdmins(tenantId);
    }
}
