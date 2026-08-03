package org.cmb.teamcoordinator.api;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.cmb.teamcoordinator.project.IdentityProvider;
import org.cmb.teamcoordinator.project.ProjectRequests.CreateProject;
import org.cmb.teamcoordinator.project.ProjectRequests.UpdateProject;
import org.cmb.teamcoordinator.project.ProjectRequests.UpsertExpert;
import org.cmb.teamcoordinator.project.ProjectRequests.UpsertMember;
import org.cmb.teamcoordinator.project.ProjectService;
import org.cmb.teamcoordinator.project.ProjectView;
import org.cmb.teamcoordinator.project.RequestIdentity;
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

    @PostMapping
    public ResponseEntity<ProjectView> create(
            HttpServletRequest servletRequest, @Valid @RequestBody CreateProject request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.create(identity(servletRequest), request));
    }

    @GetMapping("/{projectId}")
    public ProjectView get(HttpServletRequest request, @PathVariable String projectId) {
        return projectService.get(identity(request), projectId);
    }

    @PatchMapping("/{projectId}")
    public ProjectView update(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @Valid @RequestBody UpdateProject request) {
        return projectService.update(identity(servletRequest), projectId, request);
    }

    @PostMapping("/{projectId}/members")
    public ProjectView upsertMember(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @Valid @RequestBody UpsertMember request) {
        return projectService.upsertMember(identity(servletRequest), projectId, request);
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @PathVariable String userId) {
        projectService.removeMember(identity(servletRequest), projectId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/experts")
    public ProjectView upsertExpert(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @Valid @RequestBody UpsertExpert request) {
        return projectService.upsertExpert(identity(servletRequest), projectId, request);
    }

    @DeleteMapping("/{projectId}/experts/{expertId}")
    public ResponseEntity<Void> removeExpert(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @PathVariable String expertId) {
        projectService.removeExpert(identity(servletRequest), projectId, expertId);
        return ResponseEntity.noContent().build();
    }

    private RequestIdentity identity(HttpServletRequest request) {
        return identityProvider.currentIdentity(request);
    }
}
