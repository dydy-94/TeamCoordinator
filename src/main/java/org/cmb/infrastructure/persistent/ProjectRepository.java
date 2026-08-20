package org.cmb.infrastructure.persistent;

import java.util.List;
import java.util.UUID;
import org.cmb.infrastructure.persistent.mapper.ProjectMapper;
import org.cmb.teamcoordinator.project.ProjectExpert;
import org.cmb.teamcoordinator.project.ProjectMember;
import org.cmb.teamcoordinator.project.ProjectRecord;
import org.cmb.teamcoordinator.project.ProjectRole;
import org.cmb.teamcoordinator.project.ProjectStatus;
import org.cmb.teamcoordinator.project.RequestIdentity;
import org.springframework.stereotype.Repository;

/**
 * Project persistence facade. Owns orchestration (enum name conversion,
 * audit id generation); all SQL lives in {@link ProjectMapper}.
 */
@Repository
public class ProjectRepository {

    private final ProjectMapper mapper;

    public ProjectRepository(ProjectMapper mapper) {
        this.mapper = mapper;
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
        List<String> roles = mapper.findRole(tenantId, projectId, userId);
        return roles.isEmpty() ? null : ProjectRole.valueOf(roles.get(0));
    }

    public List<ProjectMember> findMembers(String tenantId, String projectId) {
        return mapper.findMembers(tenantId, projectId);
    }

    public List<ProjectExpert> findExperts(String tenantId, String projectId) {
        return mapper.findExperts(tenantId, projectId);
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
        Integer count = mapper.countMember(tenantId, projectId, userId);
        return count != null && count > 0;
    }

    public void insertMember(
            String tenantId, String projectId, String userId, ProjectRole role) {
        mapper.insertMember(tenantId, projectId, userId, role.name());
    }

    public void updateMember(
            String tenantId, String projectId, String userId, ProjectRole role) {
        mapper.updateMember(role.name(), tenantId, projectId, userId);
    }

    public int deleteMember(String tenantId, String projectId, String userId) {
        return mapper.deleteMember(tenantId, projectId, userId);
    }

    public boolean expertExists(String tenantId, String projectId, String expertId) {
        Integer count = mapper.countExpert(tenantId, projectId, expertId);
        return count != null && count > 0;
    }

    public void insertExpert(
            String tenantId, String projectId, String expertId, boolean enabled) {
        mapper.insertExpert(tenantId, projectId, expertId, enabled);
    }

    public void updateExpert(
            String tenantId, String projectId, String expertId, boolean enabled) {
        mapper.updateExpert(enabled, tenantId, projectId, expertId);
    }

    public int deleteExpert(String tenantId, String projectId, String expertId) {
        return mapper.deleteExpert(tenantId, projectId, expertId);
    }

    public void audit(
            RequestIdentity identity,
            String projectId,
            String action,
            String targetId,
            String detail) {
        mapper.insertAudit("audit-" + UUID.randomUUID(), identity.getTenantId(),
                projectId, identity.getUserId(), action, targetId, detail);
    }
}
