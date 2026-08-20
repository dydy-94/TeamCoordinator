package org.cmb.infrastructure.persistent;

import org.cmb.infrastructure.persistent.mapper.IntentAnalysisMapper;
import org.cmb.teamcoordinator.intent.CoordinatorDecision;
import org.cmb.teamcoordinator.project.RequestIdentity;
import org.springframework.stereotype.Repository;

/**
 * Intent-analysis persistence facade. All SQL lives in
 * {@link IntentAnalysisMapper}; human-request persistence is delegated to
 * {@link HumanRequestRepository}.
 */
@Repository
public class IntentAnalysisRepository {

    private final IntentAnalysisMapper mapper;
    private final HumanRequestRepository humanRequests;

    public IntentAnalysisRepository(
            IntentAnalysisMapper mapper, HumanRequestRepository humanRequests) {
        this.mapper = mapper;
        this.humanRequests = humanRequests;
    }

    public void insertAnalysis(
            String analysisId,
            RequestIdentity identity,
            String projectId,
            String inputSnapshot,
            String modelName,
            String promptVersion,
            String schemaVersion,
            CoordinatorDecision decision,
            String decisionJson,
            boolean repaired) {
        mapper.insertAnalysis(
                analysisId,
                identity.getTenantId(),
                projectId,
                identity.getUserId(),
                inputSnapshot,
                modelName,
                promptVersion,
                schemaVersion,
                decision.getDecisionType().name(),
                decisionJson,
                repaired);
    }

    public String insertHumanRequest(
            String analysisId, RequestIdentity identity, String projectId, String question) {
        return humanRequests.createCoordinatorClarification(
                analysisId, identity, projectId, question);
    }

    public HumanRequestRepository.HumanRequestRecord
            findPendingHumanRequest(String tenantId, String projectId, String taskId) {
        return humanRequests.findPendingForTask(tenantId, projectId, taskId);
    }
}
