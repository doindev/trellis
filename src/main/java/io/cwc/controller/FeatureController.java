package io.cwc.controller;

import io.cwc.config.FeatureFlags;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping
    public Map<String, Boolean> getFeatures() {
        return Map.of(
            "langchain4j", featureFlags.isLangchain4jAvailable(),
            "swagger",     featureFlags.isSwaggerAvailable(),
            "mcpServer",   featureFlags.isMcpServerAvailable(),
            "frontend",    featureFlags.isFrontendAvailable()
        );
    }
}
