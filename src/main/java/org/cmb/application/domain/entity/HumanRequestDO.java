package org.cmb.application.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import org.cmb.common.enums.HumanDecision;
import org.cmb.common.enums.HumanRequestType;

/**
 * Row type for digital_team_human_request (extracted from
 * HumanRequestRepository's nested record). Public fields match the
 * HumanRequestMapper resultMap property names.
 */
public class HumanRequestDO {
    public String id;
    public String tenantId;
    public String analysisId;
    public String taskId;
    public String messageId;
    public String dispatchId;
    public String projectId;
    public HumanRequestType type;
    public String question;
    public String agentQuestionId;
    public String allowedRoles;
    public String inputSchema;
    public String status;
    public HumanDecision decision;
    public JsonNode response;
    public String responseIdempotencyKey;
    public Instant expiresAt;
}
