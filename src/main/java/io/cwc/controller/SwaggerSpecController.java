package io.cwc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.cwc.entity.SwaggerSettingsEntity;
import io.cwc.entity.WorkflowEntity;
import io.cwc.entity.WorkflowVersionEntity;
import io.cwc.repository.SwaggerSettingsRepository;
import io.cwc.repository.WorkflowRepository;
import io.cwc.repository.WorkflowVersionRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springdoc.core.configuration.SpringDocConfiguration")
@ConditionalOnProperty(name = "cwc.features.swagger.enabled", havingValue = "true", matchIfMissing = true)
public class SwaggerSpecController {

    private final SwaggerSettingsRepository swaggerSettingsRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowVersionRepository workflowVersionRepository;

    @GetMapping("/api/swagger/spec")
    public Map<String, Object> getSpec() {
        SwaggerSettingsEntity settings = swaggerSettingsRepository.findFirstByOrderByCreatedAtAsc()
                .orElse(null);

        String title = settings != null && settings.getApiTitle() != null ? settings.getApiTitle() : "CWC API";
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

        // Batch-load latest published versions to resolve schemas from published state
        List<String> workflowIds = workflows.stream().map(WorkflowEntity::getId).toList();
        Map<String, WorkflowVersionEntity> publishedVersions = new LinkedHashMap<>();
        if (!workflowIds.isEmpty()) {
            workflowVersionRepository.findByWorkflowIdInAndPublishedTrueOrderByVersionNumberDesc(workflowIds)
                    .forEach(v -> publishedVersions.putIfAbsent(v.getWorkflowId(), v));
        }

        for (WorkflowEntity workflow : workflows) {
            // Use the latest published version's data when available;
            // fall back to current draft only if the workflow was never published.
            WorkflowVersionEntity published = publishedVersions.get(workflow.getId());

            Object nodesObj = published != null ? published.getNodes() : workflow.getNodes();
            if (!(nodesObj instanceof List<?> nodeList)) continue;

            Object effectiveInputSchema = published != null
                    ? published.getMcpInputSchema() : workflow.getMcpInputSchema();
            Object effectiveOutputSchema = published != null
                    ? published.getMcpOutputSchema() : workflow.getMcpOutputSchema();

            for (Object nodeObj : nodeList) {
                if (!(nodeObj instanceof Map<?, ?> node)) continue;
                if (!"webhook".equals(node.get("type"))) continue;

                Object paramsObj = node.get("parameters");
                Map<String, Object> parameters = paramsObj instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
                String webhookPath = (String) parameters.getOrDefault("path", "");
                if (webhookPath.isEmpty()) continue;

                String httpMethod = ((String) parameters.getOrDefault("httpMethod", "GET")).toLowerCase();
                String normalizedPath = webhookPath.startsWith("/") ? webhookPath : "/" + webhookPath;
                // Strip regex constraints from path params for OpenAPI (e.g. {id:[0-9]{1,10}} -> {id})
                String openApiPath = normalizedPath.replaceAll("\\{([^:}]+):(?:[^{}]|\\{[^}]*\\})+\\}", "{$1}");
                String pathKey = "/webhook" + openApiPath;

                String summary = workflow.getMcpDescription() != null ? workflow.getMcpDescription()
                        : workflow.getDescription() != null ? workflow.getDescription()
                        : workflow.getName();

                Map<String, Object> operation = new LinkedHashMap<>();
                operation.put("summary", summary);
                operation.put("operationId", workflow.getId() + "_" + node.get("id"));
                operation.put("tags", List.of("Workflows"));

                // Extract path parameter names from webhook URL template
                List<String> pathParamNames = io.cwc.util.SchemaUtils.extractPathParamNames(pathKey);

                // Build path parameter entries for OpenAPI
                List<Map<String, Object>> allParams = new ArrayList<>();
                for (String ppName : pathParamNames) {
                    Map<String, Object> param = new LinkedHashMap<>();
                    param.put("name", ppName);
                    param.put("in", "path");
                    param.put("required", true);
                    param.put("schema", Map.of("type", "string"));
                    allParams.add(param);
                }

                // Build full schema with auto-populated path params
                Map<String, Object> fullSchema = io.cwc.util.SchemaUtils.buildInputSchemaWithPathParams(
                        effectiveInputSchema, pathParamNames);
                Object propsObj = fullSchema.get("properties");
                List<String> requiredFields = fullSchema.get("required") instanceof List<?> r
                        ? r.stream().map(Object::toString).toList() : List.of();
                boolean schemaHasPayload = propsObj instanceof Map<?, ?> p && p.containsKey("payload");

                if (schemaHasPayload && propsObj instanceof Map<?, ?> props) {
                    // Convention mode: payload → requestBody, path matches → path params, rest → query
                    for (Map.Entry<?, ?> entry : props.entrySet()) {
                        String paramName = String.valueOf(entry.getKey());
                        if ("payload".equals(paramName)) {
                            // Generate requestBody from payload property
                            if ("post".equals(httpMethod) || "put".equals(httpMethod) || "patch".equals(httpMethod)) {
                                Map<String, Object> payloadSchema = entry.getValue() instanceof Map<?, ?> ps
                                        ? (Map<String, Object>) ps : Map.of("type", "object");
                                Map<String, Object> requestBody = new LinkedHashMap<>();
                                requestBody.put("required", requiredFields.contains("payload"));
                                requestBody.put("content", Map.of(
                                        "application/json", Map.of("schema", payloadSchema)));
                                operation.put("requestBody", requestBody);
                            }
                        } else if (pathParamNames.contains(paramName)) {
                            // Enrich existing path param with schema info (description, type)
                            for (Map<String, Object> pp : allParams) {
                                if (paramName.equals(pp.get("name"))) {
                                    if (entry.getValue() instanceof Map<?, ?> propDef) {
                                        Map<String, Object> paramSchema = new LinkedHashMap<>();
                                        paramSchema.put("type", propDef.get("type") != null ? propDef.get("type") : "string");
                                        pp.put("schema", paramSchema);
                                        if (propDef.get("description") instanceof String desc && !desc.isBlank()) {
                                            pp.put("description", desc);
                                        }
                                    }
                                    break;
                                }
                            }
                        } else {
                            // Query param
                            Map<String, Object> queryParam = new LinkedHashMap<>();
                            queryParam.put("name", paramName);
                            queryParam.put("in", "query");
                            queryParam.put("required", requiredFields.contains(paramName));
                            if (entry.getValue() instanceof Map<?, ?> propDef) {
                                Map<String, Object> paramSchema = new LinkedHashMap<>();
                                paramSchema.put("type", propDef.get("type") != null ? propDef.get("type") : "string");
                                if (propDef.get("enum") != null) paramSchema.put("enum", propDef.get("enum"));
                                if (propDef.get("default") != null) paramSchema.put("default", propDef.get("default"));
                                queryParam.put("schema", paramSchema);
                                if (propDef.get("description") instanceof String desc && !desc.isBlank()) {
                                    queryParam.put("description", desc);
                                }
                            } else {
                                queryParam.put("schema", Map.of("type", "string"));
                            }
                            allParams.add(queryParam);
                        }
                    }
                } else {
                    // Legacy mode: no payload property — use method-based split
                    if ("post".equals(httpMethod) || "put".equals(httpMethod) || "patch".equals(httpMethod)) {
                        Map<String, Object> requestSchema = buildInputSchema(effectiveInputSchema);
                        Map<String, Object> requestBody = new LinkedHashMap<>();
                        requestBody.put("required", true);
                        requestBody.put("content", Map.of(
                                "application/json", Map.of("schema", requestSchema)
                        ));
                        operation.put("requestBody", requestBody);
                    } else if (effectiveInputSchema != null) {
                        // For GET/DELETE, render input schema properties as query parameters
                        if (propsObj instanceof Map<?, ?> props) {
                            for (Map.Entry<?, ?> entry : props.entrySet()) {
                                String paramName = String.valueOf(entry.getKey());
                                // Skip path params already added above
                                if (pathParamNames.contains(paramName)) continue;
                                Map<String, Object> queryParam = new LinkedHashMap<>();
                                queryParam.put("name", paramName);
                                queryParam.put("in", "query");
                                queryParam.put("required", requiredFields.contains(paramName));
                                if (entry.getValue() instanceof Map<?, ?> propDef) {
                                    Map<String, Object> paramSchema = new LinkedHashMap<>();
                                    Object pType = propDef.get("type");
                                    paramSchema.put("type", pType != null ? pType : "string");
                                    if (propDef.get("enum") != null) paramSchema.put("enum", propDef.get("enum"));
                                    if (propDef.get("default") != null) paramSchema.put("default", propDef.get("default"));
                                    queryParam.put("schema", paramSchema);
                                    if (propDef.get("description") instanceof String desc && !desc.isBlank()) {
                                        queryParam.put("description", desc);
                                    }
                                } else {
                                    queryParam.put("schema", Map.of("type", "string"));
                                }
                                allParams.add(queryParam);
                            }
                        }
                    }
                }

                if (!allParams.isEmpty()) {
                    operation.put("parameters", allParams);
                }

                // Response from mcpOutputSchema
                Map<String, Object> responseSchema = buildOutputSchema(effectiveOutputSchema);
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

    private Map<String, Object> buildInputSchema(Object mcpInputSchema) {
        return io.cwc.util.SchemaUtils.buildInputSchema(mcpInputSchema);
    }

    private Map<String, Object> buildOutputSchema(Object mcpOutputSchema) {
        return io.cwc.util.SchemaUtils.buildOutputSchema(mcpOutputSchema);
    }
}
