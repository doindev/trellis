package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.cwc.entity.TriggerLockEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TriggerLockRepository extends JpaRepository<TriggerLockEntity, String> {

    Optional<TriggerLockEntity> findByWorkflowIdAndNodeId(String workflowId, String nodeId);

    void deleteByWorkflowId(String workflowId);

    List<TriggerLockEntity> findByInstanceId(String instanceId);

    @Modifying
    @Query("DELETE FROM TriggerLockEntity t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);

    @Modifying
    @Query("UPDATE TriggerLockEntity t SET t.lastHeartbeat = :now, t.expiresAt = :newExpiry WHERE t.instanceId = :instanceId")
    int renewHeartbeat(@Param("instanceId") String instanceId, @Param("now") Instant now, @Param("newExpiry") Instant newExpiry);
}
