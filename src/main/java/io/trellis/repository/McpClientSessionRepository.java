package io.trellis.repository;

import io.trellis.entity.McpClientSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface McpClientSessionRepository extends JpaRepository<McpClientSessionEntity, String> {

    @Transactional
    void deleteByLastSeenAtBefore(Instant cutoff);

    @Modifying
    @Transactional
    @Query("UPDATE McpClientSessionEntity e SET e.disconnectedAt = :now " +
           "WHERE e.instanceId = :instanceId AND e.disconnectedAt IS NULL")
    void disconnectStaleByInstanceId(String instanceId, Instant now);

    List<McpClientSessionEntity> findByLastSeenAtAfterOrderByDisconnectedAtAscConnectedAtDesc(Instant cutoff);
}
