package io.cwc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Core CWC properties.
 *
 * <p>The UI and API/webhook paths can be configured independently:
 * <pre>
 *   cwc.context-path=/            # API, webhooks, MCP — defaults to root
 *   cwc.ui.context-path=/cwc      # CWC browser UI — defaults to cwc.context-path
 * </pre>
 *
 * <p>If only {@code cwc.context-path} is set, both UI and API share it (original behavior).
 * If {@code cwc.ui.context-path} is also set, the UI is served at a different path from the API.
 */
@Slf4j
@Component
public class CwcProperties {

    /** API/workflow context path — where REST API, webhooks, and MCP endpoints are served. */
    @Value("${cwc.context-path:/}")
    private String contextPath;

    /** UI context path — where the CWC browser UI is served. Defaults to cwc.context-path. */
    @Value("${cwc.ui.context-path:${cwc.context-path:/}}")
    private String uiContextPath;

    @Value("${cwc.allow-non-owner-changes:false}")
    private boolean allowNonOwnerChanges;

    private String normalizedPath;
    private String normalizedUiPath;

    @PostConstruct
    void init() {
        normalizedPath = normalizePath(contextPath);
        normalizedUiPath = normalizePath(uiContextPath);
        log.info("CWC paths: api='{}', ui='{}'",
                normalizedPath.isEmpty() ? "/" : normalizedPath,
                normalizedUiPath.isEmpty() ? "/" : normalizedUiPath);
    }

    private static String normalizePath(String raw) {
        String path = raw.trim();
        while (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        if ("/".equals(path) || path.isEmpty()) {
            return "";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    /** Returns the normalized API/workflow context path, e.g. "" for root or "/myapp". */
    public String getContextPath() {
        return normalizedPath;
    }

    /** Returns the normalized UI context path, e.g. "" for root or "/cwc". */
    public String getUiContextPath() {
        return normalizedUiPath;
    }

    /** True when the API/workflow context path is root ("/"). */
    public boolean isRootContext() {
        return normalizedPath.isEmpty();
    }

    /** True when the UI context path is root ("/"). */
    public boolean isRootUiContext() {
        return normalizedUiPath.isEmpty();
    }

    public boolean isAllowNonOwnerChanges() {
        return allowNonOwnerChanges;
    }
}
