package io.cwc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import io.cwc.dto.*;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Discovers config files from a single config path using either convention-based
 * directory scanning or manifest-based explicit file listing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigDiscoveryService {

    private final ObjectMapper objectMapper;

    /**
     * Discovered config files from a single path.
     */
    public record DiscoveredConfig(
            String discoveryMode,
            Path settingsFile,
            List<DiscoveredProject> projects,
            List<String> errors
    ) {}

    public record DiscoveredProject(
            Path projectFile,
            List<Path> workflowFiles
    ) {}

    /**
     * Discovers config files from the given path using manifest or convention mode.
     */
    public DiscoveredConfig discover(Path configPath) {
        Path manifestPath = configPath.resolve("manifest.json");
        if (Files.isRegularFile(manifestPath)) {
            Path projectsDir = configPath.resolve("projects");
            if (Files.isDirectory(projectsDir)) {
                log.warn("manifest.json found at {}; ignoring convention-based projects/ directory. "
                        + "Remove manifest.json to use convention-based discovery.", configPath);
            }
            return discoverFromManifest(configPath, manifestPath);
        }
        return discoverByConvention(configPath);
    }

    private DiscoveredConfig discoverFromManifest(Path configPath, Path manifestPath) {
        List<String> errors = new ArrayList<>();
        ManifestFile manifest;
        try {
            manifest = objectMapper.readValue(manifestPath.toFile(), ManifestFile.class);
        } catch (IOException e) {
            errors.add("Failed to parse manifest.json at " + manifestPath + ": " + e.getMessage());
            return new DiscoveredConfig("MANIFEST", null, List.of(), errors);
        }

        // Resolve settings file
        Path settingsFile = null;
        if (manifest.getSettings() != null) {
            Path resolved = configPath.resolve(manifest.getSettings());
            if (Files.isRegularFile(resolved)) {
                settingsFile = resolved;
            } else {
                errors.add("Manifest references missing settings file: " + resolved);
            }
        }

        // Resolve project files
        List<DiscoveredProject> projects = new ArrayList<>();
        if (manifest.getProjects() != null) {
            for (ManifestFile.ManifestProject mp : manifest.getProjects()) {
                if (mp.getConfig() == null) {
                    errors.add("Manifest project entry missing 'config' field");
                    continue;
                }

                Path projectFile = configPath.resolve(mp.getConfig());
                if (!Files.isRegularFile(projectFile)) {
                    errors.add("Manifest references missing project file: " + projectFile);
                    continue;
                }

                List<Path> workflowFiles = new ArrayList<>();
                if (mp.getWorkflows() != null) {
                    for (String wfPath : mp.getWorkflows()) {
                        Path wfFile = configPath.resolve(wfPath);
                        if (Files.isRegularFile(wfFile)) {
                            workflowFiles.add(wfFile);
                        } else {
                            errors.add("Manifest references missing workflow file: " + wfFile);
                        }
                    }
                }

                projects.add(new DiscoveredProject(projectFile, workflowFiles));
            }
        }

        return new DiscoveredConfig("MANIFEST", settingsFile, projects, errors);
    }

    private DiscoveredConfig discoverByConvention(Path configPath) {
        List<String> errors = new ArrayList<>();

        // Check for settings.json at root
        Path settingsFile = null;
        Path settingsPath = configPath.resolve("settings.json");
        if (Files.isRegularFile(settingsPath)) {
            settingsFile = settingsPath;
        }

        // Scan projects/ directory
        List<DiscoveredProject> projects = new ArrayList<>();
        Path projectsDir = configPath.resolve("projects");
        if (Files.isDirectory(projectsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(projectsDir)) {
                for (Path projectDir : stream) {
                    if (!Files.isDirectory(projectDir)) continue;

                    Path projectFile = projectDir.resolve("project.json");
                    if (!Files.isRegularFile(projectFile)) {
                        log.debug("Skipping directory without project.json: {}", projectDir);
                        continue;
                    }

                    List<Path> workflowFiles = new ArrayList<>();
                    Path workflowsDir = projectDir.resolve("workflows");
                    if (Files.isDirectory(workflowsDir)) {
                        try (DirectoryStream<Path> wfStream = Files.newDirectoryStream(workflowsDir, "*.json")) {
                            for (Path wfFile : wfStream) {
                                if (Files.isRegularFile(wfFile)) {
                                    workflowFiles.add(wfFile);
                                }
                            }
                        }
                    }

                    projects.add(new DiscoveredProject(projectFile, workflowFiles));
                }
            } catch (IOException e) {
                errors.add("Failed to scan projects directory " + projectsDir + ": " + e.getMessage());
            }
        }

        return new DiscoveredConfig("CONVENTION", settingsFile, projects, errors);
    }
}
