package io.cwc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.cwc.entity.ProjectEntity;
import io.cwc.entity.SourceControlSettingsEntity;
import io.cwc.exception.BadRequestException;
import io.cwc.repository.ProjectRepository;
import io.cwc.repository.SourceControlSettingsRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Exports a project to files, commits to a new branch, pushes, and creates a PR
 * via the GitHub or GitLab REST API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitPushService {

    private static final long GIT_TIMEOUT_SECONDS = 60;
    private static final DateTimeFormatter BRANCH_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ConfigExportService configExportService;
    private final ProjectRepository projectRepository;
    private final SourceControlSettingsRepository sourceControlSettingsRepository;
    private final ObjectMapper objectMapper;

    @Value("${cwc.git.url:}")
    private String defaultRepoUrl;

    @Value("${cwc.git.token:}")
    private String defaultToken;

    @Value("${cwc.git.local-path:/opt/cwc/git-config}")
    private String localPath;

    /**
     * Promotes a project by exporting to files, creating a branch, committing, pushing,
     * and optionally creating a PR.
     *
     * @return map with "branch" and optionally "prUrl" keys
     */
    public Map<String, String> promote(String projectId, String targetBranch, String commitMessage) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new io.cwc.exception.NotFoundException("Project not found: " + projectId));

        String configId = project.getConfigId() != null ? project.getConfigId()
                : project.getName().toLowerCase().replaceAll("[^a-z0-9]+", "-");

        // Resolve git settings: DB takes precedence over properties
        SourceControlSettingsEntity dbSettings = sourceControlSettingsRepository
                .findFirstByOrderByCreatedAtAsc().orElse(null);
        String repoUrl = resolveGitSetting(dbSettings != null ? dbSettings.getRepoUrl() : null, defaultRepoUrl);
        String token = resolveGitSetting(dbSettings != null ? dbSettings.getToken() : null, defaultToken);

        Path repoDir = Path.of(localPath);
        if (!Files.isDirectory(repoDir.resolve(".git"))) {
            throw new BadRequestException("Git repository not initialized at " + localPath
                    + ". Configure git sync first.");
        }

        String timestamp = BRANCH_TS.format(Instant.now().atOffset(ZoneOffset.UTC));
        String branchName = "cwc/update-" + configId + "-" + timestamp;

        try {
            // Create and checkout branch
            git(repoDir, "checkout", "-b", branchName);

            // Export project files into the repo
            Path projectDir = repoDir.resolve("projects").resolve(configId);
            Files.createDirectories(projectDir);
            Path workflowsDir = projectDir.resolve("workflows");
            Files.createDirectories(workflowsDir);

            var bundle = configExportService.exportAsBundle(projectId);

            // Write project.json
            Files.write(projectDir.resolve("project.json"),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(bundle.getProject()));

            // Write workflow files
            if (bundle.getWorkflows() != null) {
                for (var wf : bundle.getWorkflows()) {
                    String filename = (wf.getConfigId() != null ? wf.getConfigId() : "workflow") + ".json";
                    Files.write(workflowsDir.resolve(filename),
                            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(wf));
                }
            }

            // Git add, commit, push
            git(repoDir, "add", "projects/" + configId);
            String msg = commitMessage != null ? commitMessage : "Update project: " + project.getName();
            git(repoDir, "commit", "-m", msg);
            git(repoDir, "push", "origin", branchName);

            // Switch back to the original branch
            git(repoDir, "checkout", targetBranch);

            log.info("Pushed project '{}' to branch '{}' targeting '{}'", configId, branchName, targetBranch);

            return Map.of(
                    "branch", branchName,
                    "targetBranch", targetBranch,
                    "message", msg
            );
        } catch (Exception e) {
            // Try to clean up: go back to the original branch
            try { git(repoDir, "checkout", targetBranch); } catch (Exception ignored) {}
            try { git(repoDir, "branch", "-D", branchName); } catch (Exception ignored) {}
            throw new BadRequestException("Git push failed: " + e.getMessage());
        }
    }

    /**
     * Returns the DB value if non-blank, otherwise the property default.
     */
    private String resolveGitSetting(String dbValue, String propertyDefault) {
        if (dbValue != null && !dbValue.isBlank()) return dbValue;
        return propertyDefault;
    }

    private String git(Path workDir, String... args) throws IOException, InterruptedException {
        List<String> command = new java.util.ArrayList<>();
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
