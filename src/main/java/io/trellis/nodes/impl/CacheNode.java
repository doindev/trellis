package io.trellis.nodes.impl;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import io.trellis.service.CacheRegistryService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Node(
    type = "cache",
    displayName = "Cache",
    description = "Cache data for fast retrieval. Lookup returns cached items on hit, passes through on miss.",
    category = "Flow",
    icon = "layers"
)
public class CacheNode extends AbstractNode {

    @Autowired
    private CacheRegistryService cacheRegistryService;

    @Override
    public List<NodeInput> getInputs() {
        return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
    }

    @Override
    public List<NodeOutput> getOutputs() {
        return List.of(
            NodeOutput.builder().name("hit").displayName("Hit").build(),
            NodeOutput.builder().name("miss").displayName("Miss").build()
        );
    }

    @Override
    public List<NodeParameter> getParameters() {
        return List.of(
            NodeParameter.builder()
                .name("operation")
                .displayName("Operation")
                .description("What to do with the cache.")
                .type(ParameterType.OPTIONS)
                .defaultValue("lookup")
                .required(true)
                .options(List.of(
                    ParameterOption.builder().name("Lookup").value("lookup")
                        .description("Look up items in cache. Hits go to output 1, misses to output 2.").build(),
                    ParameterOption.builder().name("Set").value("set")
                        .description("Store items in the cache.").build(),
                    ParameterOption.builder().name("Invalidate").value("invalidate")
                        .description("Remove items from the cache.").build()
                ))
                .build(),

            NodeParameter.builder()
                .name("cacheSource")
                .displayName("Cache Source")
                .description("Use a pre-defined cache or configure one inline.")
                .type(ParameterType.OPTIONS)
                .defaultValue("select")
                .options(List.of(
                    ParameterOption.builder().name("Select Defined Cache").value("select").build(),
                    ParameterOption.builder().name("Inline Configuration").value("inline").build()
                ))
                .build(),

            NodeParameter.builder()
                .name("cacheName")
                .displayName("Cache Name")
                .description("Name of the cache to use.")
                .type(ParameterType.STRING)
                .required(true)
                .build(),

            NodeParameter.builder()
                .name("maxSize")
                .displayName("Max Entries")
                .description("Maximum number of entries in the cache.")
                .type(ParameterType.NUMBER)
                .defaultValue(1000)
                .displayOptions(Map.of("show", Map.of("cacheSource", List.of("inline"))))
                .build(),

            NodeParameter.builder()
                .name("ttlSeconds")
                .displayName("TTL (seconds)")
                .description("Time-to-live for cache entries in seconds.")
                .type(ParameterType.NUMBER)
                .defaultValue(3600)
                .displayOptions(Map.of("show", Map.of("cacheSource", List.of("inline"))))
                .build(),

            NodeParameter.builder()
                .name("key")
                .displayName("Key")
                .description("The cache key. Use a field path (e.g. 'userId') to extract per item, or an expression (e.g. {{ $json.userId }}).")
                .type(ParameterType.STRING)
                .required(true)
                .build(),

            NodeParameter.builder()
                .name("value")
                .displayName("Value")
                .description("The value to store. Use a field path (e.g. 'details') to extract from input, or an expression. Leave blank to store the entire item.")
                .type(ParameterType.STRING)
                .displayOptions(Map.of("show", Map.of("operation", List.of("set"))))
                .build(),

            NodeParameter.builder()
                .name("outputPath")
                .displayName("Output Path")
                .description("Path where the cached value will be inserted in the output item (e.g. 'cachedData'). Leave blank to merge the cached object into the root of the input.")
                .type(ParameterType.STRING)
                .displayOptions(Map.of("show", Map.of("operation", List.of("lookup"))))
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult execute(NodeExecutionContext context) {
        List<Map<String, Object>> inputData = context.getInputData();
        if (inputData == null || inputData.isEmpty()) {
            return NodeExecutionResult.successMultiOutput(List.of(List.of(), List.of()));
        }

        String operation = context.getParameter("operation", "lookup");
        String cacheSource = context.getParameter("cacheSource", "select");

        log.info("Cache node executing: operation={}, cacheSource={}", operation, cacheSource);

        // Resolve cache name and ensure runtime cache exists
        String cacheName = context.getParameter("cacheName", "");
        if (cacheName.isBlank()) {
            return NodeExecutionResult.error("Cache name is required");
        }

        if ("inline".equals(cacheSource)) {
            if (cacheRegistryService.isDefinedCacheName(cacheName)) {
                return NodeExecutionResult.error("Inline cache name '" + cacheName + "' conflicts with a defined cache. Use 'Select Defined Cache' instead.");
            }
            Number maxSizeNum = context.getParameter("maxSize", 1000);
            Number ttlNum = context.getParameter("ttlSeconds", 3600);
            int maxSize = maxSizeNum.intValue();
            long ttlSeconds = ttlNum.longValue();
            cacheRegistryService.getOrCreateCache(cacheName, maxSize, ttlSeconds);
        } else {
            cacheRegistryService.getOrCreateFromDefinition(cacheName);
        }
        log.info("Cache node using cache: name={}, items={}", cacheName, inputData.size());

        // Resolve parameters shared across all items
        Object keyParam = context.getParameter("key", "");
        if (keyParam == null || (keyParam instanceof String s && s.isBlank())) {
            return NodeExecutionResult.error("Cache key is required");
        }
        String outputPath = "lookup".equals(operation) ? context.getParameter("outputPath", "") : "";
        Object valueParam = "set".equals(operation) ? context.getParameter("value", null) : null;

        List<Map<String, Object>> hitItems = new ArrayList<>();
        List<Map<String, Object>> missItems = new ArrayList<>();

        for (Map<String, Object> item : inputData) {
            Map<String, Object> json = (Map<String, Object>) item.getOrDefault("json", item);

            // Resolve key per-item: tries as field path first, falls back to literal
            String key = resolveKeyForItem(json, keyParam);
            if (key == null || key.isEmpty()) {
                log.warn("Cache node [{}]: could not resolve key for item, treating as miss", cacheName);
                missItems.add(item);
                continue;
            }

            switch (operation) {
                case "lookup" -> {
                    Optional<Map<String, Object>> cached = cacheRegistryService.lookup(cacheName, key);
                    if (cached.isPresent()) {
                        log.debug("Cache HIT: cache={}, key={}", cacheName, key);
                        // Always pass input through, merging cached data into it
                        Map<String, Object> output = deepClone(json);
                        if (outputPath.isBlank()) {
                            output.putAll(cached.get());
                        } else {
                            setNestedValue(output, outputPath, cached.get());
                        }
                        hitItems.add(wrapInJson(output));
                    } else {
                        log.debug("Cache MISS: cache={}, key={}", cacheName, key);
                        missItems.add(item);
                    }
                }
                case "set" -> {
                    log.debug("Cache STORE: cache={}, key={}", cacheName, key);
                    Map<String, Object> valueToStore = resolveValueForItem(json, valueParam, cacheName);
                    cacheRegistryService.store(cacheName, key, valueToStore);
                    hitItems.add(item); // pass input through
                }
                case "invalidate" -> {
                    cacheRegistryService.invalidate(cacheName, key);
                    hitItems.add(item);
                }
                default -> {
                    return NodeExecutionResult.error("Unknown cache operation: " + operation);
                }
            }
        }

        log.debug("Cache node [{}]: operation={}, hits={}, misses={}", cacheName, operation, hitItems.size(), missItems.size());
        return NodeExecutionResult.successMultiOutput(List.of(hitItems, missItems));
    }

    /**
     * Resolve the cache key for a single item.
     * If keyParam is a string, try it as a field path in the item first, then use as literal.
     * If keyParam is already a non-string (expression resolved to object), toString it.
     */
    private String resolveKeyForItem(Map<String, Object> json, Object keyParam) {
        if (keyParam instanceof String keyStr) {
            Object extracted = getNestedValue(json, keyStr);
            if (extracted != null) {
                return String.valueOf(extracted);
            }
            // Not a field path — use as literal key value (e.g. expression-resolved string)
            return keyStr;
        }
        return String.valueOf(keyParam);
    }

    /**
     * Resolve the value to store for a single item.
     * Blank/null = store entire json. String = try as field path. Map = store directly. Other = wrap.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveValueForItem(Map<String, Object> json, Object valueParam, String cacheName) {
        // Blank or null — store entire item
        if (valueParam == null || (valueParam instanceof String s && s.isBlank())) {
            return json;
        }

        // Already a Map (expression resolved to an object)
        if (valueParam instanceof Map) {
            return (Map<String, Object>) valueParam;
        }

        // String — try as field path, fall back to wrapping the literal
        if (valueParam instanceof String valuePath) {
            Object extracted = getNestedValue(json, valuePath);
            if (extracted instanceof Map) {
                return (Map<String, Object>) extracted;
            } else if (extracted != null) {
                return Map.of("value", extracted);
            }
            // Field path didn't resolve — store the literal string
            log.warn("Cache node [{}]: value '{}' did not resolve to a field, storing as literal", cacheName, valuePath);
            return Map.of("value", valueParam);
        }

        // Non-string primitive (number, boolean, etc.)
        return Map.of("value", valueParam);
    }
}
