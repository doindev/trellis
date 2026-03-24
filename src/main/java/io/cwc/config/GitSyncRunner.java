package io.cwc.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.cwc.service.GitSyncService;

/**
 * Runs git sync after DataSeeder (Order 1) but before ConfigBootstrapRunner (Order 3).
 * Clones or pulls config files from configured git repositories.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class GitSyncRunner implements CommandLineRunner {

    private final GitSyncService gitSyncService;
    private final CwcConfigProperties configProperties;

    @Override
    public void run(String... args) {
        if (!gitSyncService.isEnabled()) {
            log.debug("Git sync disabled (cwc.git.enabled=false or cwc.git.url not set)");
            return;
        }

        if (!gitSyncService.isSyncOnStartup()) {
            log.info("Git sync on startup disabled (cwc.git.sync-on-startup=false)");
            return;
        }

        try {
            log.info("Starting git sync...");
            boolean success = gitSyncService.sync();
            if (success) {
                // Prepend the git local path to config paths if not already there
                String gitPath = gitSyncService.getLocalPath();
                log.info("Git sync complete. Config files available at: {}", gitPath);
            } else {
                log.warn("Git sync failed — continuing with existing config files if available");
            }
        } catch (Exception e) {
            // Never fail startup
            log.error("Git sync error: {}", e.getMessage(), e);
        }
    }
}
