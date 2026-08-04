package org.cmb.teamcoordinator.api;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.validation.Valid;
import org.cmb.teamcoordinator.agentcore.AgentCoreAdapter;
import org.cmb.teamcoordinator.agentcore.AgentRunEvent;
import org.cmb.teamcoordinator.agentcore.AgentRunRequest;
import org.cmb.teamcoordinator.agentcore.AgentRunResponse;
import org.cmb.teamcoordinator.agentcore.AgentCoreConversationRequest;
import org.cmb.teamcoordinator.agentcore.AgentCoreConversationResponse;
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

    public MockAgentCoreController(AgentCoreAdapter agentCoreAdapter, ExpertRegistry expertRegistry) {
        this.agentCoreAdapter = agentCoreAdapter;
        this.expertRegistry = expertRegistry;
    }

    @GetMapping("/experts")
    public List<ExpertDescriptor> listExperts() {
        return expertRegistry.listExperts();
    }

    @PostMapping("/agentcore/runs")
    public ResponseEntity<AgentCoreConversationResponse> submitRun(
            @RequestBody AgentCoreConversationRequest request) {
        AgentRunResponse response;
        if ("stopSession".equals(request.getType())) {
            response = agentCoreAdapter.stopSession(request.getSessionId());
        } else if ("userAnswerQuestion".equals(request.getType())) {
            response = agentCoreAdapter.answerQuestion(
                    request.getSessionId(), request.getData().getQuestionId(),
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
    public ResponseEntity<AgentRunEvent> cancelRun(@PathVariable String sessionId) {
        AgentRunEvent event = agentCoreAdapter.cancelRun(sessionId);
        if (event == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(event);
    }

    @GetMapping("/agentcore/runs/{sessionId}")
    public ResponseEntity<AgentRunEvent> getRunStatus(@PathVariable String sessionId) {
        AgentRunEvent event = agentCoreAdapter.getRunStatus(sessionId);
        return event == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(event);
    }

    @GetMapping(value = "/agentcore/runs/{sessionId}/streamEvents", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(
            @PathVariable String sessionId,
            @RequestParam(value = "afterSequence", required = false) Long afterSequence,
            @RequestHeader(value = "Content-Type", required = false) String contentType) {

        SseEmitter emitter = new SseEmitter(30_000L);
        try {
            for (AgentRunEvent event : agentCoreAdapter.streamEvents(sessionId, afterSequence)) {
                for (Map<String, Object> chunk : chunks(event)) {
                    emitter.send(SseEmitter.event().data(chunk));
                }
            }
            emitter.complete();
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    private List<Map<String, Object>> chunks(AgentRunEvent event) {
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        if ("RUN_SUCCEEDED".equals(event.getType())) {
            Map<String, Object> chat = base(event, event.getEventId() + ":chat", "chat");
            chat.put("content", event.getPayload().get("resultText"));
            chat.put("attachments", java.util.Collections.emptyList());
            result.add(chat);
            result.add(base(event, event.getEventId(), "end"));
            return result;
        }
        String type = "RUN_WAITING_HUMAN".equals(event.getType()) ? "confirm"
                : "RUN_FAILED".equals(event.getType()) ? "error" : "liveStatus";
        Map<String, Object> chunk = base(event, event.getEventId(), type);
        chunk.put("content", event.getMessage());
        if ("confirm".equals(type)) {
            chunk.put("questionId", event.getPayload().get("questionId"));
            chunk.put("questions", java.util.Collections.singletonList(
                    java.util.Collections.singletonMap(
                            "question", event.getPayload().get("question"))));
        }
        result.add(chunk);
        return result;
    }

    private Map<String, Object> base(
            AgentRunEvent event, String eventId, String type) {
        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("type", type);
        chunk.put("sessionId", event.getSessionId());
        chunk.put("timestamp", System.currentTimeMillis());
        chunk.put("eventId", eventId);
        return chunk;
    }
}
