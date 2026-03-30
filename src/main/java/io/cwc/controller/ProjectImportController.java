package io.cwc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.cwc.config.CwcConfigProperties.ConfigMode;
import io.cwc.dto.*;
import io.cwc.service.ConfigBootstrapService;
import io.cwc.service.ConfigExportService;
import io.cwc.service.ProjectGitService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Import and export endpoints for projects and workflows.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ProjectImportController {

    private final ConfigBootstrapService configBootstrapService;
    private final ConfigExportService configExportService;
    private final ProjectGitService projectGitService;
    private final ObjectMapper objectMapper;

    /**
     * Import a full project bundle (ZIP or single-JSON).
     */
    @PostMapping("/api/projects/import")
    public ConfigReloadResult importProject(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "seed") String mode) throws IOException {

        ConfigMode configMode = "sync".equalsIgnoreCase(mode) ? ConfigMode.SYNC : ConfigMode.SEED;

        // Write to temp directory and process via the bootstrap service
        Path tempDir = Files.createTempDirectory("cwc-import-");
        try {
            String filename = file.getOriginalFilename();
            if (filename != null && filename.endsWith(".zip")) {
                return importFromZip(file, tempDir, configMode);
            } else {
                return importFromJson(file, tempDir, configMode);
            }
        } finally {
            // Cleanup temp files
            deleteRecursively(tempDir);
        }
    }

    /**
     * Import projects/workflows from a git repository URL.
     */
    @PostMapping("/api/projects/import-git")
    public ConfigReloadResult importFromGit(@RequestBody GitImportRequest request) {
        return projectGitService.importFromGitRepo(
                request.getRepoUrl(), request.getBranch(), request.getToken(),
                request.getProvider(), request.getSubPath(), request.getMode());
    }

    /**
     * Export a project as ZIP, JSON bundle, or settings-only.
     *
     * @param format "zip" (default), "bundle" (JSON with workflows), or "settings" (project.json only, no workflows)
     */
    @GetMapping("/api/projects/{id}/export")
    public ResponseEntity<?> exportProject(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "zip") String format) throws IOException {

        if ("settings".equalsIgnoreCase(format)) {
            ProjectConfigFile settingsOnly = configExportService.exportSettingsOnly(id);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(settingsOnly);
        }

        if ("bundle".equalsIgnoreCase(format)) {
            ProjectExportBundle bundle = configExportService.exportAsBundle(id);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(bundle);
        }

        // Default: ZIP
        byte[] zipBytes = configExportService.exportAsZip(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"project-export.zip\"")
                .body(zipBytes);
    }

    private ConfigReloadResult importFromZip(MultipartFile file, Path tempDir, ConfigMode mode) throws IOException {
        Path zipPath = tempDir.resolve("import.zip");
        file.transferTo(zipPath);

        // Extract ZIP
        Path extractDir = tempDir.resolve("extracted");
        Files.createDirectories(extractDir);
        try (var zis = new java.util.zip.ZipInputStream(Files.newInputStream(zipPath))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    Files.createDirectories(extractDir.resolve(entry.getName()));
                } else {
                    Path target = extractDir.resolve(entry.getName());
                    Files.createDirectories(target.getParent());
                    Files.copy(zis, target);
                }
                zis.closeEntry();
            }
        }

        // Check if it's a project-level ZIP (has project.json at root)
        Path projectJson = extractDir.resolve("project.json");
        if (Files.isRegularFile(projectJson)) {
            // Wrap in convention structure: create projects/<name>/ directory
            Path projectDir = extractDir.resolve("projects").resolve("imported");
            Files.createDirectories(projectDir);

            // Move project.json and workflows/ into the convention structure
            Files.move(projectJson, projectDir.resolve("project.json"));
            Path workflowsDir = extractDir.resolve("workflows");
            if (Files.isDirectory(workflowsDir)) {
                Path targetWfDir = projectDir.resolve("workflows");
                Files.move(workflowsDir, targetWfDir);
            }
        }

        // Use the bootstrap service with the extracted directory as a config path
        return configBootstrapService.loadAndApplyFromPath(extractDir, mode);
    }

    private ConfigReloadResult importFromJson(MultipartFile file, Path tempDir, ConfigMode mode) throws IOException {
        byte[] content = file.getBytes();

        // Try to detect if it's a bundle (has "project" key) or bare project.json
        var tree = objectMapper.readTree(content);

        Path projectDir = tempDir.resolve("projects").resolve("imported");
        Files.createDirectories(projectDir);

        if (tree.has("project") && tree.has("workflows")) {
            // Bundle format: extract project and workflows
            ProjectExportBundle bundle = objectMapper.readValue(content, ProjectExportBundle.class);

            // Write project.json
            Files.write(projectDir.resolve("project.json"),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(bundle.getProject()));

            // Write workflow files
            if (bundle.getWorkflows() != null && !bundle.getWorkflows().isEmpty()) {
                Path wfDir = projectDir.resolve("workflows");
                Files.createDirectories(wfDir);
                for (WorkflowConfigFile wf : bundle.getWorkflows()) {
                    String filename = (wf.getConfigId() != null ? wf.getConfigId() : "workflow") + ".json";
                    Files.write(wfDir.resolve(filename),
                            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(wf));
                }
            }
        } else {
            // Bare project.json
            Files.write(projectDir.resolve("project.json"), content);
        }

        return configBootstrapService.loadAndApplyFromPath(tempDir, mode);
    }

    private void deleteRecursively(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.walk(path)) {
                    stream.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> {
                                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                            });
                }
            } else {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            log.debug("Failed to clean up temp directory {}: {}", path, e.getMessage());
        }
    }
}
