package io.trellis.service;

import io.trellis.entity.WaitEntity;
import io.trellis.entity.WaitEntity.WaitStatus;
import io.trellis.entity.WaitEntity.WaitType;
import io.trellis.repository.WaitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitService {

    private final WaitRepository waitRepository;

    @Transactional
    public WaitEntity createWait(String executionId, String workflowId, String nodeId,
                                  WaitType waitType, Instant resumeAt,
                                  Object formDefinition, Object checkpointState) {
        WaitEntity entity = WaitEntity.builder()
                .executionId(executionId)
                .workflowId(workflowId)
                .nodeId(nodeId)
                .waitType(waitType)
                .resumeAt(resumeAt)
                .formDefinition(formDefinition)
                .executionState(checkpointState)
                .status(WaitStatus.WAITING)
                .createdAt(Instant.now())
                .build();
        WaitEntity saved = waitRepository.save(entity);
        log.info("Created wait: executionId={}, nodeId={}, type={}", executionId, nodeId, waitType);
        return saved;
    }

    /**
     * Resume a wait. Returns the WaitEntity if successfully claimed, null if already resumed.
     * Uses optimistic locking to prevent duplicate resumes across instances.
     */
    @Transactional
    public WaitEntity resume(String executionId, String nodeId) {
        WaitEntity entity = waitRepository.findByExecutionIdAndNodeId(executionId, nodeId)
                .orElse(null);
        if (entity == null || entity.getStatus() != WaitStatus.WAITING) {
            log.warn("No pending wait found for executionId={}, nodeId={}", executionId, nodeId);
            return null;
        }

        int updated = waitRepository.claimForResume(entity.getId(), entity.getVersion(), Instant.now(),
                WaitStatus.RESUMED, WaitStatus.WAITING);
        if (updated == 0) {
            log.warn("Wait already claimed: executionId={}, nodeId={}", executionId, nodeId);
            return null;
        }

        // Re-fetch to get the updated entity
        return waitRepository.findById(entity.getId()).orElse(null);
    }

    public WaitEntity getWaitEntry(String executionId, String nodeId) {
        return waitRepository.findByExecutionIdAndNodeId(executionId, nodeId)
                .filter(w -> w.getStatus() == WaitStatus.WAITING)
                .orElse(null);
    }

    @Transactional
    public void cancelWait(String executionId, String nodeId) {
        waitRepository.findByExecutionIdAndNodeId(executionId, nodeId)
                .filter(w -> w.getStatus() == WaitStatus.WAITING)
                .ifPresent(w -> {
                    w.setStatus(WaitStatus.CANCELLED);
                    w.setResumedAt(Instant.now());
                    waitRepository.save(w);
                    log.info("Cancelled wait: executionId={}, nodeId={}", executionId, nodeId);
                });
    }

    @Transactional
    public void cancelAllForExecution(String executionId) {
        List<WaitEntity> waits = waitRepository.findByExecutionId(executionId);
        for (WaitEntity w : waits) {
            if (w.getStatus() == WaitStatus.WAITING) {
                w.setStatus(WaitStatus.CANCELLED);
                w.setResumedAt(Instant.now());
                waitRepository.save(w);
            }
        }
        log.info("Cancelled all waits for executionId={}", executionId);
    }

    /**
     * Find all time-based waits that have elapsed and are still in WAITING status.
     */
    public List<WaitEntity> findExpiredWaits() {
        return waitRepository.findByStatusAndResumeAtBefore(WaitStatus.WAITING, Instant.now());
    }

    /**
     * Find all waits in WAITING status (for startup recovery).
     */
    public List<WaitEntity> findAllPendingWaits() {
        return waitRepository.findByStatus(WaitStatus.WAITING);
    }
}
