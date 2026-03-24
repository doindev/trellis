package io.cwc.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Deserialization target for project.json — project definition with variables, credentials, caches, MCP.
 */
@Data
public class ProjectConfigFile {

    private String configId;
    private String name;
    private String type;
    private String contextPath;
    private String description;
    private Map<String, Object> settings;

    private ProjectMcpConfig mcp;
    private List<VariableConfig> variables;
    private List<CredentialConfig> credentials;
    private List<CacheConfig> caches;
    private List<String> tags;

    @Data
    public static class ProjectMcpConfig {
        private Boolean enabled;
        private String path;
        private String transport;
        private List<SettingsConfigFile.McpEndpointConfig> endpoints;
    }

    @Data
    public static class VariableConfig {
        private String key;
        private String value;
        private String type;
    }

    @Data
    public static class CredentialConfig {
        private String ref;
        private String name;
        private String type;
        private Map<String, Object> data;
    }

    @Data
    public static class CacheConfig {
        private String name;
        private String description;
        private Integer maxSize;
        private Long ttlSeconds;
    }
}
