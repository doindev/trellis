package io.cwc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import io.cwc.dto.AiSettingsDto;
import io.cwc.dto.ModelInfo;
import io.cwc.service.AiSettingsService;
import io.cwc.service.ModelListService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings/ai")
@RequiredArgsConstructor
public class AiSettingsController {

    private final AiSettingsService aiSettingsService;
    private final ModelListService modelListService;

    private static final Map<String, String> PROVIDER_TO_CREDENTIAL_TYPE = Map.of(
            "openai", "openAiApi",
            "anthropic", "anthropicApi",
            "google", "googleAiApi",
            "mistral", "mistralApi",
            "ollama", "ollamaApi",
            "azure-openai", "openAiApi"
    );

    @GetMapping
    public AiSettingsDto getSettings() {
        return aiSettingsService.getSettings();
    }

    @PutMapping
    public AiSettingsDto updateSettings(@RequestBody AiSettingsDto dto) {
        return aiSettingsService.saveSettings(dto);
    }

    @PostMapping("/models")
    public List<ModelInfo> listModels(@RequestBody AiSettingsDto dto) {
        String credentialType = PROVIDER_TO_CREDENTIAL_TYPE.get(dto.getProvider());
        if (credentialType == null) {
            return List.of();
        }
        Map<String, Object> credentialData = new HashMap<>();
        credentialData.put("apiKey", dto.getApiKey());
        if (dto.getBaseUrl() != null && !dto.getBaseUrl().isBlank()) {
            credentialData.put("baseUrl", dto.getBaseUrl());
        }
        return modelListService.listModels(credentialType, credentialData, "chat");
    }
}
