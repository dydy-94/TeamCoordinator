package org.cmb.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cmb.infrastructure.persistent.mapper.CoordinatorPlanMapper;
import org.cmb.infrastructure.persistent.mapper.CoordinatorTaskMapper;
import org.cmb.infrastructure.persistent.mapper.HumanRequestMapper;
import org.cmb.infrastructure.persistent.mapper.ProjectArtifactMapper;
import org.cmb.infrastructure.persistent.mapper.ProjectConversationMapper;
import org.cmb.infrastructure.persistent.mapper.ProjectEventMapper;
import org.cmb.infrastructure.persistent.mapper.ProjectMessageMapper;
import org.cmb.application.dto.ProjectView;
import org.springframework.stereotype.Service;

/**
 * Application facade for the task workspace snapshot. Assembles the
 * response map exactly as the old inline queries did: snapshot keys are
 * column aliases verbatim (front-end contract), and each row map is
 * re-wrapped as {@link LinkedHashMap} for stable serialization. The single
 * conversation row keeps the old {@code queryForMap} semantics (empty map
 * when no row matches). Read-only queries live in each table's own mapper.
 */
@Service
public class WorkspaceService {

    private final ProjectConversationMapper conversationMapper;
    private final ProjectMessageMapper messageMapper;
    private final ProjectEventMapper eventMapper;
    private final CoordinatorPlanMapper planMapper;
    private final CoordinatorTaskMapper taskMapper;
    private final HumanRequestMapper humanRequestMapper;
    private final ProjectArtifactMapper artifactMapper;

    public WorkspaceService(
            ProjectConversationMapper conversationMapper,
            ProjectMessageMapper messageMapper,
            ProjectEventMapper eventMapper,
            CoordinatorPlanMapper planMapper,
            CoordinatorTaskMapper taskMapper,
            HumanRequestMapper humanRequestMapper,
            ProjectArtifactMapper artifactMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.eventMapper = eventMapper;
        this.planMapper = planMapper;
        this.taskMapper = taskMapper;
        this.humanRequestMapper = humanRequestMapper;
        this.artifactMapper = artifactMapper;
    }

    public Map<String, Object> snapshot(
            String tenantId, String projectId, String taskId, ProjectView project) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", project);
        result.put("task", singleOrEmpty(
                conversationMapper.findConversation(tenantId, projectId, taskId)));
        result.put("messages",
                asLinkedMaps(messageMapper.findMessages(tenantId, projectId, taskId)));
        result.put("events",
                asLinkedMaps(eventMapper.findEvents(tenantId, projectId, taskId)));
        result.put("plans",
                asLinkedMaps(planMapper.findPlans(tenantId, projectId, taskId)));
        result.put("tasks",
                asLinkedMaps(taskMapper.findTasks(tenantId, projectId, taskId)));
        result.put("humanRequests",
                asLinkedMaps(humanRequestMapper.findHumanRequests(tenantId, projectId, taskId)));
        result.put("artifacts",
                asLinkedMaps(artifactMapper.findArtifacts(tenantId, projectId)));
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
