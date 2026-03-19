package io.cwc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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

        // Use stored key if the provided one is masked or empty
        String apiKey = dto.getApiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("...")) {
            apiKey = aiSettingsService.getDecryptedApiKey();
        }
        if (apiKey != null) {
            credentialData.put("apiKey", apiKey);
        }
        if (dto.getBaseUrl() != null && !dto.getBaseUrl().isBlank()) {
            credentialData.put("baseUrl", dto.getBaseUrl());
        }

        try {
            return modelListService.listModels(credentialType, credentialData, "chat");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, extractErrorMessage(e));
        }
    }

    private String extractErrorMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return e.getClass().getSimpleName();
        if (msg.contains("401") || msg.toLowerCase().contains("unauthorized"))
            return "Invalid API key";
        if (msg.contains("403") || msg.toLowerCase().contains("forbidden"))
            return "Access denied";
        if (msg.toLowerCase().contains("connection refused") || msg.toLowerCase().contains("connect timed out"))
            return "Could not connect to the API server. Check the URL and try again.";
        if (msg.toLowerCase().contains("unknown host") || msg.toLowerCase().contains("nodename nor servname"))
            return "Could not resolve API host. Check the URL and try again.";
        if (msg.length() > 200) msg = msg.substring(0, 200) + "...";
        return msg;
    }
}
