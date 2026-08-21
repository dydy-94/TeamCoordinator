package org.cmb.application.service;

import java.util.Map;
import org.cmb.application.dto.ProjectView;

/**
 * Application facade for the task workspace snapshot. The snapshot keys are
 * column aliases verbatim (front-end contract).
 */
public interface WorkspaceService {

    Map<String, Object> snapshot(
            String tenantId, String projectId, String taskId, ProjectView project);
}
