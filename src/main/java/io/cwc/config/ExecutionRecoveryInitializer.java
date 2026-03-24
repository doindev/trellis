package io.cwc.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.cwc.entity.ExecutionEntity;
import io.cwc.entity.ExecutionEntity.ExecutionStatus;
import io.cwc.repository.ExecutionRepository;
import io.cwc.service.TriggerLockService;

import java.time.Instant;
import java.util.List;

/**
 * On startup, scans for executions stuck in RUNNING status from dead instances
 * and marks them as ERROR. Runs before TriggerStartupInitializer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionRecoveryInitializer {

    private final ExecutionRepository executionRepository;
    private final TriggerLockService triggerLockService;

    @EventListener(ApplicationReadyEvent.class)
    @Order(1) // Before TriggerStartupInitializer
    public void recoverOrphanedExecutions() {
        List<ExecutionEntity> stuck = executionRepository.findByStatus(ExecutionStatus.RUNNING);
        if (stuck.isEmpty()) return;

        String myInstanceId = triggerLockService.getInstanceId();
        int recovered = 0;

        for (ExecutionEntity exec : stuck) {
            String execInstanceId = exec.getInstanceId();

            // If no instance ID recorded, or it's from a different instance, mark as failed
            if (execInstanceId == null || !execInstanceId.equals(myInstanceId)) {
                exec.setStatus(ExecutionStatus.ERROR);
                exec.setErrorMessage("Execution interrupted: hosting pod terminated unexpectedly");
                exec.setFinishedAt(Instant.now());
                executionRepository.save(exec);
                recovered++;
                log.warn("Marked orphaned execution {} as ERROR (instance: {})", exec.getId(), execInstanceId);
            }
        }

        if (recovered > 0) {
            log.info("Recovered {} orphaned execution(s) from dead instances", recovered);
        }
    }
}
