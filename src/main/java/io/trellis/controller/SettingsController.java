package io.trellis.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.trellis.service.SecurityChainInfoService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SecurityChainInfoService securityChainInfoService;

    @GetMapping
    public Map<String, Object> getSettings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("variables", true);
        features.put("externalSecrets", true);
        features.put("saml", true);
        features.put("ldap", true);
        features.put("advancedExecutionFilters", true);
        features.put("sharing", true);
        features.put("auditLogs", true);
        features.put("sourceControl", true);
        features.put("environments", true);
        features.put("workerView", true);
        features.put("advancedPermissions", true);
        settings.put("enterprise", Map.of("enabled", true, "features", features));
        settings.put("securityChains", securityChainInfoService.getAvailableChains());
        settings.put("version", "1.0.0");
        settings.put("platform", "trellis");
        return settings;
    }
}
