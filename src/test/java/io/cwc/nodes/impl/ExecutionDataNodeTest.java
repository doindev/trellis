package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;

class ExecutionDataNodeTest {

    private ExecutionDataNode node;

    @BeforeEach
    void setUp() {
        node = new ExecutionDataNode();
    }

    // ── Save key-value annotation ──

    @Test
    void savesKeyValueToNodeContextData() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "save",
                "dataToSave", List.of(
                        mutableMap("key", "myKey", "value", "myValue")
                )
        );

        Map<String, Object> nodeContextData = new HashMap<>();
        NodeExecutionContext context = contextBuilder()
                .inputData(input)
                .parameters(params)
                .nodeContextData(nodeContextData)
                .build();

        NodeExecutionResult result = node.execute(context);

        assertThat(output(result)).hasSize(1);
        assertThat(nodeContextData).containsKey("customData");
        @SuppressWarnings("unchecked")
        Map<String, String> customData = (Map<String, String>) nodeContextData.get("customData");
        assertThat(customData).containsEntry("myKey", "myValue");
    }

    @Test
    void savesMultipleKeyValuePairs() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "save",
                "dataToSave", List.of(
                        mutableMap("key", "key1", "value", "val1"),
                        mutableMap("key", "key2", "value", "val2")
                )
        );

        Map<String, Object> nodeContextData = new HashMap<>();
        NodeExecutionContext context = contextBuilder()
                .inputData(input)
                .parameters(params)
                .nodeContextData(nodeContextData)
                .build();

        NodeExecutionResult result = node.execute(context);

        assertThat(output(result)).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, String> customData = (Map<String, String>) nodeContextData.get("customData");
        assertThat(customData).hasSize(2);
        assertThat(customData).containsEntry("key1", "val1");
        assertThat(customData).containsEntry("key2", "val2");
    }

    // ── Data passes through unchanged ──

    @Test
    void inputDataPassesThroughUnchanged() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice", "age", 30),
                Map.of("name", "Bob", "age", 25)
        );
        Map<String, Object> params = mutableMap(
                "operation", "save",
                "dataToSave", List.of(
                        mutableMap("key", "k", "value", "v")
                )
        );

        Map<String, Object> nodeContextData = new HashMap<>();
        NodeExecutionContext context = contextBuilder()
                .inputData(input)
                .parameters(params)
                .nodeContextData(nodeContextData)
                .build();

        NodeExecutionResult result = node.execute(context);

        assertThat(output(result)).hasSize(2);
        assertThat(jsonAt(result, 0)).containsEntry("name", "Alice");
        assertThat(jsonAt(result, 1)).containsEntry("name", "Bob");
    }

    // ── Key/value truncation ──

    @Test
    void longKeyIsTruncatedTo50Characters() {
        String longKey = "a".repeat(100);
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "save",
                "dataToSave", List.of(
                        mutableMap("key", longKey, "value", "val")
                )
        );

        Map<String, Object> nodeContextData = new HashMap<>();
        NodeExecutionContext context = contextBuilder()
                .inputData(input)
                .parameters(params)
                .nodeContextData(nodeContextData)
                .build();

        NodeExecutionResult result = node.execute(context);

        @SuppressWarnings("unchecked")
        Map<String, String> customData = (Map<String, String>) nodeContextData.get("customData");
        // Key should be truncated to 50 chars
        assertThat(customData.keySet().iterator().next()).hasSize(50);
        // Should include a hint
        assertThat(result.getHints()).isNotNull();
        assertThat(result.getHints()).anyMatch(h -> h.contains("keys were truncated"));
    }

    @Test
    void longValueIsTruncatedTo512Characters() {
        String longValue = "b".repeat(1000);
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "save",
                "dataToSave", List.of(
                        mutableMap("key", "myKey", "value", longValue)
                )
        );

        Map<String, Object> nodeContextData = new HashMap<>();
        NodeExecutionContext context = contextBuilder()
                .inputData(input)
                .parameters(params)
                .nodeContextData(nodeContextData)
                .build();

        NodeExecutionResult result = node.execute(context);

        @SuppressWarnings("unchecked")
        Map<String, String> customData = (Map<String, String>) nodeContextData.get("customData");
        assertThat(customData.get("myKey")).hasSize(512);
        assertThat(result.getHints()).isNotNull();
        assertThat(result.getHints()).anyMatch(h -> h.contains("values were truncated"));
    }

    @Test
    void shortKeyAndValueAreNotTruncated() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "save",
                "dataToSave", List.of(
                        mutableMap("key", "shortKey", "value", "shortValue")
                )
        );

        Map<String, Object> nodeContextData = new HashMap<>();
        NodeExecutionContext context = contextBuilder()
                .inputData(input)
                .parameters(params)
                .nodeContextData(nodeContextData)
                .build();

        NodeExecutionResult result = node.execute(context);

        @SuppressWarnings("unchecked")
        Map<String, String> customData = (Map<String, String>) nodeContextData.get("customData");
        assertThat(customData).containsEntry("shortKey", "shortValue");
        assertThat(result.getHints()).isNull();
    }

    // ── Empty / no input ──

    @Test
    void emptyInputProducesEmptyOutput() {
        Map<String, Object> params = mutableMap(
                "operation", "save",
                "dataToSave", List.of(
                        mutableMap("key", "k", "value", "v")
                )
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).isEmpty();
    }

    @Test
    void nullInputProducesEmptyOutput() {
        Map<String, Object> params = mutableMap(
                "operation", "save",
                "dataToSave", List.of(
                        mutableMap("key", "k", "value", "v")
                )
        );

        NodeExecutionResult result = node.execute(ctx(null, params));

        assertThat(output(result)).isEmpty();
    }

    // ── dataToSave as map-wrapped values ──

    @Test
    void dataToSaveAsMapWrappedValues() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "save",
                "dataToSave", mutableMap(
                        "values", List.of(
                                mutableMap("key", "wrapped", "value", "data")
                        )
                )
        );

        Map<String, Object> nodeContextData = new HashMap<>();
        NodeExecutionContext context = contextBuilder()
                .inputData(input)
                .parameters(params)
                .nodeContextData(nodeContextData)
                .build();

        NodeExecutionResult result = node.execute(context);

        assertThat(output(result)).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, String> customData = (Map<String, String>) nodeContextData.get("customData");
        assertThat(customData).containsEntry("wrapped", "data");
    }

    @Test
    void nonSaveOperationPassesThroughWithoutSaving() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "unknown"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("x", 1);
    }
}
