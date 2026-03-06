package io.cwc.nodes.impl;

import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;

import java.util.*;

/**
 * Helper utilities for building NodeExecutionContext instances and
 * input data in tests.
 */
public final class NodeTestHelper {

    private NodeTestHelper() {}

    // ── Context builders ──

    public static NodeExecutionContext.NodeExecutionContextBuilder contextBuilder() {
        return NodeExecutionContext.builder()
                .executionId("exec-test")
                .workflowId("wf-test")
                .nodeId("node-test")
                .executionMode(NodeExecutionContext.ExecutionMode.MANUAL);
    }

    public static NodeExecutionContext ctx(List<Map<String, Object>> input, Map<String, Object> params) {
        return contextBuilder()
                .inputData(input)
                .parameters(params)
                .build();
    }

    public static NodeExecutionContext ctx(List<Map<String, Object>> input) {
        return ctx(input, Map.of());
    }

    public static NodeExecutionContext ctxEmpty() {
        return ctx(List.of(), Map.of());
    }

    // ── Item builders ──

    /** Wraps a map in the standard { "json": data } format. */
    public static Map<String, Object> item(Map<String, Object> json) {
        Map<String, Object> item = new HashMap<>();
        item.put("json", new HashMap<>(json));
        return item;
    }

    /** Convenience: creates a single-field item. */
    public static Map<String, Object> item(String key, Object value) {
        return item(Map.of(key, value));
    }

    /** Creates multiple items from varargs maps. */
    @SafeVarargs
    public static List<Map<String, Object>> items(Map<String, Object>... jsonMaps) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> m : jsonMaps) {
            list.add(item(m));
        }
        return list;
    }

    /** Creates a mutable map (unlike Map.of which is immutable). */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> mutableMap(Object... kvPairs) {
        Map<K, V> map = new HashMap<>();
        for (int i = 0; i < kvPairs.length; i += 2) {
            map.put((K) kvPairs[i], (V) kvPairs[i + 1]);
        }
        return map;
    }

    // ── Result inspection ──

    /** Gets items from the first (or only) output of a result. */
    public static List<Map<String, Object>> output(NodeExecutionResult result) {
        return result.getOutput() != null && !result.getOutput().isEmpty()
                ? result.getOutput().get(0)
                : List.of();
    }

    /** Gets items from a specific output index. */
    public static List<Map<String, Object>> output(NodeExecutionResult result, int index) {
        return result.getOutput() != null && result.getOutput().size() > index
                ? result.getOutput().get(index)
                : List.of();
    }

    /** Extracts the "json" data from the first item of the first output. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> firstJson(NodeExecutionResult result) {
        List<Map<String, Object>> items = output(result);
        if (items.isEmpty()) return Map.of();
        Object json = items.get(0).get("json");
        return json instanceof Map ? (Map<String, Object>) json : Map.of();
    }

    /** Extracts the "json" data from the Nth item of the first output. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> jsonAt(NodeExecutionResult result, int itemIndex) {
        List<Map<String, Object>> items = output(result);
        if (items.size() <= itemIndex) return Map.of();
        Object json = items.get(itemIndex).get("json");
        return json instanceof Map ? (Map<String, Object>) json : Map.of();
    }

    /** Extracts the "json" data from the Nth item of a specific output. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> jsonAt(NodeExecutionResult result, int outputIndex, int itemIndex) {
        List<Map<String, Object>> items = output(result, outputIndex);
        if (items.size() <= itemIndex) return Map.of();
        Object json = items.get(itemIndex).get("json");
        return json instanceof Map ? (Map<String, Object>) json : Map.of();
    }

    /** Builds a condition map for If/Filter/Switch nodes. */
    public static Map<String, Object> condition(String value1, String operation, String value2) {
        return mutableMap("value1", value1, "operation", operation, "value2", value2);
    }

    /** Builds a condition map for If/Filter/Switch nodes with dataType. */
    public static Map<String, Object> condition(String value1, String dataType, String operation, String value2) {
        return mutableMap("value1", value1, "dataType", dataType, "operation", operation, "value2", value2);
    }
}
