package org.cmb.infrastructure.persistent;

import java.util.List;
import java.util.UUID;
import org.cmb.infrastructure.persistent.mapper.PermissionAuditLogMapper;
import org.cmb.infrastructure.persistent.mapper.ProjectExpertMapper;
import org.cmb.infrastructure.persistent.mapper.ProjectMapper;
import org.cmb.infrastructure.persistent.mapper.ProjectMemberMapper;
import org.cmb.application.domain.ProjectExpert;
import org.cmb.application.domain.ProjectMember;
import org.cmb.application.domain.ProjectRecord;
import org.cmb.common.enums.ProjectRole;
import org.cmb.common.enums.ProjectStatus;
import org.cmb.application.domain.RequestIdentity;
import org.springframework.stereotype.Repository;

/**
 * Project persistence facade. Owns orchestration (enum name conversion,
 * audit id generation); all SQL lives in the per-table mappers
 * ({@link ProjectMapper}, {@link ProjectMemberMapper},
 * {@link ProjectExpertMapper}, {@link PermissionAuditLogMapper}).
 */
@Repository
public class ProjectRepository {

    private final ProjectMapper mapper;
    private final ProjectMemberMapper memberMapper;
    private final ProjectExpertMapper expertMapper;
    private final PermissionAuditLogMapper auditMapper;

    public ProjectRepository(
            ProjectMapper mapper,
            ProjectMemberMapper memberMapper,
            ProjectExpertMapper expertMapper,
            PermissionAuditLogMapper auditMapper) {
        this.mapper = mapper;
        this.memberMapper = memberMapper;
        this.expertMapper = expertMapper;
        this.auditMapper = auditMapper;
    }

    public void insertProject(ProjectRecord project) {
        mapper.insertProject(project.getId(), project.getTenantId(), project.getName(),
                project.getDescription(), project.getCoordinatorAgentId(),
                project.getStatus().name(), project.getCreatedBy());
    }

    public List<ProjectRecord> findByTenant(String tenantId, String userId) {
        return mapper.findByTenant(tenantId, userId);
    }

    public ProjectRecord findVisible(String tenantId, String projectId, String userId) {
        List<ProjectRecord> rows = mapper.findVisible(tenantId, projectId, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public ProjectRole findRole(String tenantId, String projectId, String userId) {
        List<String> roles = memberMapper.findRole(tenantId, projectId, userId);
        return roles.isEmpty() ? null : ProjectRole.valueOf(roles.get(0));
    }

    public List<ProjectMember> findMembers(String tenantId, String projectId) {
        return memberMapper.findMembers(tenantId, projectId);
    }

    public List<ProjectExpert> findExperts(String tenantId, String projectId) {
        return expertMapper.findExperts(tenantId, projectId);
    }

    public void updateProject(
            String tenantId,
            String projectId,
            String name,
            String description,
            String coordinatorAgentId,
            ProjectStatus status) {
        mapper.updateProject(name, description, coordinatorAgentId, status.name(),
                tenantId, projectId);
    }

    public boolean memberExists(String tenantId, String projectId, String userId) {
        Integer count = memberMapper.countMember(tenantId, projectId, userId);
        return count != null && count > 0;
    }

    public void insertMember(
            String tenantId, String projectId, String userId, ProjectRole role) {
        memberMapper.insertMember(tenantId, projectId, userId, role.name());
    }

    public void updateMember(
            String tenantId, String projectId, String userId, ProjectRole role) {
        memberMapper.updateMember(role.name(), tenantId, projectId, userId);
    }

    public int deleteMember(String tenantId, String projectId, String userId) {
        return memberMapper.deleteMember(tenantId, projectId, userId);
    }

    public boolean expertExists(String tenantId, String projectId, String expertId) {
        Integer count = expertMapper.countExpert(tenantId, projectId, expertId);
        return count != null && count > 0;
    }

    public void insertExpert(
            String tenantId, String projectId, String expertId, boolean enabled) {
        expertMapper.insertExpert(tenantId, projectId, expertId, enabled);
    }

    public void updateExpert(
            String tenantId, String projectId, String expertId, boolean enabled) {
        expertMapper.updateExpert(enabled, tenantId, projectId, expertId);
    }

    public int deleteExpert(String tenantId, String projectId, String expertId) {
        return expertMapper.deleteExpert(tenantId, projectId, expertId);
    }

    public void audit(
            RequestIdentity identity,
            String projectId,
            String action,
            String targetId,
            String detail) {
        auditMapper.insertAudit("audit-" + UUID.randomUUID(), identity.getTenantId(),
                projectId, identity.getUserId(), action, targetId, detail);
    }
}
