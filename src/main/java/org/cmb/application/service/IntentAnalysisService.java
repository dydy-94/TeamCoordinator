package org.cmb.application.service;

import java.util.function.Consumer;
import org.cmb.application.domain.AgentEvent;
import org.cmb.application.domain.CoordinatorDecision;
import org.cmb.application.domain.RequestIdentity;
import org.cmb.application.dto.IntentAnalysisRequest;

/**
 * Coordinator intent analysis: submits the user text to the Coordinator
 * agent, streams events and parses the structured decision.
 */
public interface IntentAnalysisService {

    CoordinatorDecision analyze(
            RequestIdentity identity, String projectId, IntentAnalysisRequest request);

    long sessionWatermarkExcluding(String sessionId, String messageId);

    CoordinatorDecision analyzeForDispatch(
            RequestIdentity identity, String projectId, String taskId,
            String messageId, String businessSessionId,
            String coordinatorSessionId,
            IntentAnalysisRequest request,
            Consumer<AgentEvent> eventSink);

    CoordinatorDecision analyzeForDispatch(
            RequestIdentity identity, String projectId, String taskId,
            String messageId, String businessSessionId,
            IntentAnalysisRequest request);
}
