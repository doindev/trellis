package io.cwc.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnClass(name = "dev.langchain4j.model.chat.ChatModel")
@ConditionalOnProperty(name = "cwc.features.langchain4j.enabled", havingValue = "true", matchIfMissing = true)
public class ChatToolProvider {

    private final McpSystemToolService mcpToolService;
    private final ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    public Map<ToolSpecification, ToolExecutor> getTools() {
        Map<ToolSpecification, ToolExecutor> tools = new LinkedHashMap<>();
        List<Map<String, Object>> defs = mcpToolService.getSystemToolDefinitions();

        for (Map<String, Object> def : defs) {
            String name = (String) def.get("name");
            String description = (String) def.get("description");
            Map<String, Object> inputSchema = (Map<String, Object>) def.get("inputSchema");

            ToolSpecification spec = buildToolSpec(name, description, inputSchema);
            ToolExecutor executor = (request, memoryId) -> {
                try {
                    Map<String, Object> args = objectMapper.readValue(
                            request.arguments(), new TypeReference<>() {});
                    // LLM may send array/object values as JSON strings since the schema
                    // maps them to string types — deserialize them back to native types
                    deserializeJsonStrings(args);
                    return mcpToolService.executeTool(name, args);
                } catch (Exception e) {
                    return "{\"error\": \"" + e.getMessage() + "\"}";
                }
            };
            tools.put(spec, executor);
        }
        return tools;
    }

    /**
     * Attempt to parse any String values that look like JSON arrays or objects
     * back into their native List/Map types. This handles the mismatch between
     * the schema (which maps array/object to string) and what the tool handlers expect.
     */
    private void deserializeJsonStrings(Map<String, Object> args) {
        for (var entry : args.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String str) {
                String trimmed = str.trim();
                if ((trimmed.startsWith("[") && trimmed.endsWith("]"))
                        || (trimmed.startsWith("{") && trimmed.endsWith("}"))) {
                    try {
                        entry.setValue(objectMapper.readValue(trimmed, Object.class));
                    } catch (Exception ignored) {
                        // Not valid JSON — keep as string
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private ToolSpecification buildToolSpec(String name, String description,
                                            Map<String, Object> inputSchema) {
        var schemaBuilder = JsonObjectSchema.builder();
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        List<String> required = (List<String>) inputSchema.getOrDefault("required", List.of());

        if (properties != null) {
            for (var entry : properties.entrySet()) {
                String propName = entry.getKey();
                Map<String, Object> propDef = (Map<String, Object>) entry.getValue();
                String type = (String) propDef.getOrDefault("type", "string");
                String desc = (String) propDef.getOrDefault("description", "");

                switch (type) {
                    case "integer" -> schemaBuilder.addIntegerProperty(propName, desc);
                    case "boolean" -> schemaBuilder.addBooleanProperty(propName, desc);
                    case "number" -> schemaBuilder.addNumberProperty(propName, desc);
                    case "array" -> schemaBuilder.addStringProperty(propName, desc + " (JSON array)");
                    case "object" -> schemaBuilder.addStringProperty(propName, desc + " (JSON object)");
                    default -> schemaBuilder.addStringProperty(propName, desc);
                }
            }
        }
        if (!required.isEmpty()) {
            schemaBuilder.required(required);
        }

        return ToolSpecification.builder()
                .name(name)
                .description(description)
                .parameters(schemaBuilder.build())
                .build();
    }
}
