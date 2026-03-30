package io.cwc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import io.cwc.config.CwcConfigProperties;
import io.cwc.dto.ProjectConfigFile;
import io.cwc.dto.WorkflowConfigFile;
import io.cwc.entity.ProjectEntity;
import io.cwc.repository.ProjectRepository;
import io.cwc.repository.WorkflowRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Writes project and workflow config files back to the filesystem on publish.
 * Enabled by cwc.config.writeback=true. Writes to the first path in cwc.config.paths.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigWritebackService {

    private final CwcConfigProperties configProperties;
    private final ConfigExportService configExportService;
    private final ProjectRepository projectRepository;
    private final WorkflowRepository workflowRepository;
    private final ObjectMapper objectMapper;

    private final ReentrantLock writeLock = new ReentrantLock();

    public boolean isEnabled() {
        return configProperties.isWritebackEnabled();
    }

    /**
     * Writes project settings (project.json) to the filesystem.
     * Called after project updates.
     */
    public void writeProjectSettings(String projectId) {
        if (!isEnabled()) return;

        writeLock.lock();
        try {
            ProjectEntity project = projectRepository.findById(projectId).orElse(null);
            if (project == null) return;

            ensureConfigId(project);
            Path projectDir = resolveProjectDir(project);
            Files.createDirectories(projectDir);

            ProjectConfigFile config = configExportService.exportSettingsOnly(projectId);
            Path target = projectDir.resolve("project.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), config);
            log.info("Wrote project settings to {}", target);
        } catch (IOException e) {
            log.error("Failed to write project settings for {}: {}", projectId, e.getMessage());
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Writes a single workflow JSON to the filesystem.
     * Called after workflow publish.
     */
    public void writeWorkflow(String projectId, String workflowId) {
        if (!isEnabled()) return;

        writeLock.lock();
        try {
            ProjectEntity project = projectRepository.findById(projectId).orElse(null);
            if (project == null) return;

            ensureConfigId(project);
            Path workflowsDir = resolveProjectDir(project).resolve("workflows");
            Files.createDirectories(workflowsDir);

            WorkflowConfigFile wfConfig = configExportService.exportSingleWorkflow(projectId, workflowId);
            String filename = wfConfig.getConfigId() + ".json";
            Path target = workflowsDir.resolve(filename);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), wfConfig);
            log.info("Wrote workflow to {}", target);
        } catch (IOException e) {
            log.error("Failed to write workflow {} for project {}: {}", workflowId, projectId, e.getMessage());
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Ensures the project has a configId. If missing, derives one from the name and persists it.
     */
    private void ensureConfigId(ProjectEntity project) {
        if (project.getConfigId() == null || project.getConfigId().isBlank()) {
            String configId = slugify(project.getName());
            project.setConfigId(configId);
            projectRepository.save(project);
            log.info("Generated configId '{}' for project '{}'", configId, project.getName());
        }
    }

    private Path resolveProjectDir(ProjectEntity project) {
        Path writablePath = configProperties.getWritablePath().orElseThrow();
        return writablePath.resolve("projects").resolve(project.getConfigId());
    }

    private String slugify(String input) {
        if (input == null) return "unnamed";
        return input.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
