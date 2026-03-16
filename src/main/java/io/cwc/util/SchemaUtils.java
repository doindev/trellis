package io.cwc.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared utility for building JSON Schema objects from MCP input/output schema definitions.
 * Supports both the legacy flat McpParameter[] format and the new direct JSON Schema format.
 */
public final class SchemaUtils {

    private SchemaUtils() {}

    /**
     * Build a JSON Schema object from the stored mcpInputSchema.
     * Handles three cases:
     * 1. Direct JSON Schema object (Map with "type" key) — passthrough
     * 2. Legacy flat parameter list (List of Maps with "name" keys) — convert
     * 3. null/empty — fallback to generic input
     */
    public static Map<String, Object> buildInputSchema(Object mcpInputSchema) {
        // Case 1: Direct JSON Schema object (from Code mode)
        if (mcpInputSchema instanceof Map<?, ?> schemaMap) {
            Object type = schemaMap.get("type");
            if ("object".equals(type)) {
                // It's already a valid JSON Schema — return as-is (deep copy for safety)
                return deepCopyMap(schemaMap);
            }
        }

        // Case 2: Legacy flat parameter list (from Visual mode or old data)
        if (mcpInputSchema instanceof List<?> paramList && !paramList.isEmpty()) {
            return buildSchemaFromParamList(paramList);
        }

        // Case 3: Fallback
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

    /**
     * Build a JSON Schema object from the stored mcpOutputSchema.
     * Supports both flat properties ({name, type, description}) and enhanced
     * McpParameter format (with nesting, constraints, array items, etc.).
     */
    public static Map<String, Object> buildOutputSchema(Object mcpOutputSchema) {
        if (!(mcpOutputSchema instanceof Map<?, ?> schemaMap)) return null;
        String format = (String) schemaMap.get("format");
        if (!"json".equals(format)) return null;

        List<?> properties = (List<?>) schemaMap.get("properties");
        if (properties == null || properties.isEmpty()) return null;

        // Reuse buildSchemaFromParamList which handles both flat and enhanced formats
        Map<String, Object> schema = buildSchemaFromParamList(properties);

        String schemaDescription = (String) schemaMap.get("description");
        if (schemaDescription != null && !schemaDescription.isBlank()) {
            schema.put("description", schemaDescription);
        }

        // Verify we actually got properties
        Object schemaProps = schema.get("properties");
        if (schemaProps instanceof Map<?, ?> propsMap && propsMap.isEmpty()) return null;

        return schema;
    }

    private static Map<String, Object> buildSchemaFromParamList(List<?> paramList) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Object item : paramList) {
            if (item instanceof Map<?, ?> param) {
                String name = (String) param.get("name");
                String type = (String) param.get("type");
                String description = (String) param.get("description");
                Boolean isRequired = (Boolean) param.get("required");
                if (name == null || name.isBlank()) continue;

                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put("type", type != null ? type : "string");
                if (description != null && !description.isBlank()) {
                    prop.put("description", description);
                }

                // Handle nested properties (extended McpParameter format)
                if ("object".equals(type) && param.get("properties") instanceof List<?> nestedProps && !nestedProps.isEmpty()) {
                    Map<String, Object> nested = buildSchemaFromParamList(nestedProps);
                    prop.put("properties", nested.get("properties"));
                    if (nested.containsKey("required")) {
                        prop.put("required", nested.get("required"));
                    }
                }

                // Handle array items type
                if ("array".equals(type) && param.get("items") instanceof Map<?, ?> itemsMap) {
                    Map<String, Object> items = new LinkedHashMap<>();
                    items.put("type", itemsMap.get("type") != null ? itemsMap.get("type") : "string");
                    prop.put("items", items);
                }

                // Handle constraints
                copyIfPresent(param, prop, "enum");
                copyNumericIfPresent(param, prop, "minimum");
                copyNumericIfPresent(param, prop, "maximum");
                copyNumericIfPresent(param, prop, "minLength");
                copyNumericIfPresent(param, prop, "maxLength");
                copyStringIfPresent(param, prop, "pattern");
                if (param.get("default") != null) {
                    prop.put("default", param.get("default"));
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

    private static void copyIfPresent(Map<?, ?> src, Map<String, Object> dest, String key) {
        Object val = src.get(key);
        if (val != null) {
            dest.put(key, val);
        }
    }

    private static void copyNumericIfPresent(Map<?, ?> src, Map<String, Object> dest, String key) {
        Object val = src.get(key);
        if (val instanceof Number) {
            dest.put(key, val);
        }
    }

    private static void copyStringIfPresent(Map<?, ?> src, Map<String, Object> dest, String key) {
        Object val = src.get(key);
        if (val instanceof String s && !s.isBlank()) {
            dest.put(key, s);
        }
    }

    /**
     * Extract path parameter names from a webhook path string.
     * Parses {paramName} or {paramName:regex} patterns.
     */
    public static List<String> extractPathParamNames(String webhookPath) {
        if (webhookPath == null || webhookPath.isEmpty()) return List.of();
        List<String> names = new ArrayList<>();
        Matcher m = Pattern.compile("\\{([^:}]+)(?::(?:[^{}]|\\{[^}]*\\})+)?\\}").matcher(webhookPath);
        while (m.find()) names.add(m.group(1));
        return names;
    }

    /**
     * Build an input schema that auto-populates any missing path parameters.
     * Path params found in the webhook URL but not in the schema are added as
     * required string properties.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> buildInputSchemaWithPathParams(
            Object mcpInputSchema, List<String> pathParamNames) {
        Map<String, Object> schema = buildInputSchema(mcpInputSchema);
        if (pathParamNames == null || pathParamNames.isEmpty()) return schema;

        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        if (props == null) {
            props = new LinkedHashMap<>();
            schema.put("properties", props);
        }
        List<String> required = schema.get("required") instanceof List<?> r
                ? new ArrayList<>(r.stream().map(Object::toString).toList())
                : new ArrayList<>();

        for (String pp : pathParamNames) {
            if (!props.containsKey(pp)) {
                props.put(pp, Map.of("type", "string", "description", "Path parameter: " + pp));
                if (!required.contains(pp)) required.add(pp);
            }
        }
        if (!required.isEmpty()) schema.put("required", required);
        return schema;
    }

    /**
     * Check whether the raw mcpInputSchema defines a "payload" property.
     * Supports both direct JSON Schema (Map) and legacy param list (List) formats.
     */
    public static boolean hasPayloadProperty(Object mcpInputSchema) {
        if (mcpInputSchema instanceof Map<?, ?> schemaMap) {
            Object props = schemaMap.get("properties");
            if (props instanceof Map<?, ?> propsMap) {
                return propsMap.containsKey("payload");
            }
        }
        if (mcpInputSchema instanceof List<?> paramList) {
            for (Object item : paramList) {
                if (item instanceof Map<?, ?> param && "payload".equals(param.get("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Map<String, Object> deepCopyMap(Map<?, ?> original) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : original.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> mapVal) {
                value = deepCopyMap(mapVal);
            } else if (value instanceof List<?> listVal) {
                value = new ArrayList<>(listVal);
            }
            copy.put(String.valueOf(entry.getKey()), value);
        }
        return copy;
    }
}
