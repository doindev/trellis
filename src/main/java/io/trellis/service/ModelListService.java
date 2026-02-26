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
        try {
            var builder = dev.langchain4j.model.anthropic.AnthropicModelCatalog.builder()
                    .apiKey(getString(data, "apiKey"));
            String baseUrl = getString(data, "baseUrl");
            if (baseUrl != null && !baseUrl.isBlank()) {
                builder.baseUrl(baseUrl);
            }

            var models = builder.build().listModels();
            log.debug("Anthropic model catalog returned {} models", models.size());
            return filterAndMap(models, modelType);
        } catch (Exception e) {
            log.warn("Anthropic model catalog failed, falling back to direct API call: {}", e.getMessage());
            return listAnthropicModelsDirect(data, modelType);
        }
    }

    /**
     * Fallback: call the Anthropic /v1/models endpoint directly via HTTP.
     */
    @SuppressWarnings("unchecked")
    private List<ModelInfo> listAnthropicModelsDirect(Map<String, Object> data, String modelType) {
        String apiKey = getString(data, "apiKey");
        String baseUrl = getString(data, "baseUrl");
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.anthropic.com";
        }
        // Strip trailing slash
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseUrl + "/v1/models"))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .GET()
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Anthropic /v1/models returned status {}: {}", response.statusCode(), response.body());
                return List.of();
            }

            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> body = om.readValue(response.body(), Map.class);
            List<Map<String, Object>> modelList = (List<Map<String, Object>>) body.get("data");
            if (modelList == null) return List.of();

            return modelList.stream()
                    .filter(m -> {
                        if (modelType == null || modelType.isBlank()) return true;
                        // Anthropic models are all chat models
                        return "chat".equalsIgnoreCase(modelType);
                    })
                    .map(m -> ModelInfo.builder()
                            .id((String) m.get("id"))
                            .name(m.get("display_name") != null ? (String) m.get("display_name") : (String) m.get("id"))
                            .build())
                    .sorted(Comparator.comparing(ModelInfo::getName))
                    .toList();
        } catch (Exception e) {
            log.error("Direct Anthropic model listing failed: {}", e.getMessage(), e);
            return List.of();
        }
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
