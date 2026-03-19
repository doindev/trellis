package io.cwc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import io.cwc.dto.CredentialTestResult;
import io.cwc.dto.ModelInfo;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class ModelListService {

    private static final Set<String> TESTABLE_TYPES = Set.of(
            "openAiApi", "anthropicApi", "googleAiApi", "mistralApi", "ollamaApi"
    );

    /**
     * Tests whether the given credentials are valid by attempting to list models.
     *
     * @param credentialType the credential type (e.g. "openAiApi", "anthropicApi")
     * @param credentialData credential data map (apiKey, baseUrl, etc.)
     * @return result indicating success or failure with error message
     */
    public CredentialTestResult testCredentials(String credentialType, Map<String, Object> credentialData) {
        if (!TESTABLE_TYPES.contains(credentialType)) {
            return CredentialTestResult.success();
        }
        try {
            switch (credentialType) {
                case "openAiApi" -> listOpenAiModels(credentialData, null);
                case "anthropicApi" -> testAnthropicConnection(credentialData);
                case "googleAiApi" -> listGeminiModels(credentialData, null);
                case "mistralApi" -> listMistralModels(credentialData, null);
                case "ollamaApi" -> listOllamaModels(credentialData);
                default -> { /* unreachable */ }
            }
            return CredentialTestResult.success();
        } catch (Exception e) {
            String message = extractErrorMessage(e);
            log.debug("Credential test failed for type {}: {}", credentialType, message);
            return CredentialTestResult.failure(message);
        }
    }

    /**
     * Tests Anthropic credentials by calling /v1/models directly and throwing on any failure.
     * Unlike listAnthropicModels which silently falls back, this propagates errors.
     */
    private void testAnthropicConnection(Map<String, Object> data) throws Exception {
        String apiKey = getString(data, "apiKey");
        String baseUrl = normalizeAnthropicBaseUrl(getString(data, "baseUrl"));

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
            // Parse error message from JSON response if possible
            String errorMsg = "HTTP " + response.statusCode();
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                Map<String, Object> body = om.readValue(response.body(), Map.class);
                @SuppressWarnings("unchecked")
                Map<String, Object> error = (Map<String, Object>) body.get("error");
                if (error != null && error.get("message") != null) {
                    errorMsg = (String) error.get("message");
                }
            } catch (Exception ignored) {}
            throw new RuntimeException(errorMsg + " (" + response.statusCode() + ")");
        }
    }

    private String extractErrorMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            msg = e.getClass().getSimpleName();
        }
        // Extract HTTP status codes from common exception messages
        if (msg.contains("401") || msg.toLowerCase().contains("unauthorized")) {
            return "Invalid API key (401 Unauthorized)";
        }
        if (msg.contains("403") || msg.toLowerCase().contains("forbidden")) {
            return "Access denied (403 Forbidden)";
        }
        if (msg.contains("429") || msg.toLowerCase().contains("rate limit")) {
            return "Rate limited (429 Too Many Requests)";
        }
        if (msg.toLowerCase().contains("connection refused") || msg.toLowerCase().contains("connect timed out")) {
            return "Could not connect to the API server. Check the URL and try again.";
        }
        if (msg.toLowerCase().contains("unknown host") || msg.toLowerCase().contains("nodename nor servname")) {
            return "Could not resolve API host. Check the URL and try again.";
        }
        // Truncate long messages
        if (msg.length() > 200) {
            msg = msg.substring(0, 200) + "...";
        }
        return msg;
    }

    /**
     * Lists available models for a given credential type using the provider's API.
     *
     * @param credentialType the credential type (e.g. "openAiApi", "anthropicApi")
     * @param credentialData decrypted credential data map
     * @param modelType      optional filter: "chat", "embedding", or null for all
     * @return list of available models, sorted by name
     */
    public List<ModelInfo> listModels(String credentialType, Map<String, Object> credentialData, String modelType) {
        log.info("listModels called: type='{}', modelType='{}'", credentialType, modelType);
        return switch (credentialType) {
            case "openAiApi" -> listOpenAiModels(credentialData, modelType);
            case "anthropicApi" -> listAnthropicModels(credentialData, modelType);
            case "googleAiApi" -> listGeminiModels(credentialData, modelType);
            case "mistralApi" -> listMistralModels(credentialData, modelType);
            case "ollamaApi" -> listOllamaModels(credentialData);
            default -> List.of();
        };
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
                // Normalize: LangChain4j expects baseUrl with /v1 suffix
                baseUrl = baseUrl.replaceAll("/+$", "");
                if (!baseUrl.endsWith("/v1")) {
                    baseUrl = baseUrl + "/v1";
                }
                builder.baseUrl(baseUrl);
            }

            var models = builder.build().listModels();
            log.info("Anthropic model catalog returned {} models", models.size());
            if (!models.isEmpty()) {
                var first = models.get(0);
                log.info("First model: name='{}', type={}", first.name(), first.type());
            }
            var result = filterAndMap(models, modelType);
            if (result.isEmpty() && !models.isEmpty()) {
                log.warn("filterAndMap returned 0 from {} catalog models — falling back to direct API", models.size());
                return listAnthropicModelsDirect(data, modelType);
            }
            return result;
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
        String baseUrl = normalizeAnthropicBaseUrl(getString(data, "baseUrl"));
        log.info("listAnthropicModelsDirect: using baseUrl='{}', full URL='{}'", baseUrl, baseUrl + "/v1/models");

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

    /**
     * Normalizes the Anthropic base URL to the root domain (without /v1 suffix).
     * Used by methods that manually construct full API paths like /v1/models.
     */
    private String normalizeAnthropicBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.anthropic.com";
        }
        baseUrl = baseUrl.replaceAll("/+$", "");
        if (baseUrl.endsWith("/v1")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 3);
        }
        return baseUrl;
    }

    private String getString(Map<String, Object> data, String key) {
        Object val = data.get(key);
        return val != null ? val.toString() : null;
    }
}
