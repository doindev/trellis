package io.cwc.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import io.cwc.config.CwcConfigProperties.ConfigMode;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

/**
 * Polls the configured git repository at a fixed interval and reloads
 * config into the database when new commits are detected.
 *
 * Activated when cwc.git.enabled=true and cwc.git.poll-interval > 0 (seconds).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitPollScheduler {

    private final GitSyncService gitSyncService;
    private final ConfigBootstrapService configBootstrapService;
    private final TaskScheduler taskScheduler;

    private ScheduledFuture<?> pollFuture;

    @PostConstruct
    public void init() {
        if (!gitSyncService.isEnabled()) return;

        long intervalSeconds = gitSyncService.getPollIntervalSeconds();
        if (intervalSeconds <= 0) {
            log.debug("Git polling disabled (cwc.git.poll-interval={})", intervalSeconds);
            return;
        }

        log.info("Git polling enabled: interval={}s", intervalSeconds);
        pollFuture = taskScheduler.scheduleWithFixedDelay(this::pollAndReload,
                Duration.ofSeconds(intervalSeconds));
    }

    @PreDestroy
    public void shutdown() {
        if (pollFuture != null) {
            pollFuture.cancel(false);
            log.info("Git polling stopped");
        }
    }

    private void pollAndReload() {
        try {
            boolean changed = gitSyncService.syncAndDetectChanges();
            if (changed) {
                log.info("Git poll: changes detected, reloading config");
                var result = configBootstrapService.loadAndApply(ConfigMode.SYNC, false);
                log.info("Git poll reload complete: {} projects created, {} updated, {} workflows created, {} updated",
                        result.getProjectsCreated(), result.getProjectsUpdated(),
                        result.getWorkflowsCreated(), result.getWorkflowsUpdated());
            }
        } catch (Exception e) {
            log.error("Git poll cycle failed: {}", e.getMessage(), e);
        }
    }
}
