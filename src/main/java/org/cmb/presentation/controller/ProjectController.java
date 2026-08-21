package org.cmb.presentation.controller;
import org.cmb.application.dto.ProjectRequests;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.cmb.application.service.IdentityProvider;
import java.util.List;
import org.cmb.application.dto.ProjectRequests.CreateProject;
import org.cmb.application.dto.ProjectRequests.UpdateProject;
import org.cmb.application.dto.ProjectRequests.UpsertExpert;
import org.cmb.application.dto.ProjectRequests.UpsertMember;
import org.cmb.application.dto.ProjectRequests.UpsertSkill;
import org.cmb.application.service.ProjectService;
import org.cmb.application.dto.ProjectView;
import org.cmb.application.domain.RequestIdentity;
import org.cmb.application.domain.entity.SkillDO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final IdentityProvider identityProvider;

    public ProjectController(ProjectService projectService, IdentityProvider identityProvider) {
        this.projectService = projectService;
        this.identityProvider = identityProvider;
    }

    /**
     * 列出当前用户可见的项目。
     */
    @GetMapping
    public java.util.List<ProjectView> list(HttpServletRequest request) {
        return projectService.list(identity(request));
    }

    /**
     * 创建项目
     * @param servletRequest
     * @param request
     * @return
     */
    @PostMapping
    public ResponseEntity<ProjectView> create(
            HttpServletRequest servletRequest, @Valid @RequestBody CreateProject request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.create(identity(servletRequest), request));
    }

    /**
     * 查询项目详情
     * @param request
     * @param projectId
     * @return
     */
    @GetMapping("/{projectId}")
    public ProjectView get(HttpServletRequest request, @PathVariable String projectId) {
        return projectService.get(identity(request), projectId);
    }

    /**
     * 更新项目
     * @param servletRequest
     * @param projectId
     * @param request
     * @return
     */
    @PatchMapping("/{projectId}")
    public ProjectView update(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @Valid @RequestBody UpdateProject request) {
        return projectService.update(identity(servletRequest), projectId, request);
    }

    /**
     * 添加/更新项目成员
     * @param servletRequest
     * @param projectId
     * @param request
     * @return
     */
    @PostMapping("/{projectId}/members")
    public ProjectView upsertMember(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @Valid @RequestBody UpsertMember request) {
        return projectService.upsertMember(identity(servletRequest), projectId, request);
    }

    /**
     * 删除项目成员
     * @param servletRequest
     * @param projectId
     * @param userId
     * @return
     */
    @DeleteMapping("/{projectId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @PathVariable String userId) {
        projectService.removeMember(identity(servletRequest), projectId, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 更新专家列表
     * @param servletRequest
     * @param projectId
     * @param request
     * @return
     */
    @PostMapping("/{projectId}/experts")
    public ProjectView upsertExpert(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @Valid @RequestBody UpsertExpert request) {
        return projectService.upsertExpert(identity(servletRequest), projectId, request);
    }

    /**
     * 删除专家
     * @param servletRequest
     * @param projectId
     * @param expertId
     * @return
     */
    @DeleteMapping("/{projectId}/experts/{expertId}")
    public ResponseEntity<Void> removeExpert(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @PathVariable String expertId) {
        projectService.removeExpert(identity(servletRequest), projectId, expertId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 查询项目的技能列表
     * @param request
     * @param projectId
     * @return
     */
    @GetMapping("/{projectId}/skills")
    public List<SkillDO> listSkills(
            HttpServletRequest request, @PathVariable String projectId) {
        return projectService.listProjectSkills(identity(request), projectId);
    }

    /**
     * 为项目添加/更新技能
     * @param servletRequest
     * @param projectId
     * @param request
     * @return
     */
    @PostMapping("/{projectId}/skills")
    public ProjectView upsertSkill(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @Valid @RequestBody UpsertSkill request) {
        return projectService.upsertSkill(identity(servletRequest), projectId, request);
    }

    /**
     * 从项目中移除技能
     * @param servletRequest
     * @param projectId
     * @param skillId
     * @return
     */
    @DeleteMapping("/{projectId}/skills/{skillId}")
    public ResponseEntity<Void> removeSkill(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @PathVariable String skillId) {
        projectService.removeSkill(identity(servletRequest), projectId, skillId);
        return ResponseEntity.noContent().build();
    }

    private RequestIdentity identity(HttpServletRequest request) {
        return identityProvider.currentIdentity(request);
    }
}
