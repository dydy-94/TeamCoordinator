package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * SQL access for coordinator_cli_submission: structured Coordinator outputs
 * submitted by the companion CLI, keyed by the task id the CLI receives
 * from the Coordinator.
 */
@Mapper
public interface CoordinatorCliSubmissionMapper {

    int insert(
            @Param("businessId") String businessId,
            @Param("taskId") String taskId,
            @Param("kind") String kind,
            @Param("payload") String payload);

    /** Overwrite an existing submission for the same (task, kind). */
    int replace(
            @Param("taskId") String taskId,
            @Param("kind") String kind,
            @Param("payload") String payload);

    List<String> find(
            @Param("taskId") String taskId,
            @Param("kind") String kind);

    int delete(
            @Param("taskId") String taskId,
            @Param("kind") String kind);
}
