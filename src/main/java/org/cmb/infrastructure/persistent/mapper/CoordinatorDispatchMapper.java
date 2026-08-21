package org.cmb.infrastructure.persistent.mapper;

import java.sql.Timestamp;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.cmb.application.domain.DispatchWork;

/**
 * SQL access for the dispatch queue (digital_team_coordinator_dispatch).
 * Join queries use this table as the main table. Queries that may match
 * multiple rows return {@code List} so the repository facade keeps its
 * "first row or null" semantics.
 */
@Mapper
public interface CoordinatorDispatchMapper {

    List<String> selectClaimableDispatchId();

    int claimDispatch(
            @Param("owner") String owner,
            @Param("leaseExpiresAt") Timestamp leaseExpiresAt,
            @Param("dispatchId") String dispatchId);

    int renewLease(
            @Param("dispatchId") String dispatchId,
            @Param("owner") String owner,
            @Param("leaseExpiresAt") Timestamp leaseExpiresAt);

    List<DispatchWork> loadWork(@Param("dispatchId") String dispatchId);

    List<String> findDispatchForConversation(@Param("conversationId") String conversationId);

    int completeDispatch(
            @Param("dispatchId") String dispatchId,
            @Param("status") String status,
            @Param("error") String error);

    int releaseDispatch(@Param("dispatchId") String dispatchId);

    int insertDispatch(
            @Param("id") String id,
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("conversationId") String conversationId,
            @Param("messageId") String messageId);

    int failDispatch(
            @Param("status") String status,
            @Param("error") String error,
            @Param("tenantId") String tenantId,
            @Param("planId") String planId);

    int resetDispatchPending(
            @Param("dispatchId") String dispatchId,
            @Param("tenantId") String tenantId);
}
