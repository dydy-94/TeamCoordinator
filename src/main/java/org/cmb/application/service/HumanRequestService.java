package org.cmb.application.service;

import org.cmb.application.domain.RequestIdentity;
import org.cmb.application.dto.HumanRequestView;
import org.cmb.application.dto.HumanResponseRequest;

/**
 * Human-in-the-loop: validates and applies human responses to coordinator
 * clarifications and expert questions, resuming or restarting agent runs;
 * also expires due requests on a schedule.
 */
public interface HumanRequestService {

    HumanRequestView respond(
            RequestIdentity identity,
            String projectId,
            String requestId,
            HumanResponseRequest request);

    void expireDueRequests();
}
