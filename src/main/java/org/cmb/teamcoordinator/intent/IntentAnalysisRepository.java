package org.cmb.teamcoordinator.intent;

import java.util.UUID;
import org.cmb.teamcoordinator.human.HumanRequestRepository;
import org.cmb.teamcoordinator.project.RequestIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IntentAnalysisRepository {

    private final JdbcTemplate jdbc;
    private final HumanRequestRepository humanRequests;

    public IntentAnalysisRepository(
            JdbcTemplate jdbc, HumanRequestRepository humanRequests) {
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
                        + "(id, tenant_id, project_id, user_id, input_snapshot, model_name, "
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
}
