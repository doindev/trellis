package io.cwc.dto;

import lombok.Data;
import java.util.List;

/**
 * Deserialization target for settings.json — global application settings.
 */
@Data
public class SettingsConfigFile {

    private AiConfig ai;
    private ExecutionConfig execution;
    private McpConfig mcp;
    private SwaggerConfig swagger;
    private List<GitRepoConfig> gitRepos;
    private List<EnvironmentConfig> environments;

    @Data
    public static class AiConfig {
        private String provider;
        private String model;
        private String baseUrl;
        private String apiKey;
        private Boolean enabled;
    }

    @Data
    public static class ExecutionConfig {
        private String saveExecutionProgress;
        private String saveManualExecutions;
        private Integer executionTimeout;
        private String errorWorkflow;
    }

    @Data
    public static class McpConfig {
        private Boolean enabled;
        private Boolean agentToolsEnabled;
        private Boolean agentToolsDedicated;
        private String agentToolsPath;
        private String agentToolsTransport;
        private List<McpEndpointConfig> endpoints;
    }

    @Data
    public static class McpEndpointConfig {
        private String name;
        private String transport;
        private String path;
        private Boolean enabled;
    }

    @Data
    public static class SwaggerConfig {
        private Boolean enabled;
        private String apiTitle;
        private String apiDescription;
        private String apiVersion;
    }

    @Data
    public static class GitRepoConfig {
        private String configId;
        private String url;
        private String branch;
        private String token;
    }

    @Data
    public static class EnvironmentConfig {
        private String name;
        private String branch;
        private String description;
    }
}
