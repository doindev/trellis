package io.cwc.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.cwc.config.CwcConfigProperties.ConfigMode;
import io.cwc.config.CwcProperties;
import io.cwc.dto.ConfigReloadResult;
import io.cwc.exception.ForbiddenException;
import io.cwc.entity.ProjectRelationEntity;
import io.cwc.entity.ProjectRelationEntity.ProjectRole;
import io.cwc.repository.ProjectRelationRepository;
import io.cwc.service.ConfigBootstrapService;
import io.cwc.service.GitPushProvider;
import io.cwc.service.GitSyncProvider;
import io.cwc.util.SecurityContextHelper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

/**
 * Administrative endpoints for config management and cluster operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ConfigBootstrapService configBootstrapService;
    private final Optional<GitPushProvider> gitPushProvider;
    private final Optional<GitSyncProvider> gitSyncProvider;
    private final CwcProperties cwcProperties;
    private final SecurityContextHelper securityContextHelper;
    private final ProjectRelationRepository projectRelationRepository;

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
        if (!cwcProperties.isAllowNonOwnerChanges()) {
            String userId = securityContextHelper.getCurrentUserId();
            ProjectRole role = projectRelationRepository.findByProjectIdAndUserId(id, userId)
                    .map(ProjectRelationEntity::getRole)
                    .orElse(null);
            if (role != ProjectRole.PROJECT_PERSONAL_OWNER && role != ProjectRole.PROJECT_ADMIN) {
                throw new ForbiddenException("Promoting projects requires owner/admin role or cwc.allow-non-owner-changes=true");
            }
        }
        return gitPushProvider.orElseThrow(() -> new io.cwc.exception.ServiceUnavailableException("Git module not available")).promote(id, request.getTargetBranch(), request.getCommitMessage());
    }

    /**
     * Receives a webhook from GitHub or GitLab when commits are pushed to the config repository.
     * Validates the webhook signature, checks the branch matches, then syncs git and reloads config.
     *
     * GitHub: validates X-Hub-Signature-256 header (HMAC-SHA256 of body with cwc.git.webhook-secret)
     * GitLab: validates X-Gitlab-Token header (direct string comparison with cwc.git.webhook-secret)
     * Bitbucket: validates X-Hub-Signature header (HMAC-SHA256, same as GitHub style)
     */
    @PostMapping("/git-webhook")
    public ResponseEntity<Map<String, Object>> gitWebhook(
            @RequestBody String body,
            HttpServletRequest request) {

        String secret = gitSyncProvider.orElseThrow(() -> new io.cwc.exception.ServiceUnavailableException("Git module not available")).getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("Git webhook received but cwc.git.webhook-secret is not configured");
            return ResponseEntity.status(403).body(Map.of("error", "Webhook secret not configured"));
        }

        if (!gitSyncProvider.orElseThrow(() -> new io.cwc.exception.ServiceUnavailableException("Git module not available")).isEnabled()) {
            return ResponseEntity.status(503).body(Map.of("error", "Git sync is not enabled"));
        }

        // --- Signature validation ---
        boolean authenticated = false;

        // GitHub: X-Hub-Signature-256: sha256=<hex>
        String githubSig = request.getHeader("X-Hub-Signature-256");
        if (githubSig != null) {
            authenticated = verifyHmacSha256(body, secret, githubSig);
            if (!authenticated) {
                log.warn("Git webhook: GitHub signature verification failed");
                return ResponseEntity.status(403).body(Map.of("error", "Invalid signature"));
            }
        }

        // GitLab: X-Gitlab-Token: <secret>
        String gitlabToken = request.getHeader("X-Gitlab-Token");
        if (!authenticated && gitlabToken != null) {
            authenticated = MessageDigest.isEqual(
                    secret.getBytes(StandardCharsets.UTF_8),
                    gitlabToken.getBytes(StandardCharsets.UTF_8));
            if (!authenticated) {
                log.warn("Git webhook: GitLab token verification failed");
                return ResponseEntity.status(403).body(Map.of("error", "Invalid token"));
            }
        }

        // Bitbucket: X-Hub-Signature: sha256=<hex>
        String bitbucketSig = request.getHeader("X-Hub-Signature");
        if (!authenticated && bitbucketSig != null) {
            authenticated = verifyHmacSha256(body, secret, bitbucketSig);
            if (!authenticated) {
                log.warn("Git webhook: Bitbucket signature verification failed");
                return ResponseEntity.status(403).body(Map.of("error", "Invalid signature"));
            }
        }

        if (!authenticated) {
            log.warn("Git webhook: no recognized authentication header present");
            return ResponseEntity.status(403).body(Map.of("error", "No authentication header"));
        }

        // --- Branch check ---
        String targetBranch = gitSyncProvider.orElseThrow(() -> new io.cwc.exception.ServiceUnavailableException("Git module not available")).getBranch();
        if (!branchMatches(body, targetBranch)) {
            log.debug("Git webhook: push to non-tracked branch, ignoring");
            return ResponseEntity.ok(Map.of("status", "ignored", "reason", "branch mismatch"));
        }

        // --- Sync and reload ---
        log.info("Git webhook: valid push to '{}', syncing", targetBranch);
        boolean synced = gitSyncProvider.orElseThrow(() -> new io.cwc.exception.ServiceUnavailableException("Git module not available")).sync();
        if (!synced) {
            return ResponseEntity.status(500).body(Map.of("error", "Git sync failed"));
        }

        ConfigReloadResult result = configBootstrapService.loadAndApply(ConfigMode.SYNC, false);
        log.info("Git webhook reload complete: {} projects created, {} updated, {} workflows created, {} updated",
                result.getProjectsCreated(), result.getProjectsUpdated(),
                result.getWorkflowsCreated(), result.getWorkflowsUpdated());

        return ResponseEntity.ok(Map.of(
                "status", "reloaded",
                "projectsCreated", result.getProjectsCreated(),
                "projectsUpdated", result.getProjectsUpdated(),
                "workflowsCreated", result.getWorkflowsCreated(),
                "workflowsUpdated", result.getWorkflowsUpdated()));
    }

    /**
     * Verifies an HMAC-SHA256 signature of the form "sha256=<hex>".
     */
    private boolean verifyHmacSha256(String payload, String secret, String signatureHeader) {
        try {
            String prefix = "sha256=";
            if (!signatureHeader.startsWith(prefix)) return false;
            String expected = signatureHeader.substring(prefix.length());

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));

            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    hex.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("HMAC verification error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks whether the webhook payload indicates a push to the tracked branch.
     * Supports GitHub (ref: "refs/heads/main"), GitLab (ref: "main"), and Bitbucket (push.changes[].new.name).
     */
    private boolean branchMatches(String body, String targetBranch) {
        // Simple substring match — works for GitHub, GitLab, and most providers
        // GitHub: "ref":"refs/heads/main"
        if (body.contains("\"ref\":\"refs/heads/" + targetBranch + "\"")) return true;
        if (body.contains("\"ref\": \"refs/heads/" + targetBranch + "\"")) return true;
        // GitLab: "ref":"main"
        if (body.contains("\"ref\":\"" + targetBranch + "\"")) return true;
        if (body.contains("\"ref\": \"" + targetBranch + "\"")) return true;
        // Bitbucket: "name":"main" in changes[].new
        if (body.contains("\"name\":\"" + targetBranch + "\"")) return true;
        if (body.contains("\"name\": \"" + targetBranch + "\"")) return true;
        // If we can't parse the branch, proceed anyway (better to sync than miss)
        if (!body.contains("\"ref\"") && !body.contains("\"name\"")) return true;
        return false;
    }

    @Data
    public static class PromoteRequest {
        private String targetBranch;
        private String commitMessage;
    }
}
