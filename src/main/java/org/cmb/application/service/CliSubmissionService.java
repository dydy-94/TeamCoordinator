package org.cmb.application.service;

import java.util.Map;
import org.cmb.application.dto.ArtifactView;

/**
 * Accepts structured Coordinator outputs submitted by the companion CLI.
 * Everything is keyed by the task id — the only identifier the AgentCore
 * runtime and the CLI reliably share.
 */
public interface CliSubmissionService {

    void submitDecision(String taskId, String payload);

    void submitPlan(String taskId, String payload);

    void submitVerdict(String taskId, String payload);

    Map<String, Object> getTaskDetail(String taskId);

    void submitResult(String taskId, String resultText);

    String askHuman(String taskId, String question);

    ArtifactView uploadArtifact(
            String taskId, String fileName, String mediaType, byte[] content);
}
