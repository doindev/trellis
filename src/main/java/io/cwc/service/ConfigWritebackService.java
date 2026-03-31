package io.cwc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import io.cwc.config.CwcConfigProperties;
import io.cwc.dto.ProjectConfigFile;
import io.cwc.dto.WorkflowConfigFile;
import io.cwc.entity.CredentialEntity;
import io.cwc.entity.ProjectEntity;
import io.cwc.repository.CredentialRepository;
import io.cwc.repository.ProjectRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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
    private final CredentialEncryptionService encryptionService;
    private final CredentialRepository credentialRepository;
    private final PropertiesFileService propertiesFileService;
    private final ProjectRepository projectRepository;
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

            // Write encrypted credential values to credentials.properties
            writeCredentialProperties(project);

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

    /** Field names (lowercased) that contain secrets and must be encrypted with enc: prefix. */
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "passwd", "pwd",
            "username", "user",
            "apikey", "api_key", "api-key",
            "token", "accesstoken", "access_token",
            "bearertoken", "bearer_token",
            "basictoken", "basic_token",
            "secret", "client_secret", "clientsecret"
    );

    /**
     * Writes credential field values to the Spring application properties file.
     * Sensitive fields (password, username, apiKey, token) are encrypted with enc: prefix.
     * Non-sensitive fields (host, port, database) are stored as plain text.
     * Only adds properties that don't already exist.
     */
    private void writeCredentialProperties(ProjectEntity project) {
        String projectConfigId = project.getConfigId();
        if (projectConfigId == null) return;

        Map<String, String> properties = new LinkedHashMap<>();
        for (CredentialEntity cred : credentialRepository.findByProjectId(project.getId())) {
            String credRef = cred.getConfigId() != null ? cred.getConfigId() : slugify(cred.getName());
            try {
                Map<String, Object> decryptedData = encryptionService.decrypt(cred.getData());
                for (Map.Entry<String, Object> entry : decryptedData.entrySet()) {
                    String propKey = toUpperSnake(projectConfigId) + "_"
                            + toUpperSnake(credRef) + "_" + toUpperSnake(entry.getKey());
                    String plainValue = String.valueOf(entry.getValue());

                    if (isSensitiveField(entry.getKey())) {
                        properties.put(propKey, "enc:" + encryptionService.encryptString(plainValue));
                    } else {
                        properties.put(propKey, plainValue);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to write credential properties for {}/{}: {}",
                        projectConfigId, credRef, e.getMessage());
            }
        }
        if (!properties.isEmpty()) {
            propertiesFileService.addPropertiesIfAbsent(properties);
        }
    }

    private boolean isSensitiveField(String fieldName) {
        String lower = fieldName.toLowerCase().replaceAll("[^a-z0-9]", "");
        return SENSITIVE_FIELDS.stream().anyMatch(s -> lower.contains(s.replaceAll("[^a-z0-9]", "")));
    }

    private String toUpperSnake(String input) {
        return input.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
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
