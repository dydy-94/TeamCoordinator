package org.cmb.application.service;

import java.util.List;
import org.cmb.application.domain.RequestIdentity;
import org.cmb.application.domain.entity.ConversationDO;
import org.cmb.application.dto.CreateConversationTaskRequest;

/**
 * Conversation-task lifecycle: create, list, require and full cascade
 * deletion (including AgentCore-side sessions).
 */
public interface ConversationTaskService {

    ConversationDO create(
            RequestIdentity identity, String projectId,
            CreateConversationTaskRequest request);

    List<ConversationDO> list(RequestIdentity identity, String projectId);

    void delete(RequestIdentity identity, String projectId, String taskId);

    ConversationDO require(RequestIdentity identity, String projectId, String taskId);
}
