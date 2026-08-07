package org.cmb.teamcoordinator.project;

import java.util.List;
import java.util.UUID;
import org.cmb.teamcoordinator.agentcore.ExpertDescriptor;
import org.cmb.teamcoordinator.agentcore.ExpertRegistry;
import org.cmb.teamcoordinator.common.ApiException;
import org.cmb.teamcoordinator.config.DigitalTeamProperties;
import org.cmb.teamcoordinator.project.ProjectRequests.CreateProject;
import org.cmb.teamcoordinator.project.ProjectRequests.UpdateProject;
import org.cmb.teamcoordinator.project.ProjectRequests.UpsertExpert;
import org.cmb.teamcoordinator.project.ProjectRequests.UpsertMember;
import org.cmb.teamcoordinator.project.ProjectRequests.UpsertSkill;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository repository;
    private final ExpertRegistry expertRegistry;
    private final SkillRepository skillRepository;
    private final DigitalTeamProperties properties;

    public ProjectService(ProjectRepository repository, ExpertRegistry expertRegistry,
            SkillRepository skillRepository, DigitalTeamProperties properties) {
        this.repository = repository;
        this.expertRegistry = expertRegistry;
        this.skillRepository = skillRepository;
        this.properties = properties;
    }

    @Transactional
    public ProjectView create(RequestIdentity identity, CreateProject request) {
        ProjectRecord project = new ProjectRecord();
        project.setId("project-" + UUID.randomUUID());
        project.setTenantId(identity.getTenantId());
        project.setName(request.getName().trim());
        project.setDescription(request.getDescription());
        project.setCoordinatorAgentId(request.getCoordinatorAgentId());
        project.setStatus(ProjectStatus.ACTIVE);
        project.setCreatedBy(identity.getUserId());
        try {
            repository.insertProject(project);
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict(
                    "PROJECT_NAME_EXISTS", "A project with this name already exists in the tenant.");
        }
        repository.insertMember(
                identity.getTenantId(), project.getId(), identity.getUserId(), ProjectRole.OWNER);
        repository.audit(identity, project.getId(), "PROJECT_CREATED", project.getId(), project.getName());
        return get(identity, project.getId());
    }

    @Transactional(readOnly = true)
    public java.util.List<ProjectView> list(RequestIdentity identity) {
        java.util.List<ProjectView> result = new java.util.ArrayList<>();
        for (ProjectRecord project : repository.findByTenant(
                identity.getTenantId(), identity.getUserId())) {
            ProjectView view = new ProjectView();
            view.setId(project.getId());
            view.setName(project.getName());
            view.setDescription(project.getDescription());
            view.setCoordinatorAgentId(project.getCoordinatorAgentId());
            view.setStatus(project.getStatus());
            view.setCreatedAt(project.getCreatedAt());
            view.setUpdatedAt(project.getUpdatedAt());
            // Members and experts are omitted for list compactness;
            // call get(id) for full details.
            result.add(view);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public ProjectView get(RequestIdentity identity, String projectId) {
        ProjectRecord project = requireVisible(identity, projectId);
        return toView(project);
    }

    @Transactional
    public ProjectView update(
            RequestIdentity identity, String projectId, UpdateProject request) {
        ProjectRecord project = requireOwnerAndActive(identity, projectId);
        String name = request.getName() == null ? project.getName() : request.getName().trim();
        String description =
                request.getDescription() == null ? project.getDescription() : request.getDescription();
        String coordinatorAgentId = request.getCoordinatorAgentId() != null
                ? request.getCoordinatorAgentId() : project.getCoordinatorAgentId();
        ProjectStatus status = request.getStatus() == null ? project.getStatus() : request.getStatus();
        repository.updateProject(
                identity.getTenantId(), projectId, name, description,
                coordinatorAgentId, status);
        // Coordinator agent and team expert are mutually exclusive —
        // fail with a clear message instead of silently removing.
        if (coordinatorAgentId != null && !coordinatorAgentId.trim().isEmpty()) {
            if (repository.expertExists(
                    identity.getTenantId(), projectId, coordinatorAgentId)) {
                throw ApiException.conflict(
                        "COORDINATOR_IS_EXPERT",
                        "主Agent '" + coordinatorAgentId
                        + "' 已在专家团队中。请先从团队移除该专家，再将其设为主Agent。");
            }
        }
        repository.audit(identity, projectId, "PROJECT_UPDATED", projectId, status.name());
        return get(identity, projectId);
    }

    @Transactional
    public ProjectView upsertMember(
            RequestIdentity identity, String projectId, UpsertMember request) {
        requireOwnerAndActive(identity, projectId);
        if (repository.memberExists(identity.getTenantId(), projectId, request.getUserId())) {
            repository.updateMember(
                    identity.getTenantId(), projectId, request.getUserId(), request.getRole());
        } else {
            repository.insertMember(
                    identity.getTenantId(), projectId, request.getUserId(), request.getRole());
        }
        repository.audit(
                identity,
                projectId,
                "MEMBER_UPSERTED",
                request.getUserId(),
                request.getRole().name());
        return get(identity, projectId);
    }

    @Transactional
    public void removeMember(RequestIdentity identity, String projectId, String userId) {
        requireOwnerAndActive(identity, projectId);
        if (identity.getUserId().equals(userId)) {
            throw ApiException.conflict(
                    "OWNER_SELF_REMOVAL_FORBIDDEN", "An owner cannot remove their own membership.");
        }
        repository.deleteMember(identity.getTenantId(), projectId, userId);
        repository.audit(identity, projectId, "MEMBER_REMOVED", userId, null);
    }

    @Transactional
    public ProjectView upsertExpert(
            RequestIdentity identity, String projectId, UpsertExpert request) {
        requireOwnerAndActive(identity, projectId);
        requireKnownExpert(request.getExpertId());
        if (repository.expertExists(identity.getTenantId(), projectId, request.getExpertId())) {
            repository.updateExpert(
                    identity.getTenantId(),
                    projectId,
                    request.getExpertId(),
                    request.isEnabled());
        } else {
            repository.insertExpert(
                    identity.getTenantId(),
                    projectId,
                    request.getExpertId(),
                    request.isEnabled());
        }
        repository.audit(
                identity,
                projectId,
                "EXPERT_UPSERTED",
                request.getExpertId(),
                Boolean.toString(request.isEnabled()));
        return get(identity, projectId);
    }

    @Transactional
    public void removeExpert(RequestIdentity identity, String projectId, String expertId) {
        requireOwnerAndActive(identity, projectId);
        repository.deleteExpert(identity.getTenantId(), projectId, expertId);
        repository.audit(identity, projectId, "EXPERT_REMOVED", expertId, null);
    }

    // ── Skills ────────────────────────────────────────────────────────────

    /**
     * Skills are provided by the platform's built-in AgentCore. Projects
     * using a custom (non-default) coordinator agent cannot use skills.
     */
    private void requireBuiltInAgentCore(ProjectRecord project) {
        String coord = project.getCoordinatorAgentId();
        boolean usesBuiltIn = coord == null || coord.trim().isEmpty()
                || coord.equals(properties.getAgentCore().getCoordinatorAgentId());
        if (!usesBuiltIn) {
            throw ApiException.conflict(
                    "SKILL_UNSUPPORTED",
                    "技能管理仅在项目使用平台内置AgentCore时可用。"
                            + "当前项目使用了自定义主Agent，不支持此功能。");
        }
    }

    @Transactional(readOnly = true)
    public List<Skill> listProjectSkills(RequestIdentity identity, String projectId) {
        ProjectRecord project = requireVisible(identity, projectId);
        return skillRepository.findByProject(identity.getTenantId(), projectId);
    }

    @Transactional
    public ProjectView upsertSkill(
            RequestIdentity identity, String projectId, UpsertSkill request) {
        ProjectRecord project = requireOwnerAndActive(identity, projectId);
        requireBuiltInAgentCore(project);
        Skill skill = skillRepository.findByBusinessId(request.getSkillId());
        if (skill == null) {
            throw ApiException.notFound("SKILL_NOT_FOUND", "技能不存在: " + request.getSkillId());
        }
        String tenantId = identity.getTenantId();
        if (skillRepository.projectSkillExists(tenantId, projectId, request.getSkillId())) {
            skillRepository.updateProjectSkill(
                    tenantId, projectId, request.getSkillId(), request.isEnabled());
        } else {
            skillRepository.insertProjectSkill(
                    tenantId, projectId, request.getSkillId(), request.isEnabled());
        }
        repository.audit(identity, projectId, "SKILL_UPSERTED",
                request.getSkillId(), Boolean.toString(request.isEnabled()));
        return get(identity, projectId);
    }

    @Transactional
    public void removeSkill(
            RequestIdentity identity, String projectId, String skillId) {
        ProjectRecord project = requireOwnerAndActive(identity, projectId);
        requireBuiltInAgentCore(project);
        skillRepository.deleteProjectSkill(identity.getTenantId(), projectId, skillId);
        repository.audit(identity, projectId, "SKILL_REMOVED", skillId, null);
    }

    // ── Authorization helpers ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public void requireTaskInitiator(RequestIdentity identity, String projectId) {
        ProjectRecord project = requireVisible(identity, projectId);
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw ApiException.conflict("PROJECT_ARCHIVED", "Archived projects are read-only.");
        }
        ProjectRole role =
                repository.findRole(identity.getTenantId(), projectId, identity.getUserId());
        if (role == ProjectRole.VIEWER) {
            throw ApiException.forbidden("TASK_START_FORBIDDEN", "VIEWER cannot start tasks.");
        }
    }

    private ProjectRecord requireVisible(RequestIdentity identity, String projectId) {
        ProjectRecord project =
                repository.findVisible(identity.getTenantId(), projectId, identity.getUserId());
        if (project == null) {
            throw ApiException.notFound("PROJECT_NOT_FOUND", "Project was not found.");
        }
        return project;
    }

    private ProjectRecord requireOwnerAndActive(RequestIdentity identity, String projectId) {
        ProjectRecord project = requireVisible(identity, projectId);
        ProjectRole role =
                repository.findRole(identity.getTenantId(), projectId, identity.getUserId());
        if (role != ProjectRole.OWNER) {
            throw ApiException.forbidden(
                    "PROJECT_MANAGE_FORBIDDEN", "Only OWNER can manage project configuration.");
        }
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw ApiException.conflict("PROJECT_ARCHIVED", "Archived projects are read-only.");
        }
        return project;
    }

    private void requireKnownExpert(String expertId) {
        for (ExpertDescriptor expert : expertRegistry.listExperts()) {
            if (expert.getExpertId().equals(expertId)) {
                return;
            }
        }
        throw ApiException.notFound("EXPERT_NOT_FOUND", "Expert was not found.");
    }

    private ProjectView toView(ProjectRecord project) {
        ProjectView view = new ProjectView();
        view.setId(project.getId());
        view.setName(project.getName());
        view.setDescription(project.getDescription());
        view.setCoordinatorAgentId(project.getCoordinatorAgentId());
        view.setStatus(project.getStatus());
        view.setCreatedAt(project.getCreatedAt());
        view.setUpdatedAt(project.getUpdatedAt());
        view.setMembers(repository.findMembers(project.getTenantId(), project.getId()));
        view.setExperts(repository.findExperts(project.getTenantId(), project.getId()));
        view.setSkills(skillRepository.findByProject(project.getTenantId(), project.getId()));
        return view;
    }
}
