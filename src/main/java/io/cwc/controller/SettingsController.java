package io.cwc.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.cwc.entity.ExecutionEntity;
import io.cwc.repository.CredentialRepository;
import io.cwc.repository.ExecutionRepository;
import io.cwc.repository.UserRepository;
import io.cwc.repository.WebhookRepository;
import io.cwc.repository.WorkflowRepository;
import io.cwc.config.CwcProperties;
import io.cwc.service.SecurityChainInfoService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final CwcProperties cwcProperties;
    private final SecurityChainInfoService securityChainInfoService;
    private final WorkflowRepository workflowRepository;
    private final ExecutionRepository executionRepository;
    private final CredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final WebhookRepository webhookRepository;

    @Value("${cwc.webhook.base-path:/webhook/}")
    private String webhookBasePath;

    @Value("${cwc.webhook.test-base-path:/webhook-test/}")
    private String webhookTestBasePath;

    @Value("${cwc.support.email:}")
    private String supportEmail;

    @GetMapping
    public Map<String, Object> getSettings(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getHeader("Host");
        if (host == null) host = request.getServerName() + ":" + request.getServerPort();
        String baseUrl = scheme + "://" + host;
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
        String ctxPath = cwcProperties.getContextPath();
        settings.put("webhookUrlProduction", baseUrl + ctxPath + webhookBasePath);
        settings.put("webhookUrlTest", baseUrl + ctxPath + webhookTestBasePath);
        settings.put("cwcContextPath", ctxPath);
        settings.put("cwcUiContextPath", cwcProperties.getUiContextPath());
        settings.put("version", "1.0.0");
        settings.put("platform", "cwc");
        if (supportEmail != null && !supportEmail.isBlank()) {
            settings.put("supportEmail", supportEmail);
        }
        settings.put("allowNonOwnerChanges", cwcProperties.isAllowNonOwnerChanges());
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
