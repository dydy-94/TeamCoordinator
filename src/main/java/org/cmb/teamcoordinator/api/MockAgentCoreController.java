package org.cmb.teamcoordinator.api;

import java.util.List;
import javax.validation.Valid;
import org.cmb.teamcoordinator.agentcore.AgentCoreAdapter;
import org.cmb.teamcoordinator.agentcore.AgentRunEvent;
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

    public MockAgentCoreController(AgentCoreAdapter agentCoreAdapter, ExpertRegistry expertRegistry) {
        this.agentCoreAdapter = agentCoreAdapter;
        this.expertRegistry = expertRegistry;
    }

    @GetMapping("/experts")
    public List<ExpertDescriptor> listExperts() {
        return expertRegistry.listExperts();
    }

    @PostMapping("/agentcore/runs")
    public ResponseEntity<AgentRunResponse> submitRun(@Valid @RequestBody AgentRunRequest request) {
        return ResponseEntity.accepted().body(agentCoreAdapter.submitRun(request));
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
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(event.getSequence()))
                        .name(event.getType())
                        .data(event));
            }
            emitter.complete();
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }
}
