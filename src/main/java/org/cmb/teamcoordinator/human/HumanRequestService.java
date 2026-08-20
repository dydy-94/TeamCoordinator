package org.cmb.teamcoordinator.human;
import org.cmb.common.enums.HumanRequestType;
import org.cmb.common.enums.HumanDecision;
import org.cmb.application.dto.HumanResponseRequest;
import org.cmb.application.dto.HumanRequestView;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.cmb.common.exception.ApiException;
import org.cmb.application.domain.AgentCoreAdapter;
import org.cmb.application.domain.AgentRunRequest;
import org.cmb.application.domain.AgentRunResponse;
import org.cmb.infrastructure.persistent.ExecutionRepository;
import org.cmb.infrastructure.persistent.HumanRequestRepository;
import org.cmb.infrastructure.persistent.HumanRequestRepository.HumanRequestRecord;
import org.cmb.application.domain.DispatchWork;
import org.cmb.application.domain.TaskRecord;
import org.cmb.application.domain.ProjectMember;
import org.cmb.common.enums.ProjectRole;
import org.cmb.teamcoordinator.project.ProjectService;
import org.cmb.application.dto.ProjectView;
import org.cmb.application.domain.RequestIdentity;
import org.cmb.teamcoordinator.prompt.PromptService;
import org.cmb.application.dto.RenderedPrompt;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HumanRequestService {

    private final HumanRequestRepository repository;
    private final ProjectService projectService;
    private final ExecutionRepository executionRepository;
    private final AgentCoreAdapter agentCore;
    private final PromptService prompts;

    public HumanRequestService(
            HumanRequestRepository repository,
            ProjectService projectService,
            ExecutionRepository executionRepository,
            AgentCoreAdapter agentCore,
            PromptService prompts) {
        this.repository = repository;
        this.projectService = projectService;
        this.executionRepository = executionRepository;
        this.agentCore = agentCore;
        this.prompts = prompts;
    }

    @Transactional
    public HumanRequestView respond(
            RequestIdentity identity,
            String projectId,
            String requestId,
            HumanResponseRequest request) {
        ProjectView project = projectService.get(identity, projectId);
        HumanRequestRecord record =
                repository.find(identity.getTenantId(), projectId, requestId);
        if (record == null) {
            throw ApiException.notFound(
                    "HUMAN_REQUEST_NOT_FOUND", "Human request was not found.");
        }
        if ("RESOLVED".equals(record.status)) {
            if (request.getIdempotencyKey().equals(record.responseIdempotencyKey)) {
                return toView(record);
            }
            throw ApiException.conflict(
                    "HUMAN_REQUEST_ALREADY_DECIDED",
                    "Human request already has a final decision.");
        }
        if (record.expiresAt != null && !record.expiresAt.isAfter(Instant.now())) {
            repository.expire(identity.getTenantId(), requestId);
            throw ApiException.conflict(
                    "HUMAN_REQUEST_EXPIRED", "Human request has expired.");
        }
        ProjectRole role = role(project, identity.getUserId());
        if (role == null || !record.allowedRoles.contains(role.name())) {
            throw ApiException.forbidden(
                    "HUMAN_RESPONSE_FORBIDDEN", "User is not an allowed responder.");
        }
        validateDecision(record.type, request.getDecision());
        validateSchema(record.inputSchema, request.getResponse());
        int updated = repository.resolve(
                identity.getTenantId(), requestId, request.getDecision(),
                request.getResponse(), request.getIdempotencyKey(), identity.getUserId());
        if (updated != 1) {
            HumanRequestRecord current =
                    repository.find(identity.getTenantId(), projectId, requestId);
            if (current != null && "RESOLVED".equals(current.status)) {
                return toView(current);
            }
            throw ApiException.conflict(
                    "HUMAN_REQUEST_ALREADY_DECIDED",
                    "Human request already has a final decision.");
        }
        if (record.taskId == null && request.getDecision() != HumanDecision.REJECT) {
            repository.resumeCoordinatorDispatch(
                    identity.getTenantId(), record.messageId, record.dispatchId,
                    request.getResponse().path("answer").asText());
        } else if (record.taskId != null
                && request.getDecision() != HumanDecision.REJECT) {
            TaskRecord task = executionRepository.findTask(
                    identity.getTenantId(), projectId, record.taskId);
            DispatchWork work = executionRepository.loadWorkForTask(
                    identity.getTenantId(), projectId, record.taskId);
            String answer = request.getResponse().path("answer").asText();
            AgentRunResponse resumed = tryResume(
                    task.getExpertId(), task.getSessionId(),
                    record.agentQuestionId,
                    answers(request.getResponse()));
            if (resumed == null) {
                AgentRunRequest run = new AgentRunRequest();
                Map<String, Object> promptContext = new LinkedHashMap<>();
                promptContext.put("objective", task.getObjective());
                promptContext.put("expectedOutput", task.getExpectedOutput());
                promptContext.put("acceptanceCriteria", task.getAcceptanceCriteria());
                promptContext.put("humanResponse", answer);
                promptContext.put("previousAgentRunId", task.getSessionId());
                RenderedPrompt prompt = prompts.render(
                        PromptService.EXPERT_RESUME, promptContext,
                        identity.getTenantId(), projectId, work.getConversationId(),
                        request.getIdempotencyKey() + ":new-run", task.getExpertId());
                run.setSystemPrompt(prompt.getContent());
                run.setTaskText(answer);
                run.setStructuredInput(promptContext);
                // Reuse existing session to continue the agent conversation
                run.setConversationSessionId(task.getSessionId());
                resumed = agentCore.submitRun(task.getExpertId(), run);
                executionRepository.replaceSession(
                        task.getId(), resumed.getSessionId());
            } else {
                repository.resumeTask(identity.getTenantId(), record.taskId);
            }
        } else if (record.taskId != null) {
            repository.failTaskAndDispatch(
                    identity.getTenantId(), record.taskId, "CANCELLED",
                    "Human responder rejected the request.");
        }
        return toView(repository.find(identity.getTenantId(), projectId, requestId));
    }

    private AgentRunResponse tryResume(
            String expertId, String sessionId,
            String questionId, Map<String, String> answers) {
        try {
            return agentCore.answerQuestion(
                    expertId, sessionId, questionId, answers);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Map<String, String> answers(JsonNode response) {
        JsonNode source = response.has("answers") ? response.get("answers") : response;
        Map<String, String> result = new LinkedHashMap<>();
        source.fields().forEachRemaining(
                entry -> result.put(entry.getKey(), entry.getValue().asText()));
        return result;
    }

    @Scheduled(fixedDelayString = "${digital-team.human.timeout-scan-ms:5000}")
    @Transactional
    public void expireDueRequests() {
        for (HumanRequestRecord record : repository.findExpiredPending()) {
            repository.expire(record.tenantId, record.id);
            if (record.taskId != null) {
                repository.failTaskAndDispatch(
                        record.tenantId, record.taskId, "TIMED_OUT",
                        "Human request expired.");
            }
        }
    }

    private void validateDecision(HumanRequestType type, HumanDecision decision) {
        boolean valid = type == HumanRequestType.CLARIFICATION
                ? decision == HumanDecision.ANSWER
                : decision == HumanDecision.APPROVE || decision == HumanDecision.REJECT;
        if (!valid) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "HUMAN_DECISION_INVALID",
                    "Decision does not match the request type.");
        }
    }

    private void validateSchema(String schema, JsonNode response) {
        if (schema == null) {
            return;
        }
        if (!JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
                .getSchema(schema).validate(response).isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "HUMAN_RESPONSE_INVALID",
                    "Response does not match input_schema.");
        }
    }

    private ProjectRole role(ProjectView project, String userId) {
        for (ProjectMember member : project.getMembers()) {
            if (userId.equals(member.getUserId())) {
                return member.getRole();
            }
        }
        return null;
    }

    private HumanRequestView toView(HumanRequestRecord record) {
        HumanRequestView view = new HumanRequestView();
        view.setId(record.id);
        view.setProjectId(record.projectId);
        view.setTaskId(record.taskId);
        view.setRequestType(record.type);
        view.setQuestion(record.question);
        view.setStatus(record.status);
        view.setDecision(record.decision);
        view.setResponse(record.response);
        view.setExpiresAt(record.expiresAt);
        return view;
    }
}
