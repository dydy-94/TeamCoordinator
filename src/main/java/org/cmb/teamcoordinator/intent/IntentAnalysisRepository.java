package org.cmb.teamcoordinator.intent;

import java.util.UUID;
import org.cmb.teamcoordinator.human.HumanRequestRepository;
import org.cmb.teamcoordinator.project.RequestIdentity;
import org.cmb.teamcoordinator.persistence.MyBatisExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class IntentAnalysisRepository {

    private final MyBatisExecutor jdbc;
    private final HumanRequestRepository humanRequests;

    public IntentAnalysisRepository(
            MyBatisExecutor jdbc, HumanRequestRepository humanRequests) {
        this.jdbc = jdbc;
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
        jdbc.update(
                "INSERT INTO coordinator_analysis "
                        + "(business_id, tenant_id, project_id, user_id, input_snapshot, model_name, "
                        + "prompt_version, schema_version, decision_type, decision_json, repaired) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
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

    public org.cmb.teamcoordinator.human.HumanRequestRepository.HumanRequestRecord
            findPendingHumanRequest(String tenantId, String projectId, String taskId) {
        return humanRequests.findPendingForTask(tenantId, projectId, taskId);
    }
}
