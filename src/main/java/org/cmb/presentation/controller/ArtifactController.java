package org.cmb.presentation.controller;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.cmb.application.service.ArtifactService;
import org.cmb.application.dto.ArtifactUploadRequest;
import org.cmb.application.dto.ArtifactView;
import org.cmb.application.domain.IdentityProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/artifacts")
public class ArtifactController {

    private final ArtifactService service;
    private final IdentityProvider identityProvider;

    public ArtifactController(ArtifactService service, IdentityProvider identityProvider) {
        this.service = service;
        this.identityProvider = identityProvider;
    }

    /**
     * 上传文件
     * 预留一个 Artifact，并获取 MinIO 预签名上传地址
     * @param servletRequest
     * @param projectId
     * @param request
     * @return
     */
    @PostMapping("/uploads")
    public ArtifactView reserve(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @Valid @RequestBody ArtifactUploadRequest request) {
        return service.reserve(
                identityProvider.currentIdentity(servletRequest), projectId, request);
    }

    /**
     * 通知 Coordinator 上传完成
     * @param servletRequest
     * @param projectId
     * @param artifactId
     * @return
     */
    @PostMapping("/{artifactId}/complete")
    public ArtifactView complete(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @PathVariable String artifactId) {
        return service.complete(
                identityProvider.currentIdentity(servletRequest), projectId, artifactId);
    }

    /**
     * 查询 Artifact 元数据并取得临时 downloadUrl
     * @param servletRequest
     * @param projectId
     * @param artifactId
     * @return
     */
    @GetMapping("/{artifactId}")
    public ArtifactView get(
            HttpServletRequest servletRequest,
            @PathVariable String projectId,
            @PathVariable String artifactId) {
        return service.get(
                identityProvider.currentIdentity(servletRequest), projectId, artifactId);
    }
}
