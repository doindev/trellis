package io.cwc.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.cwc.service.ConfigBootstrapService;

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

    @Override
    public void run(String... args) {
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
}
