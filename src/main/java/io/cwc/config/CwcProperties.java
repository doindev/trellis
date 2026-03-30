package io.cwc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class CwcProperties {

    @Value("${cwc.context-path:/}")
    private String contextPath;

    @Value("${cwc.allow-non-owner-changes:false}")
    private boolean allowNonOwnerChanges;

    private String normalizedPath;

    @PostConstruct
    void init() {
        String path = contextPath.trim();
        // Strip trailing slashes
        while (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        // Treat "/" as empty (root context)
        if ("/".equals(path) || path.isEmpty()) {
            normalizedPath = "";
        } else {
            // Ensure leading slash
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            normalizedPath = path;
        }
    }

    /** Returns the normalized context path, e.g. "/cwc" or "" for root. */
    public String getContextPath() {
        return normalizedPath;
    }

    /** True when CWC is served at the root ("/"). */
    public boolean isRootContext() {
        return normalizedPath.isEmpty();
    }

    /** True when non-owner users are allowed to make and deploy changes from the UI. */
    public boolean isAllowNonOwnerChanges() {
        return allowNonOwnerChanges;
    }
}
