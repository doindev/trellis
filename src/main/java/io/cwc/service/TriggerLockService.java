package io.cwc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cwc.entity.TriggerLockEntity;
import io.cwc.repository.TriggerLockRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages distributed trigger locks using the database.
 * Ensures only one instance in a cluster executes a given trigger at a time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TriggerLockService {

    private static final Duration LOCK_TTL = Duration.ofMinutes(2);

    private final TriggerLockRepository triggerLockRepository;

    private final String instanceId = UUID.randomUUID().toString();

    public String getInstanceId() {
        return instanceId;
    }

    /**
     * Attempts to acquire a lock for the given workflow+node combination.
     * Returns true if this instance now holds the lock.
     */
    @Transactional
    public boolean tryAcquire(String workflowId, String nodeId) {
        Instant now = Instant.now();
        Optional<TriggerLockEntity> existing = triggerLockRepository.findByWorkflowIdAndNodeId(workflowId, nodeId);

        if (existing.isPresent()) {
            TriggerLockEntity lock = existing.get();

            // Already held by us — renew
            if (instanceId.equals(lock.getInstanceId())) {
                lock.setLastHeartbeat(now);
                lock.setExpiresAt(now.plus(LOCK_TTL));
                triggerLockRepository.save(lock);
                return true;
            }

            // Held by another instance — check if expired
            if (lock.getExpiresAt().isBefore(now)) {
                log.info("Taking over expired trigger lock for workflow={} node={} from instance={}",
                        workflowId, nodeId, lock.getInstanceId());
                lock.setInstanceId(instanceId);
                lock.setAcquiredAt(now);
                lock.setLastHeartbeat(now);
                lock.setExpiresAt(now.plus(LOCK_TTL));
                triggerLockRepository.save(lock);
                return true;
            }

            // Held by another active instance
            return false;
        }

        // No lock exists — create one
        try {
            TriggerLockEntity lock = TriggerLockEntity.builder()
                    .workflowId(workflowId)
                    .nodeId(nodeId)
                    .instanceId(instanceId)
                    .acquiredAt(now)
                    .lastHeartbeat(now)
                    .expiresAt(now.plus(LOCK_TTL))
                    .build();
            triggerLockRepository.save(lock);
            log.debug("Acquired trigger lock for workflow={} node={}", workflowId, nodeId);
            return true;
        } catch (Exception e) {
            // Unique constraint violation — another instance won the race
            log.debug("Failed to acquire trigger lock (race condition) for workflow={} node={}", workflowId, nodeId);
            return false;
        }
    }

    @Transactional
    public void release(String workflowId, String nodeId) {
        triggerLockRepository.findByWorkflowIdAndNodeId(workflowId, nodeId).ifPresent(lock -> {
            if (instanceId.equals(lock.getInstanceId())) {
                triggerLockRepository.delete(lock);
                log.debug("Released trigger lock for workflow={} node={}", workflowId, nodeId);
            }
        });
    }

    @Transactional
    public void releaseAllForWorkflow(String workflowId) {
        triggerLockRepository.deleteByWorkflowId(workflowId);
    }

    /**
     * Renews all locks held by this instance, extending their expiry.
     */
    @Transactional
    public void heartbeat() {
        Instant now = Instant.now();
        Instant newExpiry = now.plus(LOCK_TTL);
        int renewed = triggerLockRepository.renewHeartbeat(instanceId, now, newExpiry);
        if (renewed > 0) {
            log.debug("Renewed {} trigger lock(s) for instance {}", renewed, instanceId);
        }
    }

    /**
     * Deletes locks whose expiry has passed (from crashed instances).
     */
    @Transactional
    public int cleanupExpired() {
        int deleted = triggerLockRepository.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired trigger lock(s)", deleted);
        }
        return deleted;
    }
}
