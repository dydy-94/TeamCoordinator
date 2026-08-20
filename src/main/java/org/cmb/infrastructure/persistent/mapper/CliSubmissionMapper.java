package org.cmb.infrastructure.persistent.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * SQL access for coordinator_cli_submission: structured Coordinator outputs
 * submitted by the companion CLI, keyed by the AgentCore session.
 */
@Mapper
public interface CliSubmissionMapper {

    int insert(
            @Param("businessId") String businessId,
            @Param("sessionId") String sessionId,
            @Param("kind") String kind,
            @Param("payload") String payload);

    /** Overwrite an existing submission for the same (session, kind). */
    int replace(
            @Param("sessionId") String sessionId,
            @Param("kind") String kind,
            @Param("payload") String payload);

    List<String> findBySession(@Param("sessionId") String sessionId);

    List<String> find(
            @Param("sessionId") String sessionId,
            @Param("kind") String kind);
}
