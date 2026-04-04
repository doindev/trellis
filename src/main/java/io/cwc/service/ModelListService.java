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
public class ModelListService implements ModelListProvider {

    private static final Set<String> TESTABLE_TYPES = Set.of(
            "openAiApi", "anthropicApi", "googleAiApi", "mistralApi", "ollamaApi"
    );

    @Override
    public CredentialTestResult testCredentials(String credentialType, Map<String, Object> credentialData) {
        if (!TESTABLE_TYPES.contains(credentialType)) {
            return CredentialTestResult.failure("Credential testing not supported for type: " + credentialType);
        }
        try {
            List<ModelInfo> models = listModels(credentialType, credentialData, null);
            if (models.isEmpty()) {
                return CredentialTestResult.failure("Connected but no models found");
            }
            return CredentialTestResult.success();
        } catch (Exception e) {
            String msg = friendlyError(e.getMessage());
            return CredentialTestResult.failure(msg);
        }
    }

    private String friendlyError(String msg) {
        if (msg == null) return "Unknown error";
        if (msg.contains("401") || msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("invalid api key")) {
            return "Invalid API key. Check the key and try again.";
        }
        if (msg.toLowerCase().contains("connection refused") || msg.toLowerCase().contains("connect timed out")) {
            return "Could not connect to the API server. Check the URL and try again.";
        }
        if (msg.toLowerCase().contains("unknown host") || msg.toLowerCase().contains("nodename nor servname")) {
            return "Could not resolve API host. Check the URL and try again.";
        }
        if (msg.length() > 200) {
            msg = msg.substring(0, 200) + "...";
        }
        return msg;
    }

    @Override
    public List<ModelInfo> listModels(String credentialType, Map<String, Object> credentialData, String modelType) {
        log.info("listModels called: type='{}', modelType='{}'", credentialType, modelType);
        try {
            return switch (credentialType) {
                case "openAiApi" -> OpenAiCatalog.list(credentialData, modelType);
                case "anthropicApi" -> AnthropicCatalog.list(credentialData, modelType);
                case "googleAiApi" -> GeminiCatalog.list(credentialData, modelType);
                case "mistralApi" -> MistralCatalog.list(credentialData, modelType);
                case "ollamaApi" -> OllamaCatalog.list(credentialData);
                default -> List.of();
            };
        } catch (NoClassDefFoundError e) {
            log.warn("Provider module not on classpath for '{}': {}", credentialType, e.getMessage());
            return List.of();
        }
    }

    // ── Provider-specific inner classes ──
    // Each lives in its own class so the JVM only loads it (and its langchain4j imports)
    // when actually invoked. Missing provider jars won't crash class introspection.

    private static class OpenAiCatalog {
        static List<ModelInfo> list(Map<String, Object> data, String modelType) {
            var builder = dev.langchain4j.model.openai.OpenAiModelCatalog.builder()
                    .apiKey(str(data, "apiKey"));
            String baseUrl = str(data, "baseUrl");
            if (baseUrl != null && !baseUrl.isBlank()) builder.baseUrl(baseUrl);
            return filterAndMap(builder.build().listModels(), modelType);
        }
    }

    private static class AnthropicCatalog {
        static List<ModelInfo> list(Map<String, Object> data, String modelType) {
            try {
                var builder = dev.langchain4j.model.anthropic.AnthropicModelCatalog.builder()
                        .apiKey(str(data, "apiKey"));
                String baseUrl = str(data, "baseUrl");
                if (baseUrl != null && !baseUrl.isBlank()) {
                    baseUrl = baseUrl.replaceAll("/+$", "");
                    if (!baseUrl.endsWith("/v1")) baseUrl = baseUrl + "/v1";
                    builder.baseUrl(baseUrl);
                }
                var models = builder.build().listModels();
                var result = filterAndMap(models, modelType);
                if (result.isEmpty() && !models.isEmpty()) return listDirect(data, modelType);
                return result;
            } catch (Exception e) {
                return listDirect(data, modelType);
            }
        }

        @SuppressWarnings("unchecked")
        private static List<ModelInfo> listDirect(Map<String, Object> data, String modelType) {
            String apiKey = str(data, "apiKey");
            String baseUrl = str(data, "baseUrl");
            if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.anthropic.com";
            baseUrl = baseUrl.replaceAll("/+$", "");
            if (baseUrl.endsWith("/v1")) baseUrl = baseUrl.substring(0, baseUrl.length() - 3);
            try {
                var client = java.net.http.HttpClient.newHttpClient();
                var request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(baseUrl + "/v1/models"))
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .GET().build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) return List.of();
                var om = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> body = om.readValue(response.body(), Map.class);
                List<Map<String, Object>> modelList = (List<Map<String, Object>>) body.get("data");
                if (modelList == null) return List.of();
                return modelList.stream()
                        .filter(m -> modelType == null || modelType.isBlank() || "chat".equalsIgnoreCase(modelType))
                        .map(m -> ModelInfo.builder()
                                .id((String) m.get("id"))
                                .name(m.get("display_name") != null ? (String) m.get("display_name") : (String) m.get("id"))
                                .build())
                        .sorted(Comparator.comparing(ModelInfo::getName))
                        .toList();
            } catch (Exception e) {
                return List.of();
            }
        }
    }

    private static class GeminiCatalog {
        static List<ModelInfo> list(Map<String, Object> data, String modelType) {
            var builder = dev.langchain4j.model.googleai.GoogleAiGeminiModelCatalog.builder()
                    .apiKey(str(data, "apiKey"));
            String baseUrl = str(data, "baseUrl");
            if (baseUrl != null && !baseUrl.isBlank()) builder.baseUrl(baseUrl);
            return filterAndMap(builder.build().listModels(), modelType);
        }
    }

    private static class MistralCatalog {
        static List<ModelInfo> list(Map<String, Object> data, String modelType) {
            var builder = dev.langchain4j.model.mistralai.MistralAiModelCatalog.builder()
                    .apiKey(str(data, "apiKey"));
            String baseUrl = str(data, "baseUrl");
            if (baseUrl != null && !baseUrl.isBlank()) builder.baseUrl(baseUrl);
            return filterAndMap(builder.build().listModels(), modelType);
        }
    }

    private static class OllamaCatalog {
        static List<ModelInfo> list(Map<String, Object> data) {
            String baseUrl = str(data, "baseUrl");
            if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://localhost:11434";
            var ollamaModels = dev.langchain4j.model.ollama.OllamaModels.builder()
                    .baseUrl(baseUrl)
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();
            return ollamaModels.availableModels().content().stream()
                    .map(m -> ModelInfo.builder().id(m.getName()).name(m.getName()).build())
                    .sorted(Comparator.comparing(ModelInfo::getName))
                    .toList();
        }
    }

    // ── Shared helpers ──

    private static List<ModelInfo> filterAndMap(
            List<dev.langchain4j.model.catalog.ModelDescription> models, String modelType) {
        var stream = models.stream();
        if (modelType != null && !modelType.isBlank()) {
            String upperType = modelType.toUpperCase();
            stream = stream.filter(m -> m.type() != null && m.type().name().equals(upperType));
        }
        return stream
                .map(m -> ModelInfo.builder()
                        .id(m.name())
                        .name(m.displayName() != null && !m.displayName().isBlank() ? m.displayName() : m.name())
                        .build())
                .sorted(Comparator.comparing(ModelInfo::getName))
                .toList();
    }

    private static String str(Map<String, Object> data, String key) {
        Object val = data.get(key);
        return val != null ? val.toString() : null;
    }
}
