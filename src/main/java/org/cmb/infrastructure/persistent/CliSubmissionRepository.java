package org.cmb.infrastructure.persistent;

import java.util.List;
import java.util.UUID;
import org.cmb.infrastructure.persistent.mapper.CliSubmissionMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * Persistence facade for CLI submissions. A submission is idempotent per
 * (session, kind): a re-submission overwrites the previous payload.
 */
@Repository
public class CliSubmissionRepository {

    public static final String KIND_DECISION = "DECISION";
    public static final String KIND_PLAN = "PLAN";
    public static final String KIND_VERDICT = "VERDICT";

    private final CliSubmissionMapper mapper;

    public CliSubmissionRepository(CliSubmissionMapper mapper) {
        this.mapper = mapper;
    }

    public void save(String sessionId, String kind, String payload) {
        try {
            mapper.insert("cli-sub-" + UUID.randomUUID(), sessionId, kind, payload);
        } catch (DuplicateKeyException ex) {
            mapper.replace(sessionId, kind, payload);
        }
    }

    /** Latest payload for the given session, if any. */
    public String findBySession(String sessionId) {
        List<String> rows = mapper.findBySession(sessionId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public String find(String sessionId, String kind) {
        List<String> rows = mapper.find(sessionId, kind);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
