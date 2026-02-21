package io.trellis.repository;

import io.trellis.entity.WaitEntity;
import io.trellis.entity.WaitEntity.WaitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface WaitRepository extends JpaRepository<WaitEntity, String> {

    Optional<WaitEntity> findByExecutionIdAndNodeId(String executionId, String nodeId);

    List<WaitEntity> findByStatusAndResumeAtBefore(WaitStatus status, Instant now);

    List<WaitEntity> findByExecutionId(String executionId);

    List<WaitEntity> findByStatus(WaitStatus status);

    /**
     * Atomically claim a wait for resume — only succeeds if status is still WAITING.
     * Returns the number of rows updated (0 or 1).
     */
    @Modifying
    @Query("UPDATE WaitEntity w SET w.status = ?4, w.resumedAt = ?3 " +
           "WHERE w.id = ?1 AND w.status = ?5 AND w.version = ?2")
    int claimForResume(String id, Long version, Instant resumedAt,
                       WaitStatus newStatus, WaitStatus requiredStatus);
}
