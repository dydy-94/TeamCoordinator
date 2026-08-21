package org.cmb.application.service;

import java.util.List;
import org.cmb.application.domain.RequestIdentity;
import org.cmb.application.domain.entity.SkillDO;
import org.cmb.application.dto.ProjectRequests.CreateProject;
import org.cmb.application.dto.ProjectRequests.UpdateProject;
import org.cmb.application.dto.ProjectRequests.UpsertExpert;
import org.cmb.application.dto.ProjectRequests.UpsertMember;
import org.cmb.application.dto.ProjectRequests.UpsertSkill;
import org.cmb.application.dto.ProjectView;

/**
 * Project management: CRUD, members, experts, skills and authorization
 * helpers.
 */
public interface ProjectService {

    ProjectView create(RequestIdentity identity, CreateProject request);

    List<ProjectView> list(RequestIdentity identity);

    ProjectView get(RequestIdentity identity, String projectId);

    ProjectView update(
            RequestIdentity identity, String projectId, UpdateProject request);

    ProjectView upsertMember(
            RequestIdentity identity, String projectId, UpsertMember request);

    void removeMember(RequestIdentity identity, String projectId, String userId);

    ProjectView upsertExpert(
            RequestIdentity identity, String projectId, UpsertExpert request);

    void removeExpert(RequestIdentity identity, String projectId, String expertId);

    List<SkillDO> listProjectSkills(RequestIdentity identity, String projectId);

    ProjectView upsertSkill(
            RequestIdentity identity, String projectId, UpsertSkill request);

    void removeSkill(RequestIdentity identity, String projectId, String skillId);

    void requireTaskInitiator(RequestIdentity identity, String projectId);
}
