package io.cwc.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import io.cwc.config.CwcConfigProperties.ConfigMode;
import io.cwc.dto.ConfigReloadResult;
import io.cwc.service.ConfigBootstrapService;
import io.cwc.service.GitPushService;

import java.util.Map;

/**
 * Administrative endpoints for config management and cluster operations.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ConfigBootstrapService configBootstrapService;
    private final GitPushService gitPushService;

    /**
     * Re-applies config files from cwc.config.paths to the database.
     */
    @PostMapping("/reload-config")
    public ConfigReloadResult reloadConfig(
            @RequestParam(required = false) String mode,
            @RequestParam(required = false, defaultValue = "false") boolean adoptOrphans) {
        ConfigMode modeOverride = null;
        if (mode != null) {
            modeOverride = "sync".equalsIgnoreCase(mode) ? ConfigMode.SYNC : ConfigMode.SEED;
        }
        return configBootstrapService.loadAndApply(modeOverride, adoptOrphans);
    }

    /**
     * Promote a project: export to files, push to branch, optionally create PR.
     */
    @PostMapping("/projects/{id}/promote")
    public Map<String, String> promoteProject(
            @PathVariable String id,
            @RequestBody PromoteRequest request) {
        return gitPushService.promote(id, request.getTargetBranch(), request.getCommitMessage());
    }

    @Data
    public static class PromoteRequest {
        private String targetBranch;
        private String commitMessage;
    }
}
