package org.cmb.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cmb.infrastructure.persistent.mapper.WorkspaceMapper;
import org.cmb.application.dto.ProjectView;
import org.springframework.stereotype.Service;

/**
 * Application facade for the task workspace snapshot. Assembles the
 * response map exactly as the old inline queries did: snapshot keys are
 * column aliases verbatim (front-end contract), and each row map is
 * re-wrapped as {@link LinkedHashMap} for stable serialization. The single
 * conversation row keeps the old {@code queryForMap} semantics (empty map
 * when no row matches).
 */
@Service
public class WorkspaceService {

    private final WorkspaceMapper mapper;

    public WorkspaceService(WorkspaceMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> snapshot(
            String tenantId, String projectId, String taskId, ProjectView project) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", project);
        result.put("task", singleOrEmpty(
                mapper.findConversation(tenantId, projectId, taskId)));
        result.put("messages",
                asLinkedMaps(mapper.findMessages(tenantId, projectId, taskId)));
        result.put("events",
                asLinkedMaps(mapper.findEvents(tenantId, projectId, taskId)));
        result.put("plans",
                asLinkedMaps(mapper.findPlans(tenantId, projectId, taskId)));
        result.put("tasks",
                asLinkedMaps(mapper.findTasks(tenantId, projectId, taskId)));
        result.put("humanRequests",
                asLinkedMaps(mapper.findHumanRequests(tenantId, projectId, taskId)));
        result.put("artifacts",
                asLinkedMaps(mapper.findArtifacts(tenantId, projectId)));
        return result;
    }

    private static Map<String, Object> singleOrEmpty(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(rows.get(0));
    }

    private static List<Map<String, Object>> asLinkedMaps(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            result.add(new LinkedHashMap<>(row));
        }
        return result;
    }
}
