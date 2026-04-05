package io.cwc.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.cwc.dto.AiSettingsDto;
import io.cwc.entity.WorkflowEntity;
import io.cwc.repository.WorkflowRepository;
import io.cwc.service.AiSettingsService;
import io.cwc.service.ConfigBootstrapService;
import io.cwc.service.GitSyncProvider;

import java.util.Optional;

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
    private final Optional<GitSyncProvider> gitSyncProvider;
    private final AiSettingsService aiSettingsService;
    private final WorkflowRepository workflowRepository;

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

        // Auto-set default agent if none is configured
        setDefaultAgentIfNeeded();
    }

    private void setDefaultAgentIfNeeded() {
        AiSettingsDto settings = aiSettingsService.getSettings();
        if (settings.getDefaultAgentId() != null) return;

        workflowRepository.findAll().stream()
                .filter(w -> "AGENT".equals(w.getType()))
                .map(WorkflowEntity::getId)
                .findFirst()
                .ifPresent(agentId -> {
                    settings.setDefaultAgentId(agentId);
                    settings.setEnabled(true);
                    aiSettingsService.saveSettings(settings);
                    log.info("Auto-set default AI agent to: {}", agentId);
                });
    }

    private void logStartupType() {
        boolean git = gitSyncProvider.map(GitSyncProvider::isEnabled).orElse(false);
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
