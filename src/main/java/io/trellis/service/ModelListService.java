package io.trellis.service;

import io.trellis.dto.ModelInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ModelListService {

    /**
     * Lists available models for a given credential type using the provider's API.
     *
     * @param credentialType the credential type (e.g. "openAiApi", "anthropicApi")
     * @param credentialData decrypted credential data map
     * @param modelType      optional filter: "chat", "embedding", or null for all
     * @return list of available models, sorted by name
     */
    public List<ModelInfo> listModels(String credentialType, Map<String, Object> credentialData, String modelType) {
        try {
            return switch (credentialType) {
                case "openAiApi" -> listOpenAiModels(credentialData, modelType);
                case "anthropicApi" -> listAnthropicModels(credentialData, modelType);
                case "googleAiApi" -> listGeminiModels(credentialData, modelType);
                case "mistralApi" -> listMistralModels(credentialData, modelType);
                case "ollamaApi" -> listOllamaModels(credentialData);
                default -> List.of();
            };
        } catch (Exception e) {
            log.warn("Failed to list models for credential type {}: {}", credentialType, e.getMessage());
            return List.of();
        }
    }

    private List<ModelInfo> listOpenAiModels(Map<String, Object> data, String modelType) {
        var builder = dev.langchain4j.model.openai.OpenAiModelCatalog.builder()
                .apiKey(getString(data, "apiKey"));
        String baseUrl = getString(data, "baseUrl");
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }

        return filterAndMap(builder.build().listModels(), modelType);
    }

    private List<ModelInfo> listAnthropicModels(Map<String, Object> data, String modelType) {
        var builder = dev.langchain4j.model.anthropic.AnthropicModelCatalog.builder()
                .apiKey(getString(data, "apiKey"));
        String baseUrl = getString(data, "baseUrl");
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }

        return filterAndMap(builder.build().listModels(), modelType);
    }

    private List<ModelInfo> listGeminiModels(Map<String, Object> data, String modelType) {
        var builder = dev.langchain4j.model.googleai.GoogleAiGeminiModelCatalog.builder()
                .apiKey(getString(data, "apiKey"));
        String baseUrl = getString(data, "baseUrl");
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }

        return filterAndMap(builder.build().listModels(), modelType);
    }

    private List<ModelInfo> listMistralModels(Map<String, Object> data, String modelType) {
        var builder = dev.langchain4j.model.mistralai.MistralAiModelCatalog.builder()
                .apiKey(getString(data, "apiKey"));
        String baseUrl = getString(data, "baseUrl");
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }

        return filterAndMap(builder.build().listModels(), modelType);
    }

    private List<ModelInfo> listOllamaModels(Map<String, Object> data) {
        String baseUrl = getString(data, "baseUrl");
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:11434";
        }

        var ollamaModels = dev.langchain4j.model.ollama.OllamaModels.builder()
                .baseUrl(baseUrl)
                .build();

        return ollamaModels.availableModels().content().stream()
                .map(m -> ModelInfo.builder()
                        .id(m.getName())
                        .name(m.getName())
                        .build())
                .sorted(Comparator.comparing(ModelInfo::getName))
                .toList();
    }

    private List<ModelInfo> filterAndMap(
            List<dev.langchain4j.model.catalog.ModelDescription> models,
            String modelType) {

        var stream = models.stream();

        if (modelType != null && !modelType.isBlank()) {
            String upperType = modelType.toUpperCase();
            stream = stream.filter(m -> m.type() != null && m.type().name().equals(upperType));
        }

        return stream
                .map(m -> ModelInfo.builder()
                        .id(m.name())
                        .name(m.displayName() != null && !m.displayName().isBlank()
                                ? m.displayName() : m.name())
                        .build())
                .sorted(Comparator.comparing(ModelInfo::getName))
                .toList();
    }

    private String getString(Map<String, Object> data, String key) {
        Object val = data.get(key);
        return val != null ? val.toString() : null;
    }
}
