package org.cmb.teamcoordinator.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.cmb.teamcoordinator.agentcore.AgentCoreAdapter;
import org.cmb.teamcoordinator.agentcore.AgentCoreConversationRequest;
import org.cmb.teamcoordinator.agentcore.AgentCoreConversationResponse;
import org.cmb.teamcoordinator.agentcore.AgentEvent;
import org.cmb.teamcoordinator.agentcore.AgentRunRequest;
import org.cmb.teamcoordinator.agentcore.AgentRunResponse;
import org.cmb.teamcoordinator.agentcore.ExpertDescriptor;
import org.cmb.teamcoordinator.agentcore.ExpertRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/mock")
public class MockAgentCoreController {

    private final AgentCoreAdapter agentCoreAdapter;
    private final ExpertRegistry expertRegistry;
    private final ObjectMapper objectMapper;

    public MockAgentCoreController(
            AgentCoreAdapter agentCoreAdapter, ExpertRegistry expertRegistry,
            ObjectMapper objectMapper) {
        this.agentCoreAdapter = agentCoreAdapter;
        this.expertRegistry = expertRegistry;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/experts")
    public List<ExpertDescriptor> listExperts() {
        return expertRegistry.listExperts();
    }

    @PostMapping("/agentcore/runs")
    public ResponseEntity<AgentCoreConversationResponse> submitRun(
            @RequestBody AgentCoreConversationRequest request) {
        AgentRunResponse response;
        String agentId = "expert-analysis";
        if ("stopSession".equals(request.getType())) {
            response = agentCoreAdapter.stopSession(agentId, request.getSessionId());
        } else if ("userAnswerQuestion".equals(request.getType())) {
            response = agentCoreAdapter.answerQuestion(
                    agentId, request.getSessionId(),
                    request.getData().getQuestionId(),
                    request.getData().getAnswers());
        } else {
            AgentRunRequest run = new AgentRunRequest();
            run.setSystemPrompt(request.getSystemPrompt());
            if (request.getData() != null
                    && request.getData().getContents() != null
                    && !request.getData().getContents().isEmpty()) {
                run.setTaskText(request.getData().getContents().get(0).getValue());
                run.setSkillNames(request.getData().getSkillNames());
                run.setAttachments(request.getData().getAttachments());
            }
            response = agentCoreAdapter.submitRun("expert-analysis", run);
        }
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.accepted()
                .body(AgentCoreConversationResponse.success(response));
    }

    @PostMapping("/agentcore/runs/{sessionId}/cancel")
    public ResponseEntity<AgentEvent> cancelRun(@PathVariable String sessionId) {
        AgentEvent event = agentCoreAdapter.cancelRun("expert-analysis", sessionId);
        return event == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(event);
    }

    @GetMapping("/agentcore/runs/{sessionId}")
    public ResponseEntity<AgentEvent> getRunStatus(@PathVariable String sessionId) {
        AgentEvent event = agentCoreAdapter.getRunStatus("expert-analysis", sessionId);
        return event == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(event);
    }

    @GetMapping(value = "/agentcore/runs/{sessionId}/streamEvents",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(
            @PathVariable String sessionId,
            @RequestParam(value = "afterSequence", required = false) Long afterSequence,
            @RequestHeader(value = "Content-Type", required = false) String contentType) {

        SseEmitter emitter = new SseEmitter(30_000L);
        try {
            for (AgentEvent event : agentCoreAdapter.streamEvents(
                    "expert-analysis", sessionId, afterSequence)) {
                emitter.send(SseEmitter.event()
                        .id(event.getEventId())
                        .data(toChunk(event)));
            }
            emitter.complete();
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    /**
     * Convert an AgentEvent to the SSE chunk format that matches
     * real AgentCore output. The AgentEvent is serialized directly
     * as the SSE data payload, preserving all fields.
     */
    private Map<String, Object> toChunk(AgentEvent event) {
        @SuppressWarnings("unchecked")
        Map<String, Object> chunk = objectMapper.convertValue(event, Map.class);
        return chunk;
    }
}
