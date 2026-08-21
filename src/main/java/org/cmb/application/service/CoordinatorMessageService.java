package org.cmb.application.service;

import java.util.List;
import org.cmb.application.domain.RequestIdentity;
import org.cmb.application.domain.entity.MessageDO;
import org.cmb.application.domain.entity.ProjectEventDO;
import org.cmb.application.dto.MessageRequest;

/**
 * Message intake: persists the user message, emits the public userMessage
 * and coordinator-analyzing events, and enqueues a dispatch for the worker.
 */
public interface CoordinatorMessageService {

    MessageDO accept(
            RequestIdentity identity, String projectId, String taskId,
            MessageRequest request);

    void requireEventAccess(RequestIdentity identity, String projectId, String taskId);

    List<ProjectEventDO> replayAuthorized(
            RequestIdentity identity, String projectId, String taskId,
            long afterSequence);
}
