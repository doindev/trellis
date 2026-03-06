package io.cwc.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    private final TaskScheduler taskScheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public void registerCron(String workflowId, String cronExpression, Runnable task) {
        deregister(workflowId);
        try {
            ScheduledFuture<?> future = taskScheduler.schedule(task, new CronTrigger(cronExpression));
            scheduledTasks.put(workflowId, future);
            log.info("Registered cron schedule for workflow {}: {}", workflowId, cronExpression);
        } catch (Exception e) {
            log.error("Failed to register cron schedule for workflow {}: {}", workflowId, e.getMessage());
        }
    }

    public void registerInterval(String workflowId, long intervalSeconds, Runnable task) {
        deregister(workflowId);
        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(task, Duration.ofSeconds(intervalSeconds));
        scheduledTasks.put(workflowId, future);
        log.info("Registered interval schedule for workflow {}: {}s", workflowId, intervalSeconds);
    }

    public void deregister(String workflowId) {
        ScheduledFuture<?> existing = scheduledTasks.remove(workflowId);
        if (existing != null) {
            existing.cancel(false);
            log.info("Deregistered schedule for workflow {}", workflowId);
        }
    }

    /**
     * Deregisters all scheduled tasks whose key starts with the given prefix.
     * Used to remove all triggers for a workflow (keys are workflowId_nodeId).
     */
    public void deregisterByPrefix(String prefix) {
        scheduledTasks.keySet().removeIf(key -> {
            if (key.startsWith(prefix)) {
                ScheduledFuture<?> future = scheduledTasks.get(key);
                if (future != null) {
                    future.cancel(false);
                    log.info("Deregistered schedule: {}", key);
                }
                return true;
            }
            return false;
        });
    }

    public boolean isScheduled(String workflowId) {
        return scheduledTasks.containsKey(workflowId);
    }
}
