package io.cwc.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.cwc.config.CwcConfigProperties;
import io.cwc.dto.SettingsConfigFile;
import io.cwc.entity.AiSettingsEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes all application settings to {cwc.config.paths[0]}/settings.json on any change.
 * For encrypted values (like AI API key), writes encrypted blobs to credentials.properties
 * and uses enc:{{env:KEY}} references in settings.json.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsWritebackService {

    private final CwcConfigProperties configProperties;
    private final AiSettingsService aiSettingsService;
    private final ExecutionSettingsService executionSettingsService;
    private final McpSettingsService mcpSettingsService;
    private final SwaggerSettingsService swaggerSettingsService;
    private final PropertiesFileService propertiesFileService;
    private final ConfigBootstrapService configBootstrapService;
    private final ObjectMapper objectMapper;

    private final ReentrantLock writeLock = new ReentrantLock();

    public boolean isEnabled() {
        return configProperties.isWritebackEnabled();
    }

    /**
     * Writes all current application settings to settings.json.
     * Suppressed during bootstrap to avoid feedback loops.
     */
    public void writeSettings() {
        if (!isEnabled()) return;
        if (!configBootstrapService.isComplete()) return;

        writeLock.lock();
        try {
            Path writablePath = configProperties.getWritablePath().orElse(null);
            if (writablePath == null) return;

            SettingsConfigFile settings = assembleSettings();
            Path target = writablePath.resolve("settings.json");
            Files.createDirectories(writablePath);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), settings);
            log.debug("Wrote application settings to {}", target);
        } catch (IOException e) {
            log.error("Failed to write settings.json: {}", e.getMessage());
        } finally {
            writeLock.unlock();
        }
    }

    private SettingsConfigFile assembleSettings() {
        SettingsConfigFile settings = new SettingsConfigFile();

        // AI settings
        AiSettingsEntity aiEntity = aiSettingsService.getEntity();
        if (aiEntity != null) {
            SettingsConfigFile.AiConfig ai = new SettingsConfigFile.AiConfig();
            ai.setProvider(aiEntity.getProvider());
            ai.setModel(aiEntity.getModel());
            ai.setBaseUrl(aiEntity.getBaseUrl());
            ai.setEnabled(aiEntity.isEnabled());

            // Store encrypted API key in properties file, reference in settings.json
            if (aiEntity.getApiKey() != null && !aiEntity.getApiKey().isBlank()) {
                // aiEntity.getApiKey() is already encrypted by AiSettingsService
                propertiesFileService.addPropertyIfAbsent("CWC_AI_API_KEY", "enc:" + aiEntity.getApiKey());
                ai.setApiKey("{{env:CWC_AI_API_KEY}}");
            }
            settings.setAi(ai);
        }

        // Execution settings
        var execDto = executionSettingsService.getSettings();
        if (execDto != null) {
            SettingsConfigFile.ExecutionConfig exec = new SettingsConfigFile.ExecutionConfig();
            exec.setSaveExecutionProgress(execDto.getSaveExecutionProgress());
            exec.setSaveManualExecutions(execDto.getSaveManualExecutions());
            exec.setExecutionTimeout(execDto.getExecutionTimeout());
            exec.setErrorWorkflow(execDto.getErrorWorkflow());
            settings.setExecution(exec);
        }

        // MCP settings
        var mcpDto = mcpSettingsService.getSettings();
        if (mcpDto != null) {
            SettingsConfigFile.McpConfig mcp = new SettingsConfigFile.McpConfig();
            mcp.setEnabled(mcpDto.isEnabled());
            mcp.setAgentToolsEnabled(mcpDto.isAgentToolsEnabled());
            mcp.setAgentToolsDedicated(mcpDto.isAgentToolsDedicated());
            mcp.setAgentToolsPath(mcpDto.getAgentToolsPath());
            mcp.setAgentToolsTransport(mcpDto.getAgentToolsTransport());

            // Endpoints
            var endpoints = mcpSettingsService.listEndpoints(); // already filtered to instance-level
            if (endpoints != null && !endpoints.isEmpty()) {
                var epConfigs = new ArrayList<SettingsConfigFile.McpEndpointConfig>();
                for (var ep : endpoints) {
                    SettingsConfigFile.McpEndpointConfig epConfig = new SettingsConfigFile.McpEndpointConfig();
                    epConfig.setName(ep.getName());
                    epConfig.setTransport(ep.getTransport());
                    epConfig.setPath(ep.getPath());
                    epConfig.setEnabled(ep.isEnabled());
                    epConfigs.add(epConfig);
                }
                mcp.setEndpoints(epConfigs);
            }
            settings.setMcp(mcp);
        }

        // Swagger settings
        var swaggerDto = swaggerSettingsService.getSettings();
        if (swaggerDto != null) {
            SettingsConfigFile.SwaggerConfig swagger = new SettingsConfigFile.SwaggerConfig();
            swagger.setEnabled(swaggerDto.isEnabled());
            swagger.setApiTitle(swaggerDto.getApiTitle());
            swagger.setApiDescription(swaggerDto.getApiDescription());
            swagger.setApiVersion(swaggerDto.getApiVersion());
            settings.setSwagger(swagger);
        }

        return settings;
    }
}
