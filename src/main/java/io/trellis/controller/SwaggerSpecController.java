package io.trellis.controller;

import io.trellis.entity.SwaggerSettingsEntity;
import io.trellis.entity.WorkflowEntity;
import io.trellis.repository.SwaggerSettingsRepository;
import io.trellis.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SwaggerSpecController {

    private final SwaggerSettingsRepository swaggerSettingsRepository;
    private final WorkflowRepository workflowRepository;

    @GetMapping("/api/swagger/spec")
    public Map<String, Object> getSpec() {
        SwaggerSettingsEntity settings = swaggerSettingsRepository.findFirstByOrderByCreatedAtAsc()
                .orElse(null);

        String title = settings != null && settings.getApiTitle() != null ? settings.getApiTitle() : "Trellis API";
        String description = settings != null && settings.getApiDescription() != null ? settings.getApiDescription() : "Workflow webhook endpoints";
        String version = settings != null && settings.getApiVersion() != null ? settings.getApiVersion() : "1.0.0";

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("openapi", "3.0.3");
        spec.put("info", Map.of("title", title, "description", description, "version", version));
        spec.put("paths", buildPaths());
        return spec;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildPaths() {
        Map<String, Object> paths = new LinkedHashMap<>();
        List<WorkflowEntity> workflows = workflowRepository.findBySwaggerEnabledTrue();

        for (WorkflowEntity workflow : workflows) {
            Object nodesObj = workflow.getNodes();
            if (!(nodesObj instanceof List<?> nodeList)) continue;

            for (Object nodeObj : nodeList) {
                if (!(nodeObj instanceof Map<?, ?> node)) continue;
                if (!"webhook".equals(node.get("type"))) continue;

                Object paramsObj = node.get("parameters");
                Map<String, Object> parameters = paramsObj instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
                String webhookPath = (String) parameters.getOrDefault("path", "");
                if (webhookPath.isEmpty()) continue;

                String httpMethod = ((String) parameters.getOrDefault("httpMethod", "GET")).toLowerCase();
                String normalizedPath = webhookPath.startsWith("/") ? webhookPath : "/" + webhookPath;
                String pathKey = "/webhook" + normalizedPath;

                String summary = workflow.getMcpDescription() != null ? workflow.getMcpDescription()
                        : workflow.getDescription() != null ? workflow.getDescription()
                        : workflow.getName();

                Map<String, Object> operation = new LinkedHashMap<>();
                operation.put("summary", summary);
                operation.put("operationId", workflow.getId() + "_" + node.get("id"));
                operation.put("tags", List.of("Workflows"));

                // Request body only for methods that support it
                if ("post".equals(httpMethod) || "put".equals(httpMethod) || "patch".equals(httpMethod)) {
                    Map<String, Object> requestSchema = buildInputSchema(workflow.getMcpInputSchema());
                    Map<String, Object> requestBody = new LinkedHashMap<>();
                    requestBody.put("required", true);
                    requestBody.put("content", Map.of(
                            "application/json", Map.of("schema", requestSchema)
                    ));
                    operation.put("requestBody", requestBody);
                }

                // Response from mcpOutputSchema
                Map<String, Object> responseSchema = buildOutputSchema(workflow.getMcpOutputSchema());
                Map<String, Object> response200 = new LinkedHashMap<>();
                response200.put("description", "Successful response");
                if (responseSchema != null) {
                    response200.put("content", Map.of(
                            "application/json", Map.of("schema", responseSchema)
                    ));
                }
                operation.put("responses", Map.of("200", response200));

                paths.put(pathKey, Map.of(httpMethod, operation));
            }
        }

        return paths;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildInputSchema(Object mcpInputSchema) {
        if (mcpInputSchema instanceof List<?> paramList && !paramList.isEmpty()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();
            for (Object item : paramList) {
                if (item instanceof Map<?, ?> param) {
                    String name = (String) param.get("name");
                    String type = (String) param.get("type");
                    String desc = (String) param.get("description");
                    Boolean isRequired = (Boolean) param.get("required");
                    if (name == null || name.isBlank()) continue;
                    Map<String, Object> prop = new LinkedHashMap<>();
                    prop.put("type", type != null ? type : "string");
                    if (desc != null && !desc.isBlank()) {
                        prop.put("description", desc);
                    }
                    properties.put(name, prop);
                    if (Boolean.TRUE.equals(isRequired)) {
                        required.add(name);
                    }
                }
            }
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", properties);
            if (!required.isEmpty()) {
                schema.put("required", required);
            }
            return schema;
        }
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "input", Map.of(
                                "type", "string",
                                "description", "Input data for the workflow"
                        )
                )
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildOutputSchema(Object mcpOutputSchema) {
        if (!(mcpOutputSchema instanceof Map<?, ?> schemaMap)) return null;
        String format = (String) schemaMap.get("format");
        if (!"json".equals(format)) return null;

        List<?> properties = (List<?>) schemaMap.get("properties");
        if (properties == null || properties.isEmpty()) return null;

        Map<String, Object> schemaProps = new LinkedHashMap<>();
        for (Object item : properties) {
            if (item instanceof Map<?, ?> prop) {
                String name = (String) prop.get("name");
                String type = (String) prop.get("type");
                String desc = (String) prop.get("description");
                if (name == null || name.isBlank()) continue;
                Map<String, Object> propSchema = new LinkedHashMap<>();
                propSchema.put("type", type != null ? type : "string");
                if (desc != null && !desc.isBlank()) {
                    propSchema.put("description", desc);
                }
                schemaProps.put(name, propSchema);
            }
        }

        if (schemaProps.isEmpty()) return null;

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", schemaProps);
        String schemaDescription = (String) schemaMap.get("description");
        if (schemaDescription != null && !schemaDescription.isBlank()) {
            schema.put("description", schemaDescription);
        }
        return schema;
    }
}
