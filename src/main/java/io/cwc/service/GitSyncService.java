package io.cwc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ProcessBuilder-based git clone/pull service.
 * Syncs config files from a git repository to a local directory.
 */
@Slf4j
@Service
public class GitSyncService {

    private static final long GIT_TIMEOUT_SECONDS = 120;

    @Value("${cwc.git.enabled:false}")
    private boolean enabled;

    @Value("${cwc.git.url:}")
    private String repoUrl;

    @Value("${cwc.git.branch:main}")
    private String branch;

    @Value("${cwc.git.token:}")
    private String token;

    @Value("${cwc.git.local-path:/opt/cwc/git-config}")
    private String localPath;

    @Value("${cwc.git.sync-on-startup:true}")
    private boolean syncOnStartup;

    @Value("${cwc.git.poll-interval:0}")
    private long pollIntervalSeconds;

    @Value("${cwc.git.webhook-secret:}")
    private String webhookSecret;

    public boolean isEnabled() {
        return enabled && repoUrl != null && !repoUrl.isBlank();
    }

    public boolean isSyncOnStartup() {
        return syncOnStartup;
    }

    public String getLocalPath() {
        return localPath;
    }

    public long getPollIntervalSeconds() {
        return pollIntervalSeconds;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public String getBranch() {
        return branch;
    }

    /**
     * Performs a git clone or pull depending on whether the local directory already exists.
     *
     * @return true if sync was successful
     */
    public boolean sync() {
        if (!isEnabled()) {
            log.debug("Git sync disabled");
            return false;
        }

        Path local = Path.of(localPath);
        try {
            if (Files.isDirectory(local.resolve(".git"))) {
                return pull(local);
            } else {
                return clone(local);
            }
        } catch (Exception e) {
            log.error("Git sync failed: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Performs a git pull and returns whether new changes were fetched.
     *
     * @return true if new commits were pulled (i.e., HEAD changed)
     */
    public boolean syncAndDetectChanges() {
        if (!isEnabled()) {
            return false;
        }

        Path local = Path.of(localPath);
        try {
            if (!Files.isDirectory(local.resolve(".git"))) {
                // First clone always counts as changes
                return clone(local);
            }

            String headBefore = getHead(local);
            boolean success = pull(local);
            if (!success) return false;
            String headAfter = getHead(local);

            boolean changed = headBefore != null && !headBefore.equals(headAfter);
            if (changed) {
                log.info("Git sync: new changes detected ({}..{})", headBefore.substring(0, 7), headAfter.substring(0, 7));
            }
            return changed;
        } catch (Exception e) {
            log.error("Git sync failed: {}", e.getMessage(), e);
            return false;
        }
    }

    private String getHead(Path local) {
        try {
            GitResult result = executeGit(
                    List.of("git", "-C", local.toAbsolutePath().toString(), "rev-parse", "HEAD"), local);
            return result.exitCode == 0 ? result.stdout.trim() : null;
        } catch (Exception e) {
            log.warn("Failed to read HEAD: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Syncs a per-project repository to a subdirectory.
     */
    public boolean syncProjectRepo(String configId, String url, String repoBranch, String repoToken) {
        Path local = Path.of(localPath, "projects", configId);
        try {
            String authUrl = injectToken(url, repoToken);
            if (Files.isDirectory(local.resolve(".git"))) {
                return pullFrom(local, repoBranch);
            } else {
                return cloneTo(local, authUrl, repoBranch);
            }
        } catch (Exception e) {
            log.error("Git sync for project '{}' failed: {}", configId, e.getMessage(), e);
            return false;
        }
    }

    private boolean clone(Path local) throws IOException, InterruptedException {
        String authUrl = injectToken(repoUrl, token);
        return cloneTo(local, authUrl, branch);
    }

    private boolean cloneTo(Path local, String authUrl, String cloneBranch) throws IOException, InterruptedException {
        Files.createDirectories(local.getParent());

        List<String> command = new ArrayList<>(List.of(
                "git", "clone",
                "--branch", cloneBranch,
                "--depth", "1",
                "--single-branch",
                authUrl,
                local.toAbsolutePath().toString()
        ));

        log.info("Git clone: branch={} -> {}", cloneBranch, local);
        GitResult result = executeGit(command, local.getParent());
        if (result.exitCode == 0) {
            log.info("Git clone successful: {}", local);
            return true;
        } else {
            log.error("Git clone failed (exit {}): {}", result.exitCode, result.stderr);
            return false;
        }
    }

    private boolean pull(Path local) throws IOException, InterruptedException {
        return pullFrom(local, branch);
    }

    private boolean pullFrom(Path local, String pullBranch) throws IOException, InterruptedException {
        List<String> command = List.of("git", "-C", local.toAbsolutePath().toString(),
                "pull", "origin", pullBranch);

        log.info("Git pull: branch={} in {}", pullBranch, local);
        GitResult result = executeGit(command, local);
        if (result.exitCode == 0) {
            log.info("Git pull successful: {}", result.stdout.contains("Already up to date") ? "no changes" : "updated");
            return true;
        } else {
            log.error("Git pull failed (exit {}): {}", result.exitCode, result.stderr);
            return false;
        }
    }

    private String injectToken(String url, String authToken) {
        if (authToken == null || authToken.isBlank()) return url;
        // https://github.com/... → https://TOKEN@github.com/...
        if (url.startsWith("https://")) {
            return "https://" + authToken + "@" + url.substring(8);
        }
        return url;
    }

    private GitResult executeGit(List<String> command, Path workDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(false);

        Process process = pb.start();

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        Thread outThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) stdout.append(line).append("\n");
            } catch (IOException ignored) {}
        });
        Thread errThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) stderr.append(line).append("\n");
            } catch (IOException ignored) {}
        });

        outThread.start();
        errThread.start();

        boolean completed = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            return new GitResult(-1, "", "Git command timed out after " + GIT_TIMEOUT_SECONDS + " seconds");
        }

        outThread.join(5000);
        errThread.join(5000);

        return new GitResult(process.exitValue(), stdout.toString().trim(), stderr.toString().trim());
    }

    private record GitResult(int exitCode, String stdout, String stderr) {}
}
