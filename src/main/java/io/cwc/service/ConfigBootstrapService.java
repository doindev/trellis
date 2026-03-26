package io.cwc.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cwc.config.CwcConfigProperties;
import io.cwc.config.CwcConfigProperties.ConfigMode;
import io.cwc.dto.*;
import io.cwc.entity.*;
import io.cwc.repository.*;
import io.cwc.service.PlaceholderResolver.ResolvedResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Core config bootstrap logic: discovers files across paths, merges them,
 * resolves placeholders, and applies to the database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigBootstrapService {

    private final CwcConfigProperties configProperties;
    private final ConfigDiscoveryService discoveryService;
    private final PlaceholderResolver placeholderResolver;
    private final ObjectMapper objectMapper;

    private final ProjectRepository projectRepository;
    private final WorkflowRepository workflowRepository;
    private final CredentialRepository credentialRepository;
    private final CredentialEncryptionService encryptionService;
    private final VariableRepository variableRepository;
    private final CacheDefinitionRepository cacheDefinitionRepository;
    private final TagRepository tagRepository;
    private final AiSettingsService aiSettingsService;
    private final ExecutionSettingsService executionSettingsService;
    private final McpSettingsService mcpSettingsService;
    private final ProjectRelationRepository projectRelationRepository;
    private final WebhookService webhookService;
    private final ClusterSyncService clusterSyncService;

    private final ReentrantLock reloadLock = new ReentrantLock();
    private final AtomicBoolean bootstrapComplete = new AtomicBoolean(false);
    private volatile ConfigReloadResult lastResult;

    public boolean isComplete() {
        return bootstrapComplete.get();
    }

    public ConfigReloadResult getLastResult() {
        return lastResult;
    }

    /**
     * Main entry point: discovers files, merges across paths, resolves placeholders, applies to DB.
     *
     * @param modeOverride if non-null, overrides the configured cwc.config.mode
     * @param adoptOrphans if true, auto-adopt UI-created entities that match by contextPath/name
     */
    public ConfigReloadResult loadAndApply(ConfigMode modeOverride, boolean adoptOrphans) {
        if (!reloadLock.tryLock()) {
            throw new io.cwc.exception.BadRequestException("Config reload already in progress");
        }
        try {
            return doLoadAndApply(modeOverride, adoptOrphans);
        } finally {
            bootstrapComplete.set(true);
            reloadLock.unlock();
        }
    }

    public ConfigReloadResult loadAndApply() {
        return loadAndApply(null, false);
    }

    /**
     * Load and apply from a single explicit path (used by import API).
     * Does not require cwc.config.paths to be configured.
     */
    public ConfigReloadResult loadAndApplyFromPath(java.nio.file.Path path, ConfigMode mode) {
        if (!reloadLock.tryLock()) {
            throw new io.cwc.exception.BadRequestException("Config reload already in progress");
        }
        try {
            ConfigReloadResult result = ConfigReloadResult.builder()
                    .mode(mode.name().toLowerCase())
                    .pathsScanned(1)
                    .build();

            ConfigDiscoveryService.DiscoveredConfig discovered = discoveryService.discover(path);
            result.getDiscoveryModes().add(discovered.discoveryMode());
            result.getErrors().addAll(discovered.errors());

            MergedConfig merged = new MergedConfig();
            mergeDiscovered(discovered, path, merged, result);

            if (merged.settings != null) {
                applySettings(merged.settings, mode, result);
            }
            for (MergedProject mp : merged.projects.values()) {
                applyProject(mp, mode, false, result);
            }

            logSummary(result);
            lastResult = result;
            return result;
        } finally {
            reloadLock.unlock();
        }
    }

    private ConfigReloadResult doLoadAndApply(ConfigMode modeOverride, boolean adoptOrphans) {
        ConfigMode mode = modeOverride != null ? modeOverride : configProperties.getConfigMode();
        ConfigReloadResult result = ConfigReloadResult.builder()
                .mode(mode.name().toLowerCase())
                .build();

        if (!configProperties.isEnabled()) {
            log.info("Config bootstrap disabled (no cwc.config.paths configured)");
            lastResult = result;
            return result;
        }

        List<Path> paths = configProperties.getConfigPaths();
        result.setPathsScanned(paths.size());

        // Phase 1: Discover and merge across all paths
        MergedConfig merged = new MergedConfig();
        for (Path path : paths) {
            log.info("Config path: {}", path);
            ConfigDiscoveryService.DiscoveredConfig discovered = discoveryService.discover(path);
            result.getDiscoveryModes().add(discovered.discoveryMode());
            log.info("Discovery mode: {} ({})", discovered.discoveryMode(), path);
            result.getErrors().addAll(discovered.errors());
            mergeDiscovered(discovered, path, merged, result);
        }

        // Phase 2: Apply settings
        if (merged.settings != null) {
            applySettings(merged.settings, mode, result);
        }

        // Phase 3: Apply projects and their children
        for (MergedProject mp : merged.projects.values()) {
            applyProject(mp, mode, adoptOrphans, result);
        }

        // Log summary
        logSummary(result);
        lastResult = result;
        return result;
    }

    // ---- Merge Phase ----

    private static class MergedConfig {
        SettingsConfigFile settings;
        Map<String, MergedProject> projects = new LinkedHashMap<>();
    }

    private static class MergedProject {
        ProjectConfigFile config;
        Map<String, WorkflowConfigFile> workflows = new LinkedHashMap<>();
    }

    private void mergeDiscovered(ConfigDiscoveryService.DiscoveredConfig discovered, Path basePath,
                                  MergedConfig merged, ConfigReloadResult result) {
        // Merge settings (deep-merge)
        if (discovered.settingsFile() != null) {
            try {
                SettingsConfigFile settings = objectMapper.readValue(
                        discovered.settingsFile().toFile(), SettingsConfigFile.class);
                if (merged.settings == null) {
                    merged.settings = settings;
                } else {
                    deepMergeSettings(merged.settings, settings);
                }
            } catch (IOException e) {
                result.addError("Failed to parse settings file " + discovered.settingsFile() + ": " + e.getMessage());
            }
        }

        // Merge projects (later replaces earlier by configId)
        for (ConfigDiscoveryService.DiscoveredProject dp : discovered.projects()) {
            try {
                ProjectConfigFile projectConfig = objectMapper.readValue(
                        dp.projectFile().toFile(), ProjectConfigFile.class);

                String configId = projectConfig.getConfigId();
                if (configId == null || configId.isBlank()) {
                    // Derive from directory name
                    configId = dp.projectFile().getParent().getFileName().toString()
                            .toLowerCase().replaceAll("[^a-z0-9-]", "-");
                    projectConfig.setConfigId(configId);
                }

                MergedProject mp = new MergedProject();
                mp.config = projectConfig;

                // Parse workflow files
                for (Path wfFile : dp.workflowFiles()) {
                    try {
                        WorkflowConfigFile wfConfig = objectMapper.readValue(
                                wfFile.toFile(), WorkflowConfigFile.class);

                        String wfConfigId = wfConfig.getConfigId();
                        if (wfConfigId == null || wfConfigId.isBlank()) {
                            // Derive from filename
                            String filename = wfFile.getFileName().toString();
                            wfConfigId = filename.replaceAll("\\.json$", "")
                                    .toLowerCase().replaceAll("[^a-z0-9-]", "-");
                            wfConfig.setConfigId(wfConfigId);
                        }
                        mp.workflows.put(wfConfigId, wfConfig);
                    } catch (IOException e) {
                        result.addError("Failed to parse workflow file " + wfFile + ": " + e.getMessage());
                    }
                }

                if (merged.projects.containsKey(configId)) {
                    log.info("Project '{}' overridden by path {}", configId, basePath);
                }
                merged.projects.put(configId, mp);

            } catch (IOException e) {
                result.addError("Failed to parse project file " + dp.projectFile() + ": " + e.getMessage());
            }
        }
    }

    private void deepMergeSettings(SettingsConfigFile base, SettingsConfigFile overlay) {
        if (overlay.getAi() != null) base.setAi(overlay.getAi());
        if (overlay.getExecution() != null) base.setExecution(overlay.getExecution());
        if (overlay.getMcp() != null) base.setMcp(overlay.getMcp());
        if (overlay.getSwagger() != null) base.setSwagger(overlay.getSwagger());
        if (overlay.getGitRepos() != null) base.setGitRepos(overlay.getGitRepos());
        if (overlay.getEnvironments() != null) base.setEnvironments(overlay.getEnvironments());
    }

    // ---- Apply Phase: Settings ----

    private void applySettings(SettingsConfigFile settings, ConfigMode mode, ConfigReloadResult result) {
        // Resolve placeholders on the settings object
        try {
            Map<String, Object> rawMap = objectMapper.convertValue(settings, new TypeReference<>() {});
            ResolvedResult resolved = placeholderResolver.resolve(rawMap);
            resolved.unresolved().forEach(p -> result.addError("Unresolved placeholder in settings.json: " + p));
            settings = objectMapper.convertValue(resolved.resolved(), SettingsConfigFile.class);
        } catch (Exception e) {
            result.addError("Failed to resolve placeholders in settings: " + e.getMessage());
            return;
        }

        // Apply AI settings
        if (settings.getAi() != null) {
            var ai = settings.getAi();
            io.cwc.dto.AiSettingsDto dto = io.cwc.dto.AiSettingsDto.builder()
                    .provider(ai.getProvider())
                    .model(ai.getModel())
                    .baseUrl(ai.getBaseUrl())
                    .apiKey(ai.getApiKey())
                    .enabled(ai.getEnabled() != null && ai.getEnabled())
                    .build();
            aiSettingsService.saveSettings(dto);
        }

        // Apply execution settings
        if (settings.getExecution() != null) {
            var exec = settings.getExecution();
            io.cwc.dto.ExecutionSettingsDto dto = io.cwc.dto.ExecutionSettingsDto.builder()
                    .saveExecutionProgress(exec.getSaveExecutionProgress())
                    .saveManualExecutions(exec.getSaveManualExecutions())
                    .executionTimeout(exec.getExecutionTimeout() != null ? exec.getExecutionTimeout() : -1)
                    .errorWorkflow(exec.getErrorWorkflow())
                    .build();
            executionSettingsService.updateSettings(dto);
        }

        // Apply MCP settings
        if (settings.getMcp() != null) {
            var mcp = settings.getMcp();

            // Enable/disable MCP
            if (mcp.getEnabled() != null) {
                mcpSettingsService.setEnabled(mcp.getEnabled());
            }

            // Agent tools settings
            if (mcp.getAgentToolsEnabled() != null || mcp.getAgentToolsDedicated() != null
                    || mcp.getAgentToolsPath() != null || mcp.getAgentToolsTransport() != null) {
                mcpSettingsService.updateAgentToolsSettings(
                        mcp.getAgentToolsEnabled(),
                        mcp.getAgentToolsDedicated(),
                        mcp.getAgentToolsPath(),
                        mcp.getAgentToolsTransport());
            }

            // MCP endpoints
            if (mcp.getEndpoints() != null) {
                for (var epConfig : mcp.getEndpoints()) {
                    if (epConfig.getName() == null || epConfig.getTransport() == null) continue;
                    try {
                        // Check if an endpoint with this transport already exists
                        var existing = mcpSettingsService.listEndpoints().stream()
                                .filter(e -> e.getTransport().equals(epConfig.getTransport()))
                                .findFirst();
                        if (existing.isPresent()) {
                            // Update existing endpoint
                            McpEndpointDto updateDto = McpEndpointDto.builder()
                                    .name(epConfig.getName())
                                    .transport(epConfig.getTransport())
                                    .path(epConfig.getPath())
                                    .enabled(epConfig.getEnabled() != null ? epConfig.getEnabled() : true)
                                    .build();
                            mcpSettingsService.updateEndpoint(existing.get().getId(), updateDto);
                        } else {
                            // Create new endpoint
                            McpEndpointDto createDto = McpEndpointDto.builder()
                                    .name(epConfig.getName())
                                    .transport(epConfig.getTransport())
                                    .path(epConfig.getPath())
                                    .enabled(epConfig.getEnabled() != null ? epConfig.getEnabled() : true)
                                    .build();
                            mcpSettingsService.createEndpoint(createDto);
                        }
                    } catch (Exception e) {
                        result.addWarning("Failed to apply MCP endpoint '" + epConfig.getName() + "': " + e.getMessage());
                    }
                }
            }
        }

        result.setSettingsApplied(1);
        log.info("Applied global settings from config files");
    }

    // ---- Apply Phase: Projects ----

    @Transactional
    private void applyProject(MergedProject mp, ConfigMode mode, boolean adoptOrphans,
                               ConfigReloadResult result) {
        ProjectConfigFile config = mp.config;

        // Resolve placeholders on the project config
        try {
            String origConfigId = config.getConfigId();
            Map<String, Object> rawMap = objectMapper.convertValue(config, new TypeReference<>() {});
            ResolvedResult resolved = placeholderResolver.resolve(rawMap);
            resolved.unresolved().forEach(p -> result.addError("Unresolved placeholder in project '" + origConfigId + "': " + p));
            config = objectMapper.convertValue(resolved.resolved(), ProjectConfigFile.class);
            mp.config = config;
        } catch (Exception e) {
            result.addError("Failed to resolve placeholders in project '" + config.getConfigId() + "': " + e.getMessage());
            result.setProjectsFailed(result.getProjectsFailed() + 1);
            return;
        }

        String configId = config.getConfigId();

        try {
            // Find existing project by configId
            ProjectEntity existing = projectRepository.findByConfigId(configId).orElse(null);

            // Adoption check: if not found by configId, try contextPath match
            if (existing == null && adoptOrphans && config.getContextPath() != null) {
                existing = projectRepository.findByContextPath(config.getContextPath())
                        .filter(p -> p.getConfigId() == null)
                        .orElse(null);
                if (existing != null) {
                    existing.setConfigId(configId);
                    projectRepository.save(existing);
                    result.addAdoption("Adopted project '" + existing.getName() + "' (" + existing.getId()
                            + ") with configId '" + configId + "' by contextPath match");
                    log.info("Auto-adopted project '{}' with configId '{}'", existing.getName(), configId);
                }
            }

            // Collision check
            if (existing == null && config.getContextPath() != null) {
                Optional<ProjectEntity> byCtxPath = projectRepository.findByContextPath(config.getContextPath());
                if (byCtxPath.isPresent() && byCtxPath.get().getConfigId() == null) {
                    String msg = String.format(
                            "Project configId '%s' wants contextPath '%s', but that contextPath is already used by project '%s' (id=%s, no configId). "
                                    + "Use ?adoptOrphans=true to auto-adopt, or manually set configId on the existing project.",
                            configId, config.getContextPath(), byCtxPath.get().getName(), byCtxPath.get().getId());
                    result.addError(msg);
                    result.setProjectsFailed(result.getProjectsFailed() + 1);
                    return;
                }
            }

            if (existing != null && mode == ConfigMode.SEED) {
                result.setProjectsSkipped(result.getProjectsSkipped() + 1);
                log.debug("Seed mode: project '{}' already exists, skipping", configId);
            } else if (existing != null) {
                // Sync mode: update
                updateProjectFromConfig(existing, config);
                projectRepository.save(existing);
                result.setProjectsUpdated(result.getProjectsUpdated() + 1);
                log.info("Updated project '{}' from config", configId);
            } else {
                // Create
                existing = createProjectFromConfig(config);
                result.setProjectsCreated(result.getProjectsCreated() + 1);
                log.info("Created project '{}' from config", configId);
            }

            // Apply children
            String projectId = existing.getId();
            Map<String, String> credRefToDbId = applyCredentials(config.getCredentials(), projectId, configId, mode, result);
            applyVariables(config.getVariables(), projectId, mode, result);
            applyCaches(config.getCaches(), projectId, mode, result);
            applyTags(config.getTags(), result);

            // Apply workflows
            for (Map.Entry<String, WorkflowConfigFile> entry : mp.workflows.entrySet()) {
                applyWorkflow(entry.getValue(), projectId, configId, credRefToDbId, mode, result);
            }

            // Notify cluster of context path changes
            if (config.getContextPath() != null) {
                clusterSyncService.notifyChange(ClusterSyncService.DOMAIN_CONTEXT_PATHS);
            }

        } catch (Exception e) {
            result.addError("Failed to apply project '" + configId + "': " + e.getMessage());
            result.setProjectsFailed(result.getProjectsFailed() + 1);
            log.error("Failed to apply project '{}'", configId, e);
        }
    }

    private ProjectEntity createProjectFromConfig(ProjectConfigFile config) {
        ProjectEntity entity = ProjectEntity.builder()
                .configId(config.getConfigId())
                .name(config.getName())
                .type("TEAM".equalsIgnoreCase(config.getType())
                        ? ProjectEntity.ProjectType.TEAM : ProjectEntity.ProjectType.PERSONAL)
                .contextPath(config.getContextPath())
                .description(config.getDescription())
                .settings(config.getSettings())
                .build();
        entity = projectRepository.save(entity);

        // Add the default owner as admin
        var owners = projectRelationRepository.findAll().stream()
                .filter(r -> r.getRole() == ProjectRelationEntity.ProjectRole.PROJECT_PERSONAL_OWNER)
                .findFirst();
        if (owners.isPresent()) {
            ProjectRelationEntity relation = ProjectRelationEntity.builder()
                    .projectId(entity.getId())
                    .userId(owners.get().getUserId())
                    .role(ProjectRelationEntity.ProjectRole.PROJECT_ADMIN)
                    .build();
            projectRelationRepository.save(relation);
        }

        return entity;
    }

    private void updateProjectFromConfig(ProjectEntity entity, ProjectConfigFile config) {
        if (config.getName() != null) entity.setName(config.getName());
        if (config.getDescription() != null) entity.setDescription(config.getDescription());
        if (config.getContextPath() != null) entity.setContextPath(config.getContextPath());
        if (config.getSettings() != null) entity.setSettings(config.getSettings());
    }

    // ---- Apply Phase: Credentials ----

    private Map<String, String> applyCredentials(List<ProjectConfigFile.CredentialConfig> credentials,
                                                  String projectId, String projectConfigId,
                                                  ConfigMode mode, ConfigReloadResult result) {
        Map<String, String> refToDbId = new LinkedHashMap<>();
        if (credentials == null) return refToDbId;

        for (ProjectConfigFile.CredentialConfig cc : credentials) {
            try {
                String ref = cc.getRef();
                if (ref == null || ref.isBlank()) {
                    result.addWarning("Credential in project '" + projectConfigId + "' missing ref, skipping");
                    continue;
                }

                CredentialEntity existing = credentialRepository.findByProjectIdAndConfigId(projectId, ref)
                        .orElse(null);

                // Build the source placeholder map for round-trip export
                String sourcePlaceholders = buildSourcePlaceholders(cc.getData());

                if (existing != null && mode == ConfigMode.SEED) {
                    refToDbId.put(ref, existing.getId());
                } else if (existing != null) {
                    // Update
                    if (cc.getName() != null) existing.setName(cc.getName());
                    if (cc.getData() != null) {
                        Map<String, Object> data = (Map<String, Object>) cc.getData();
                        existing.setData(encryptionService.encrypt(data));
                    }
                    existing.setSourcePlaceholder(sourcePlaceholders);
                    credentialRepository.save(existing);
                    refToDbId.put(ref, existing.getId());
                    result.setCredentialsApplied(result.getCredentialsApplied() + 1);
                } else {
                    Map<String, Object> data = cc.getData() != null ? (Map<String, Object>) cc.getData() : Map.of();
                    CredentialEntity entity = CredentialEntity.builder()
                            .projectId(projectId)
                            .configId(ref)
                            .name(cc.getName() != null ? cc.getName() : ref)
                            .type(cc.getType())
                            .data(encryptionService.encrypt(data))
                            .sourcePlaceholder(sourcePlaceholders)
                            .build();
                    entity = credentialRepository.save(entity);
                    refToDbId.put(ref, entity.getId());
                    result.setCredentialsApplied(result.getCredentialsApplied() + 1);
                }
            } catch (Exception e) {
                result.addError("Failed to apply credential '" + cc.getRef() + "' in project '"
                        + projectConfigId + "': " + e.getMessage());
            }
        }
        return refToDbId;
    }

    private String buildSourcePlaceholders(Map<String, Object> data) {
        if (data == null) return null;
        // Scan original data values for {{env:...}} patterns before resolution
        // This is called BEFORE placeholder resolution, so we capture originals
        // However, by the time we get here, placeholders are already resolved.
        // We need to store the template separately during the merge phase.
        // For now, return null — this will be enhanced when export is implemented.
        return null;
    }

    // ---- Apply Phase: Variables ----

    private void applyVariables(List<ProjectConfigFile.VariableConfig> variables,
                                 String projectId, ConfigMode mode, ConfigReloadResult result) {
        if (variables == null) return;

        for (ProjectConfigFile.VariableConfig vc : variables) {
            try {
                Optional<VariableEntity> existing = variableRepository.findByKeyAndProjectId(vc.getKey(), projectId);

                if (existing.isPresent() && mode == ConfigMode.SEED) {
                    // Skip
                } else if (existing.isPresent()) {
                    VariableEntity entity = existing.get();
                    entity.setValue(vc.getValue());
                    if (vc.getType() != null) entity.setType(vc.getType());
                    variableRepository.save(entity);
                    result.setVariablesApplied(result.getVariablesApplied() + 1);
                } else {
                    VariableEntity entity = VariableEntity.builder()
                            .key(vc.getKey())
                            .value(vc.getValue())
                            .type(vc.getType() != null ? vc.getType() : "string")
                            .projectId(projectId)
                            .build();
                    variableRepository.save(entity);
                    result.setVariablesApplied(result.getVariablesApplied() + 1);
                }
            } catch (Exception e) {
                result.addError("Failed to apply variable '" + vc.getKey() + "': " + e.getMessage());
            }
        }
    }

    // ---- Apply Phase: Caches ----

    private void applyCaches(List<ProjectConfigFile.CacheConfig> caches,
                              String projectId, ConfigMode mode, ConfigReloadResult result) {
        if (caches == null) return;

        for (ProjectConfigFile.CacheConfig cc : caches) {
            try {
                Optional<CacheDefinitionEntity> existing = cacheDefinitionRepository
                        .findByNameAndProjectId(cc.getName(), projectId);

                if (existing.isPresent() && mode == ConfigMode.SEED) {
                    // Skip
                } else if (existing.isPresent()) {
                    CacheDefinitionEntity entity = existing.get();
                    if (cc.getMaxSize() != null) entity.setMaxSize(cc.getMaxSize());
                    if (cc.getTtlSeconds() != null) entity.setTtlSeconds(cc.getTtlSeconds());
                    if (cc.getDescription() != null) entity.setDescription(cc.getDescription());
                    entity.setConfigId(cc.getName());
                    cacheDefinitionRepository.save(entity);
                    result.setCachesApplied(result.getCachesApplied() + 1);
                } else {
                    CacheDefinitionEntity entity = CacheDefinitionEntity.builder()
                            .name(cc.getName())
                            .configId(cc.getName())
                            .description(cc.getDescription())
                            .maxSize(cc.getMaxSize() != null ? cc.getMaxSize() : 1000)
                            .ttlSeconds(cc.getTtlSeconds() != null ? cc.getTtlSeconds() : 3600)
                            .projectId(projectId)
                            .build();
                    cacheDefinitionRepository.save(entity);
                    result.setCachesApplied(result.getCachesApplied() + 1);
                }
            } catch (Exception e) {
                result.addError("Failed to apply cache '" + cc.getName() + "': " + e.getMessage());
            }
        }
    }

    // ---- Apply Phase: Tags ----

    private void applyTags(List<String> tags, ConfigReloadResult result) {
        if (tags == null) return;
        for (String tagName : tags) {
            tagRepository.findByName(tagName).orElseGet(() -> {
                TagEntity tag = new TagEntity();
                tag.setName(tagName);
                return tagRepository.save(tag);
            });
        }
    }

    // ---- Apply Phase: Workflows ----

    private void applyWorkflow(WorkflowConfigFile wfConfig, String projectId, String projectConfigId,
                                Map<String, String> credRefToDbId, ConfigMode mode,
                                ConfigReloadResult result) {
        String configId = wfConfig.getConfigId();
        try {
            WorkflowEntity existing = workflowRepository.findByProjectIdAndConfigId(projectId, configId)
                    .orElse(null);

            // Resolve credential refs in nodes
            List<Map<String, Object>> resolvedNodes = resolveCredentialRefs(
                    wfConfig.getNodes(), credRefToDbId, projectId, configId, result);

            if (existing != null && mode == ConfigMode.SEED) {
                result.setWorkflowsSkipped(result.getWorkflowsSkipped() + 1);
            } else if (existing != null) {
                // Update
                if (wfConfig.getName() != null) existing.setName(wfConfig.getName());
                if (wfConfig.getDescription() != null) existing.setDescription(wfConfig.getDescription());
                if (wfConfig.getType() != null) existing.setType(wfConfig.getType());
                if (wfConfig.getIcon() != null) existing.setIcon(wfConfig.getIcon());
                existing.setNodes(resolvedNodes);
                if (wfConfig.getConnections() != null) existing.setConnections(wfConfig.getConnections());
                if (wfConfig.getSettings() != null) existing.setSettings(wfConfig.getSettings());
                if (wfConfig.getMcpEnabled() != null) existing.setMcpEnabled(wfConfig.getMcpEnabled());
                if (wfConfig.getMcpDescription() != null) existing.setMcpDescription(wfConfig.getMcpDescription());
                if (wfConfig.getMcpInputSchema() != null) existing.setMcpInputSchema(wfConfig.getMcpInputSchema());
                if (wfConfig.getMcpOutputSchema() != null) existing.setMcpOutputSchema(wfConfig.getMcpOutputSchema());
                if (wfConfig.getSwaggerEnabled() != null) existing.setSwaggerEnabled(wfConfig.getSwaggerEnabled());
                workflowRepository.save(existing);
                result.setWorkflowsUpdated(result.getWorkflowsUpdated() + 1);
                log.info("Updated workflow '{}/{}' from config", projectConfigId, configId);

                // Re-register webhooks if published
                if (existing.isPublished()) {
                    try {
                        webhookService.registerWorkflowWebhooks(existing);
                        clusterSyncService.notifyChange(ClusterSyncService.DOMAIN_WEBHOOKS);
                    } catch (Exception e) {
                        result.addWarning("Failed to re-register webhooks for workflow '" + configId + "': " + e.getMessage());
                    }
                }
            } else {
                // Create
                var builder = WorkflowEntity.builder()
                        .projectId(projectId)
                        .configId(configId)
                        .name(wfConfig.getName() != null ? wfConfig.getName() : configId)
                        .description(wfConfig.getDescription())
                        .nodes(resolvedNodes)
                        .connections(wfConfig.getConnections())
                        .settings(wfConfig.getSettings());

                if (wfConfig.getType() != null) builder.type(wfConfig.getType());
                if (wfConfig.getIcon() != null) builder.icon(wfConfig.getIcon());
                if (wfConfig.getMcpEnabled() != null) builder.mcpEnabled(wfConfig.getMcpEnabled());
                if (wfConfig.getMcpDescription() != null) builder.mcpDescription(wfConfig.getMcpDescription());
                if (wfConfig.getMcpInputSchema() != null) builder.mcpInputSchema(wfConfig.getMcpInputSchema());
                if (wfConfig.getMcpOutputSchema() != null) builder.mcpOutputSchema(wfConfig.getMcpOutputSchema());
                if (wfConfig.getSwaggerEnabled() != null) builder.swaggerEnabled(wfConfig.getSwaggerEnabled());

                WorkflowEntity entity = workflowRepository.save(builder.build());
                result.setWorkflowsCreated(result.getWorkflowsCreated() + 1);
                log.info("Created workflow '{}/{}' from config", projectConfigId, configId);

                // Auto-publish if requested
                if (Boolean.TRUE.equals(wfConfig.getPublished())) {
                    try {
                        entity.setPublished(true);
                        workflowRepository.save(entity);
                        webhookService.registerWorkflowWebhooks(entity);
                        clusterSyncService.notifyChange(ClusterSyncService.DOMAIN_WEBHOOKS);
                        clusterSyncService.notifyChange(ClusterSyncService.DOMAIN_TRIGGERS);
                        result.setWorkflowsPublished(result.getWorkflowsPublished() + 1);
                    } catch (Exception e) {
                        result.addWarning("Failed to auto-publish workflow '" + configId + "': " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            result.addError("Failed to apply workflow '" + configId + "' in project '"
                    + projectConfigId + "': " + e.getMessage());
            result.setWorkflowsFailed(result.getWorkflowsFailed() + 1);
        }
    }

    // ---- Credential Ref Resolution in Workflow Nodes ----

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolveCredentialRefs(
            List<Map<String, Object>> nodes, Map<String, String> credRefToDbId,
            String projectId, String workflowConfigId, ConfigReloadResult result) {

        if (nodes == null) return List.of();

        List<Map<String, Object>> resolved = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            Map<String, Object> copy = new LinkedHashMap<>(node);
            Object credentials = copy.get("credentials");
            if (credentials instanceof Map) {
                Map<String, Object> credMap = new LinkedHashMap<>((Map<String, Object>) credentials);
                for (Map.Entry<String, Object> entry : credMap.entrySet()) {
                    String credType = entry.getKey();
                    Object credValue = entry.getValue();
                    if (credValue instanceof Map) {
                        Map<String, Object> credRef = (Map<String, Object>) credValue;
                        String ref = (String) credRef.get("ref");
                        if (ref != null) {
                            // Ref-based resolution
                            String dbId = credRefToDbId.get(ref);
                            if (dbId != null) {
                                Map<String, Object> resolved2 = new LinkedHashMap<>();
                                resolved2.put("id", dbId);
                                resolved2.put("name", ref);
                                credMap.put(credType, resolved2);
                            } else {
                                result.addWarning("Workflow '" + workflowConfigId + "' node '"
                                        + copy.get("name") + "': credential ref '" + ref + "' not found in project");
                            }
                        } else {
                            // Name-based fallback for UI exports
                            String name = (String) credRef.get("name");
                            if (name != null) {
                                credentialRepository.findByProjectId(projectId).stream()
                                        .filter(c -> name.equals(c.getName()) && credType.equals(c.getType()))
                                        .findFirst()
                                        .ifPresent(c -> {
                                            Map<String, Object> resolved3 = new LinkedHashMap<>();
                                            resolved3.put("id", c.getId());
                                            resolved3.put("name", c.getName());
                                            credMap.put(credType, resolved3);
                                        });
                            }
                        }
                    }
                }
                copy.put("credentials", credMap);
            }
            resolved.add(copy);
        }
        return resolved;
    }

    // ---- Logging ----

    private void logSummary(ConfigReloadResult result) {
        log.info("Config bootstrap complete (mode={}):", result.getMode());
        log.info("  Paths scanned: {} ({})", result.getPathsScanned(),
                String.join(", ", result.getDiscoveryModes()));
        log.info("  Settings: {}", result.getSettingsApplied() > 0 ? "applied" : "none");
        log.info("  Projects: {} created, {} updated, {} skipped, {} failed",
                result.getProjectsCreated(), result.getProjectsUpdated(),
                result.getProjectsSkipped(), result.getProjectsFailed());
        log.info("  Workflows: {} created, {} updated, {} skipped, {} failed, {} published",
                result.getWorkflowsCreated(), result.getWorkflowsUpdated(),
                result.getWorkflowsSkipped(), result.getWorkflowsFailed(),
                result.getWorkflowsPublished());
        log.info("  Credentials: {} | Variables: {} | Caches: {}",
                result.getCredentialsApplied(), result.getVariablesApplied(), result.getCachesApplied());

        for (String error : result.getErrors()) {
            log.error("  ERROR: {}", error);
        }
        for (String warning : result.getWarnings()) {
            log.warn("  WARNING: {}", warning);
        }
    }
}
