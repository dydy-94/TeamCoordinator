package org.cmb.infrastructure.persistent;

import java.util.List;
import java.util.UUID;
import org.cmb.infrastructure.persistent.mapper.CoordinatorCliSubmissionMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * Persistence facade for CLI submissions. A submission is idempotent per
 * (task, kind): a re-submission overwrites the previous payload. Submissions
 * are deleted once consumed so a stale payload can never leak into the next
 * message of the same conversation.
 */
@Repository
public class CliSubmissionRepository {

    public static final String KIND_DECISION = "DECISION";
    public static final String KIND_PLAN = "PLAN";
    public static final String KIND_VERDICT = "VERDICT";

    private final CoordinatorCliSubmissionMapper mapper;

    public CliSubmissionRepository(CoordinatorCliSubmissionMapper mapper) {
        this.mapper = mapper;
    }

    public void save(String taskId, String kind, String payload) {
        try {
            mapper.insert("cli-sub-" + UUID.randomUUID(), taskId, kind, payload);
        } catch (DuplicateKeyException ex) {
            mapper.replace(taskId, kind, payload);
        }
    }

    public String find(String taskId, String kind) {
        List<String> rows = mapper.find(taskId, kind);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void delete(String taskId, String kind) {
        mapper.delete(taskId, kind);
    }
}
