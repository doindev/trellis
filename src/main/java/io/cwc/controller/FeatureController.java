package io.cwc.controller;

import io.cwc.config.FeatureFlags;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exposes runtime feature flags to the frontend so the UI can hide
 * views, settings panels, and menu items for features whose backend
 * dependencies are not on the classpath.
 */
@RestController
@RequestMapping("/api/features")
@RequiredArgsConstructor
public class FeatureController {

    private final FeatureFlags featureFlags;
    private String frontendBuild = null;

    @PostConstruct
    void init() {
        // Read the build number generated during the Angular compile
        try {
            var resource = new ClassPathResource("static/.cwc-build");
            if (resource.exists()) {
                frontendBuild = new String(resource.getInputStream().readAllBytes()).trim();
            }
        } catch (Exception e) {
            // No build file — frontend not present or not built with build number
        }
    }

    @GetMapping
    public Map<String, Object> getFeatures() {
        var flags = new LinkedHashMap<String, Object>();
        flags.put("langchain4j", featureFlags.isLangchain4jAvailable());
        flags.put("swagger", featureFlags.isSwaggerAvailable());
        flags.put("mcpServer", featureFlags.isMcpServerAvailable());
        flags.put("git", featureFlags.isGitAvailable());
        flags.put("frontend", featureFlags.isFrontendAvailable());
        if (frontendBuild != null) {
            flags.put("frontendBuild", frontendBuild);
        }
        return flags;
    }
}
