package org.cmb.teamcoordinator.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import org.cmb.teamcoordinator.agentcore.ExpertDescriptor;
import org.cmb.teamcoordinator.agentcore.ExpertRegistry;
import org.cmb.teamcoordinator.artifact.ArtifactRepository;
import org.cmb.teamcoordinator.artifact.FileStore;
import org.cmb.teamcoordinator.artifact.MockFileDescriptor;
import org.cmb.teamcoordinator.coordinator.MessageEventRepository;
import org.cmb.teamcoordinator.project.ProjectExpert;
import org.cmb.teamcoordinator.project.ProjectService;
import org.cmb.teamcoordinator.project.ProjectView;
import org.cmb.teamcoordinator.project.RequestIdentity;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Service
public class IntentAnalysisService {

    private static final String PROMPT_VERSION = "intent-v1";
    private static final String SCHEMA_VERSION = "task-intent-v1";

    private final ProjectService projectService;
    private final MessageEventRepository messageRepository;
    private final IntentAnalysisRepository analysisRepository;
    private final ExpertRegistry expertRegistry;
    private final ArtifactRepository artifactRepository;
    private final FileStore fileStore;
    private final IntentModelClient modelClient;
    private final CoordinatorAgentClient coordinatorAgent;
    private final DecisionSchemaValidator schemaValidator;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final String prompt;

    public IntentAnalysisService(
            ProjectService projectService,
            MessageEventRepository messageRepository,
            IntentAnalysisRepository analysisRepository,
            ExpertRegistry expertRegistry,
            ArtifactRepository artifactRepository,
            FileStore fileStore,
            IntentModelClient modelClient,
            CoordinatorAgentClient coordinatorAgent,
            DecisionSchemaValidator schemaValidator,
            ObjectMapper objectMapper,
            Validator validator) {
        this.projectService = projectService;
        this.messageRepository = messageRepository;
        this.analysisRepository = analysisRepository;
        this.expertRegistry = expertRegistry;
        this.artifactRepository = artifactRepository;
        this.fileStore = fileStore;
        this.modelClient = modelClient;
        this.coordinatorAgent = coordinatorAgent;
        this.schemaValidator = schemaValidator;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.prompt = loadPrompt();
    }

    public CoordinatorDecision analyze(
            RequestIdentity identity, String projectId, IntentAnalysisRequest request) {
        String runKey = "direct-" + UUID.randomUUID();
        CoordinatorDecision decision = analyze(
                identity, projectId, null, runKey, request);
        if (decision == null) {
            throw new IllegalStateException("Coordinator AgentCore run is still in progress.");
        }
        return decision;
    }

    public CoordinatorDecision analyzeForDispatch(
            RequestIdentity identity, String projectId, String messageId,
            IntentAnalysisRequest request) {
        return analyze(identity, projectId, messageId, "message-" + messageId, request);
    }

    private CoordinatorDecision analyze(
            RequestIdentity identity, String projectId, String messageId, String runKey,
            IntentAnalysisRequest request) {
        projectService.requireTaskInitiator(identity, projectId);
        IntentAnalysisContext context = buildContext(identity, projectId, request);
        CoordinatorAgentClient.Result agentResult = coordinatorAgent.execute(
                identity, projectId, messageId, runKey, prompt, context);
        if (!agentResult.isComplete()) {
            return null;
        }
        String output = agentResult.getOutput();
        ParseResult parsed = parse(output);
        boolean repaired = agentResult.isRepaired();
        if (parsed == null && !repaired) {
            coordinatorAgent.prepareRepair(identity, runKey, output);
            agentResult = coordinatorAgent.execute(
                    identity, projectId, messageId, runKey, prompt, context);
            if (!agentResult.isComplete()) {
                return null;
            }
            output = agentResult.getOutput();
            parsed = parse(output);
            repaired = parsed != null;
        }
        CoordinatorDecision decision = parsed == null ? fallbackDecision() : parsed.decision;
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
            RequestIdentity identity, String projectId, IntentAnalysisRequest request) {
        ProjectView project = projectService.get(identity, projectId);
        IntentAnalysisContext context = new IntentAnalysisContext();
        context.setProjectName(project.getName());
        context.setProjectDescription(project.getDescription());
        context.setText(request.getText());
        context.setAttachmentRefs(request.getAttachmentRefs());
        context.setRecentMessages(messageRepository.findRecentMessageTexts(
                identity.getTenantId(), projectId, 10));
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
        return context;
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

    private String loadPrompt() {
        try {
            InputStream input = getClass().getResourceAsStream(
                    "/coordinator/task-intent-prompt-v1.txt");
            if (input == null) {
                throw new IllegalStateException("Intent prompt is missing.");
            }
            return StreamUtils.copyToString(input, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not load intent prompt.", ex);
        }
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
