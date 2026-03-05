package io.trellis.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.trellis.entity.ExecutionEntity;
import io.trellis.repository.CredentialRepository;
import io.trellis.repository.ExecutionRepository;
import io.trellis.repository.UserRepository;
import io.trellis.repository.WebhookRepository;
import io.trellis.repository.WorkflowRepository;
import io.trellis.service.SecurityChainInfoService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SecurityChainInfoService securityChainInfoService;
    private final WorkflowRepository workflowRepository;
    private final ExecutionRepository executionRepository;
    private final CredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final WebhookRepository webhookRepository;

    @Value("${server.port:5678}")
    private int serverPort;

    @Value("${trellis.webhook.base-path:/webhook/}")
    private String webhookBasePath;

    @Value("${trellis.webhook.test-base-path:/webhook-test/}")
    private String webhookTestBasePath;

    @Value("${trellis.support.email:}")
    private String supportEmail;

    @GetMapping
    public Map<String, Object> getSettings() {
        String baseUrl = "http://localhost:" + serverPort;
        Map<String, Object> settings = new LinkedHashMap<>();
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("variables", true);
        features.put("externalSecrets", true);
        features.put("advancedExecutionFilters", true);
        features.put("sharing", true);
        features.put("auditLogs", true);
        features.put("sourceControl", true);
        features.put("environments", true);
        features.put("workerView", true);
        features.put("advancedPermissions", true);
        settings.put("enterprise", Map.of("enabled", true, "features", features));
        settings.put("securityChains", securityChainInfoService.getAvailableChains());
        settings.put("webhookUrlProduction", baseUrl + webhookBasePath);
        settings.put("webhookUrlTest", baseUrl + webhookTestBasePath);
        settings.put("version", "1.0.0");
        settings.put("platform", "trellis");
        if (supportEmail != null && !supportEmail.isBlank()) {
            settings.put("supportEmail", supportEmail);
        }
        return settings;
    }

    @GetMapping("/usage")
    public Map<String, Object> getUsage() {
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("workflows", workflowRepository.count());
        usage.put("executions", executionRepository.count());
        usage.put("executionsSuccess", executionRepository.countByStatus(ExecutionEntity.ExecutionStatus.SUCCESS));
        usage.put("executionsError", executionRepository.countByStatus(ExecutionEntity.ExecutionStatus.ERROR));
        usage.put("credentials", credentialRepository.count());
        usage.put("users", userRepository.count());
        usage.put("activeWebhooks", webhookRepository.count());
        return usage;
    }
}
