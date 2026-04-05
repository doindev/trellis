package io.cwc.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Detects which optional features are available at startup.
 *
 * Each feature is enabled when BOTH conditions are met:
 *   1. The required dependency is on the classpath (auto-detected)
 *   2. The corresponding property is not explicitly set to {@code false}
 *
 * <p>Properties (all default to {@code true} / auto-detect):
 * <pre>
 *   cwc.features.langchain4j.enabled=false   # disable AI / chat
 *   cwc.features.swagger.enabled=false        # disable Swagger UI
 *   cwc.features.mcp-server.enabled=false     # disable MCP server
 *   cwc.git.enabled=false                     # disable Git sync (also requires cwc-git module)
 * </pre>
 *
 * When this project is split into Maven modules, each module will bring its own
 * dependencies. Features that depend on absent modules will be automatically
 * disabled — no code changes required.
 */
@Slf4j
@Component
@Getter
@RequiredArgsConstructor
public class FeatureFlags {

    private final ApplicationContext applicationContext;
    private final Environment environment;

    // ── AI / LangChain4j ──
    private boolean langchain4jAvailable;

    // ── Swagger / OpenAPI (springdoc) ──
    private boolean swaggerAvailable;

    // ── MCP Server ──
    private boolean mcpServerAvailable;

    // ── Git sync ──
    private boolean gitAvailable;

    // ── CWC Frontend UI (Angular SPA) ──
    private boolean frontendAvailable;

    @PostConstruct
    void detect() {
        langchain4jAvailable = propertyEnabled("cwc.features.langchain4j.enabled")
                && classExists("dev.langchain4j.model.chat.ChatModel");

        swaggerAvailable = propertyEnabled("cwc.features.swagger.enabled")
                && (classExists("org.springdoc.core.configuration.SpringDocConfiguration")
                    || beanExists("swaggerSettingsService"));

        mcpServerAvailable = propertyEnabled("cwc.features.mcp-server.enabled")
                && (classExists("org.springframework.ai.mcp.server.autoconfigure.McpServerAutoConfiguration")
                    || beanExists("cwcMcpServerManager"));

        // Git: cwc-git module present AND cwc.git.enabled=true
        gitAvailable = "true".equalsIgnoreCase(environment.getProperty("cwc.git.enabled"))
                && beanExists("gitSyncService");

        frontendAvailable = getClass().getClassLoader().getResource("static/.cwc-ui") != null;

        log.info("Feature detection: langchain4j={}, swagger={}, mcpServer={}, git={}, frontend={}",
                langchain4jAvailable, swaggerAvailable, mcpServerAvailable, gitAvailable, frontendAvailable);
    }

    /** Returns {@code true} unless the property is explicitly {@code "false"}. */
    private boolean propertyEnabled(String key) {
        String value = environment.getProperty(key);
        return value == null || !"false".equalsIgnoreCase(value.trim());
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className, false, FeatureFlags.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean beanExists(String beanName) {
        return applicationContext.containsBean(beanName);
    }
}
