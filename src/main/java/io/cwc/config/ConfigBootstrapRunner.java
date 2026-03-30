package io.cwc.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.cwc.service.ConfigBootstrapService;
import io.cwc.service.GitSyncService;

/**
 * Runs the config bootstrap after DataSeeder (Order 1) and GitSyncRunner (Order 2).
 * Reads config files from cwc.config.paths, merges, resolves placeholders, and seeds/syncs to DB.
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class ConfigBootstrapRunner implements CommandLineRunner {

    private final ConfigBootstrapService configBootstrapService;
    private final CwcConfigProperties configProperties;
    private final GitSyncService gitSyncService;

    @Override
    public void run(String... args) {
        logStartupType();

        if (!configProperties.isEnabled()) {
            log.debug("Config bootstrap disabled (no cwc.config.paths configured)");
            return;
        }
        try {
            configBootstrapService.loadAndApply();
        } catch (Exception e) {
            // Never fail startup — log and continue
            log.error("Config bootstrap failed: {}", e.getMessage(), e);
        }
    }

    private void logStartupType() {
        boolean git = gitSyncService.isEnabled();
        boolean localPaths = configProperties.isEnabled();
        boolean writeback = configProperties.isWritebackEnabled();

        String type;
        if (git && localPaths) {
            type = "HYBRID (local + git)";
        } else if (git && !localPaths) {
            type = "GIT-ONLY";
        } else if (!git && localPaths) {
            type = "LOCAL" + (writeback ? " (with writeback)" : "");
        } else {
            type = "NONE (no config source)";
        }
        log.info("CWC startup type: {} | config.paths={} | config.mode={} | config.writeback={} | git.enabled={}",
                type, configProperties.getConfigPaths(), configProperties.getConfigMode(),
                writeback, git);
    }
}
