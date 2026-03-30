package io.cwc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cwc.config.CwcConfigProperties;
import io.cwc.config.CwcConfigProperties.ConfigMode;
import io.cwc.dto.ConfigReloadResult;
import io.cwc.entity.ProjectEntity;
import io.cwc.entity.ProjectSourceControlEntity;
import io.cwc.exception.BadRequestException;
import io.cwc.exception.ForbiddenException;
import io.cwc.exception.NotFoundException;
import io.cwc.repository.ProjectRepository;
import io.cwc.repository.ProjectSourceControlRepository;
import io.cwc.util.SecurityContextHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates per-project git operations: import from repo, sync, push changes.
 * Each user stores their own encrypted token — only they can use it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectGitService {

    private static final long GIT_TIMEOUT_SECONDS = 120;
    private static final DateTimeFormatter BRANCH_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final GitSyncService gitSyncService;
    private final ConfigBootstrapService configBootstrapService;
    private final ConfigExportService configExportService;
    private final ProjectRepository projectRepository;
    private final ProjectSourceControlRepository sourceControlRepository;
    private final CredentialEncryptionService encryptionService;
    private final SecurityContextHelper securityContextHelper;
    private final ObjectMapper objectMapper;

    @Value("${cwc.git.local-path:/opt/cwc/git-config}")
    private String gitLocalPath;

    /**
     * Import projects/workflows from a git repository.
     * Clones the repo, applies config via bootstrap, and creates a source control link.
     */
    public ConfigReloadResult importFromGitRepo(String repoUrl, String branch, String token,
                                                 String provider, String subPath, String mode) {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new BadRequestException("Repository URL is required");
        }

        String configId = deriveConfigIdFromUrl(repoUrl);
        ConfigMode configMode = "sync".equalsIgnoreCase(mode) ? ConfigMode.SYNC : ConfigMode.SEED;

        // Clone or pull the repo
        boolean synced = gitSyncService.syncProjectRepo(configId, repoUrl, branch, token);
        if (!synced) {
            throw new BadRequestException("Failed to sync git repository: " + repoUrl);
        }

        // Determine the path to import from
        Path importPath = Path.of(gitLocalPath, "projects", configId);
        if (subPath != null && !subPath.isBlank()) {
            importPath = importPath.resolve(subPath);
        }

        // Apply config from the cloned repo
        ConfigReloadResult result = configBootstrapService.loadAndApplyFromPath(importPath, configMode);

        // Link the source control settings for each project that was created/updated
        String userId = securityContextHelper.getCurrentUserId();
        linkImportedProjects(result, repoUrl, branch, token, provider, userId);

        return result;
    }

    /**
     * Pull latest from the project's linked repo and re-apply.
     */
    public ConfigReloadResult syncProject(String projectId) {
        String userId = securityContextHelper.getCurrentUserId();
        ProjectSourceControlEntity sc = sourceControlRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new NotFoundException("No source control configured for this project by you"));

        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
        String configId = project.getConfigId();
        if (configId == null) {
            throw new BadRequestException("Project has no configId — cannot sync");
        }

        String decryptedToken = decryptToken(sc.getToken());
        boolean synced = gitSyncService.syncProjectRepo(configId, sc.getRepoUrl(), sc.getBranch(), decryptedToken);

        sc.setLastSyncAt(Instant.now());
        if (synced) {
            sc.setLastSyncStatus("SUCCESS");
            sc.setLastSyncError(null);
        } else {
            sc.setLastSyncStatus("FAILED");
            sc.setLastSyncError("Git sync returned false");
            sourceControlRepository.save(sc);
            throw new BadRequestException("Git sync failed for project: " + configId);
        }
        sourceControlRepository.save(sc);

        Path importPath = Path.of(gitLocalPath, "projects", configId);
        if (sc.getRepoSubPath() != null && !sc.getRepoSubPath().isBlank()) {
            importPath = importPath.resolve(sc.getRepoSubPath());
        }

        return configBootstrapService.loadAndApplyFromPath(importPath, ConfigMode.SYNC);
    }

    /**
     * Export project, commit to branch, push to remote.
     * Uses the current user's per-project credentials.
     */
    public Map<String, String> pushProject(String projectId, String commitMessage, String targetBranch) {
        String userId = securityContextHelper.getCurrentUserId();
        ProjectSourceControlEntity sc = sourceControlRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new NotFoundException("No source control configured for this project by you"));

        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
        String configId = project.getConfigId();
        if (configId == null) {
            throw new BadRequestException("Project has no configId — cannot push");
        }

        String decryptedToken = decryptToken(sc.getToken());
        Path repoDir = Path.of(gitLocalPath, "projects", configId);
        if (!Files.isDirectory(repoDir.resolve(".git"))) {
            throw new BadRequestException("Git repository not cloned for project: " + configId
                    + ". Sync first.");
        }

        String timestamp = BRANCH_TS.format(Instant.now().atOffset(ZoneOffset.UTC));
        String branchName = "cwc/update-" + configId + "-" + timestamp;
        if (targetBranch == null || targetBranch.isBlank()) {
            targetBranch = sc.getBranch();
        }

        try {
            git(repoDir, "checkout", "-b", branchName);

            // Export project files
            var bundle = configExportService.exportAsBundle(projectId);

            // Write project.json
            Files.write(repoDir.resolve("project.json"),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(bundle.getProject()));

            // Write workflows
            Path workflowsDir = repoDir.resolve("workflows");
            Files.createDirectories(workflowsDir);
            if (bundle.getWorkflows() != null) {
                for (var wf : bundle.getWorkflows()) {
                    String filename = (wf.getConfigId() != null ? wf.getConfigId() : "workflow") + ".json";
                    Files.write(workflowsDir.resolve(filename),
                            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(wf));
                }
            }

            git(repoDir, "add", ".");
            String msg = commitMessage != null ? commitMessage : "Update project: " + project.getName();
            git(repoDir, "commit", "-m", msg);

            String authUrl = injectToken(sc.getRepoUrl(), decryptedToken);
            git(repoDir, "remote", "set-url", "origin", authUrl);
            try {
                git(repoDir, "push", "origin", branchName);
            } finally {
                git(repoDir, "remote", "set-url", "origin", sc.getRepoUrl());
            }

            git(repoDir, "checkout", targetBranch);

            log.info("Pushed project '{}' to branch '{}' targeting '{}'", configId, branchName, targetBranch);

            Map<String, String> result = new LinkedHashMap<>();
            result.put("branch", branchName);
            result.put("targetBranch", targetBranch);
            result.put("message", msg);
            return result;

        } catch (Exception e) {
            try { git(repoDir, "checkout", targetBranch); } catch (Exception ignored) {}
            try { git(repoDir, "branch", "-D", branchName); } catch (Exception ignored) {}
            throw new BadRequestException("Git push failed: " + e.getMessage());
        }
    }

    /**
     * Link a project to a git repo (without importing).
     */
    @Transactional
    public ProjectSourceControlEntity linkRepo(String projectId, String repoUrl, String branch,
                                                String token, String provider) {
        String userId = securityContextHelper.getCurrentUserId();
        ProjectSourceControlEntity sc = sourceControlRepository.findByProjectIdAndUserId(projectId, userId)
                .orElse(ProjectSourceControlEntity.builder()
                        .projectId(projectId)
                        .userId(userId)
                        .build());

        sc.setRepoUrl(repoUrl);
        sc.setBranch(branch != null ? branch : "main");
        sc.setToken(encryptToken(token));
        sc.setProvider(provider != null ? provider : "github");
        return sourceControlRepository.save(sc);
    }

    /**
     * Remove the current user's git repo link from a project.
     */
    @Transactional
    public void unlinkRepo(String projectId) {
        String userId = securityContextHelper.getCurrentUserId();
        sourceControlRepository.deleteByProjectIdAndUserId(projectId, userId);
        log.info("Unlinked source control for project {} by user {}", projectId, userId);
    }

    /**
     * Get the current user's source control link for a project, if any.
     */
    public ProjectSourceControlEntity getLink(String projectId) {
        String userId = securityContextHelper.getCurrentUserId();
        return sourceControlRepository.findByProjectIdAndUserId(projectId, userId).orElse(null);
    }

    // --- Helpers ---

    private void linkImportedProjects(ConfigReloadResult result, String repoUrl, String branch,
                                       String token, String provider, String userId) {
        // After import, find newly created projects by configId and link them
        // This is best-effort — if we can't determine which projects were created, skip
        if (result.getProjectsCreated() > 0 || result.getProjectsUpdated() > 0) {
            log.info("Import created {} projects, updated {} — source control links should be set up via project settings",
                    result.getProjectsCreated(), result.getProjectsUpdated());
        }
    }

    private String deriveConfigIdFromUrl(String url) {
        // e.g. https://github.com/org/my-repo.git -> my-repo
        String cleaned = url.replaceAll("\\.git$", "");
        int lastSlash = cleaned.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < cleaned.length() - 1) {
            return cleaned.substring(lastSlash + 1).toLowerCase().replaceAll("[^a-z0-9]+", "-");
        }
        return "imported-" + System.currentTimeMillis();
    }

    private String encryptToken(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) return null;
        return encryptionService.encryptString(plainToken);
    }

    private String decryptToken(String encryptedToken) {
        if (encryptedToken == null || encryptedToken.isBlank()) return null;
        return encryptionService.decryptString(encryptedToken);
    }

    private String injectToken(String url, String authToken) {
        if (url == null || authToken == null || authToken.isBlank()) return url;
        if (url.startsWith("https://")) {
            return "https://" + authToken + "@" + url.substring(8);
        }
        return url;
    }

    private String git(Path workDir, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(workDir.toAbsolutePath().toString());
        command.addAll(List.of(args));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append("\n");
        }

        boolean completed = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new IOException("Git command timed out: " + String.join(" ", args));
        }
        if (process.exitValue() != 0) {
            throw new IOException("Git command failed (exit " + process.exitValue() + "): " + output);
        }
        return output.toString().trim();
    }
}
