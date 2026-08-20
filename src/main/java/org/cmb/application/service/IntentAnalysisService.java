package org.cmb.application.service;
import org.cmb.application.service.DecisionSchemaValidator;
import org.cmb.common.enums.DecisionType;
import org.cmb.application.dto.IntentAnalysisRequest;
import org.cmb.application.domain.IntentAnalysisContext;
import org.cmb.application.domain.CoordinatorDecision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import org.cmb.infrastructure.persistent.IntentAnalysisRepository;
import org.cmb.application.domain.AgentEvent;
import org.cmb.application.domain.ExpertDescriptor;
import org.cmb.application.domain.ExpertRegistry;
import org.cmb.infrastructure.persistent.ArtifactRepository;
import org.cmb.application.domain.FileStore;
import org.cmb.application.domain.MockFileDescriptor;
import org.cmb.application.domain.ProjectExpert;
import org.cmb.application.service.ProjectService;
import org.cmb.application.dto.ProjectView;
import org.cmb.application.domain.RequestIdentity;
import org.springframework.stereotype.Service;

@Service
public class IntentAnalysisService {

    private static final String PROMPT_VERSION = "database-managed";
    private static final String SCHEMA_VERSION = "task-intent-v1";

    private final ProjectService projectService;
    private final IntentAnalysisRepository analysisRepository;
    private final ExpertRegistry expertRegistry;
    private final ArtifactRepository artifactRepository;
    private final FileStore fileStore;
    private final CoordinatorAgentClient coordinatorAgent;
    private final DecisionSchemaValidator schemaValidator;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public IntentAnalysisService(
            ProjectService projectService,
            IntentAnalysisRepository analysisRepository,
            ExpertRegistry expertRegistry,
            ArtifactRepository artifactRepository,
            FileStore fileStore,
            CoordinatorAgentClient coordinatorAgent,
            DecisionSchemaValidator schemaValidator,
            ObjectMapper objectMapper,
            Validator validator) {
        this.projectService = projectService;
        this.analysisRepository = analysisRepository;
        this.expertRegistry = expertRegistry;
        this.artifactRepository = artifactRepository;
        this.fileStore = fileStore;
        this.coordinatorAgent = coordinatorAgent;
        this.schemaValidator = schemaValidator;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public CoordinatorDecision analyze(
            RequestIdentity identity, String projectId, IntentAnalysisRequest request) {
        String runKey = "direct-" + UUID.randomUUID();
        CoordinatorDecision decision = analyze(
                identity, projectId, null, null, runKey,
                "direct-session-" + UUID.randomUUID(), null, request, null);
        if (decision == null) {
            throw new IllegalStateException("Coordinator AgentCore run is still in progress.");
        }
        return decision;
    }

    public CoordinatorDecision analyzeForDispatch(
            RequestIdentity identity, String projectId, String taskId,
            String messageId, String businessSessionId,
            String coordinatorSessionId,
            IntentAnalysisRequest request,
            Consumer<AgentEvent> eventSink) {
        return analyze(
                identity, projectId, taskId, messageId, "message-" + messageId,
                businessSessionId, coordinatorSessionId, request, eventSink);
    }

    public CoordinatorDecision analyzeForDispatch(
            RequestIdentity identity, String projectId, String taskId,
            String messageId, String businessSessionId,
            IntentAnalysisRequest request) {
        return analyzeForDispatch(identity, projectId, taskId, messageId,
                businessSessionId, null, request, null);
    }

    private CoordinatorDecision analyze(
            RequestIdentity identity, String projectId, String taskId,
            String messageId, String runKey, String businessSessionId,
            String coordinatorSessionId,
            IntentAnalysisRequest request, Consumer<AgentEvent> eventSink) {
        projectService.requireTaskInitiator(identity, projectId);
        IntentAnalysisContext context = buildContext(identity, projectId, taskId, request);
        CoordinatorAgentClient.Result agentResult = coordinatorAgent.execute(
                identity, projectId, messageId, runKey,
                businessSessionId, coordinatorSessionId, context, eventSink);
        if (!agentResult.isComplete()) {
            return null;
        }
        String output = agentResult.getOutput();
        ParseResult parsed = parse(output);
        boolean repaired = agentResult.isRepaired();
        if (parsed == null && !repaired) {
            coordinatorAgent.prepareRepair(identity, runKey, output);
            agentResult = coordinatorAgent.execute(
                    identity, projectId, messageId, runKey,
                    businessSessionId, coordinatorSessionId, context, eventSink);
            if (!agentResult.isComplete()) {
                return null;
            }
            output = agentResult.getOutput();
            parsed = parse(output);
            repaired = parsed != null;
        }
        CoordinatorDecision decision = parsed == null ? fallbackDecision() : parsed.decision;
        decision.setCoordinatorSessionId(agentResult.getSessionId());
        decision.setEffectiveAgentId(agentResult.getEffectiveAgentId());
        String decisionJson = write(decision);
        String analysisId = "analysis-" + UUID.randomUUID();
        analysisRepository.insertAnalysis(
                analysisId,
                identity,
                projectId,
                write(context),
                "agentcore:" + CoordinatorAgentClient.COORDINATOR_AGENT_ID,
                PROMPT_VERSION,
                SCHEMA_VERSION,
                decision,
                decisionJson,
                repaired);
        decision.setAnalysisId(analysisId);
        if (decision.getDecisionType() == DecisionType.ASK_HUMAN) {
            decision.setHumanRequestId(analysisRepository.insertHumanRequest(
                    analysisId, identity, projectId, decision.getQuestion()));
        }
        return decision;
    }

    private IntentAnalysisContext buildContext(
            RequestIdentity identity, String projectId, String taskId,
            IntentAnalysisRequest request) {
        ProjectView project = projectService.get(identity, projectId);
        IntentAnalysisContext context = new IntentAnalysisContext();
        context.setProjectName(project.getName());
        context.setProjectDescription(project.getDescription());
        context.setCoordinatorAgentId(project.getCoordinatorAgentId());
        context.setText(request.getText());
        context.setAttachmentRefs(request.getAttachmentRefs());
        // Conversation history lives in AgentCore's session — no need to duplicate.
        context.setRecentMessages(new ArrayList<>());
        context.setExperts(enabledExperts(project));
        List<MockFileDescriptor> attachments = new ArrayList<>();
        for (String reference : request.getAttachmentRefs()) {
            String storageKey = artifactRepository.resolveStorageKey(
                    identity.getTenantId(), projectId, reference);
            MockFileDescriptor descriptor = fileStore.getDescriptor(storageKey);
            if (descriptor != null) {
                attachments.add(descriptor);
            }
        }
        context.setAttachments(attachments);

        // Inject pending human-request state so the Coordinator can decide
        // whether a new message answers a waiting question or is a new request.
        if (taskId != null) {
            injectPendingState(identity.getTenantId(), projectId, taskId, context);
        }

        return context;
    }

    private void injectPendingState(
            String tenantId, String projectId, String taskId,
            IntentAnalysisContext context) {
        org.cmb.infrastructure.persistent.HumanRequestRepository.HumanRequestRecord pending =
                analysisRepository.findPendingHumanRequest(tenantId, projectId, taskId);
        if (pending != null) {
            boolean isExpert = pending.taskId != null;
            context.setPendingStatus(isExpert ? "EXPERT_WAITING" : "COORDINATOR_WAITING");
            context.setPendingQuestion(pending.question);
        }
    }

    private List<ExpertDescriptor> enabledExperts(ProjectView project) {
        Set<String> enabled = new HashSet<>();
        for (ProjectExpert expert : project.getExperts()) {
            if (expert.isEnabled()) {
                enabled.add(expert.getExpertId());
            }
        }
        List<ExpertDescriptor> result = new ArrayList<>();
        for (ExpertDescriptor expert : expertRegistry.listExperts()) {
            if (enabled.isEmpty() || enabled.contains(expert.getExpertId())) {
                result.add(expert);
            }
        }
        return result;
    }

    private ParseResult parse(String output) {
        try {
            JsonNode node = objectMapper.readTree(output);
            schemaValidator.validate(node);
            CoordinatorDecision decision = objectMapper.treeToValue(node, CoordinatorDecision.class);
            Set<ConstraintViolation<CoordinatorDecision>> violations = validator.validate(decision);
            validateDecisionShape(decision);
            if (!violations.isEmpty()) {
                return null;
            }
            return new ParseResult(decision);
        } catch (Exception ex) {
            return null;
        }
    }

    private void validateDecisionShape(CoordinatorDecision decision) {
        if (decision.getDecisionType() == DecisionType.ANSWER
                && isBlank(decision.getAnswer())) {
            throw new IllegalArgumentException("ANSWER requires answer.");
        }
        if (decision.getDecisionType() == DecisionType.ASK_HUMAN
                && isBlank(decision.getQuestion())) {
            throw new IllegalArgumentException("ASK_HUMAN requires question.");
        }
        if (decision.getDecisionType() == DecisionType.CREATE_PLAN
                && decision.getTaskIntent() == null) {
            throw new IllegalArgumentException("CREATE_PLAN requires task_intent.");
        }
    }

    private CoordinatorDecision fallbackDecision() {
        CoordinatorDecision decision = new CoordinatorDecision();
        decision.setDecisionType(DecisionType.ASK_HUMAN);
        decision.setQuestion("暂时无法可靠理解该请求，请补充目标和期望输出后重试。");
        return decision;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize intent analysis.", ex);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class ParseResult {
        private final CoordinatorDecision decision;

        private ParseResult(CoordinatorDecision decision) {
            this.decision = decision;
        }
    }
}
