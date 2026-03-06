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

class ThrottleNodeTest {

    private ThrottleNode node;

    @BeforeEach
    void setUp() {
        node = new ThrottleNode();
    }

    // -- Helper to build context with workflow static data --

    private NodeExecutionContext ctxWithStaticData(
            List<Map<String, Object>> input, Map<String, Object> params,
            Map<String, Object> workflowStaticData) {
        return contextBuilder()
                .inputData(input)
                .parameters(params)
                .workflowStaticData(workflowStaticData)
                .build();
    }

    // -- Batch size release threshold --

    @Test
    void releasesWhenBatchSizeIsReached() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1),
                Map.of("id", 2),
                Map.of("id", 3)
        );
        Map<String, Object> params = mutableMap(
                "mode", "batchSize",
                "batchSize", 3,
                "flushOnEmpty", true
        );
        Map<String, Object> staticData = new HashMap<>();

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params, staticData));

        // Batch size reached: items are released on output 0
        List<Map<String, Object>> released = output(result, 0);
        assertThat(released).hasSize(3);
        assertThat(jsonAt(result, 0, 0)).containsEntry("id", 1);
        assertThat(jsonAt(result, 0, 1)).containsEntry("id", 2);
        assertThat(jsonAt(result, 0, 2)).containsEntry("id", 3);
    }

    @Test
    void releasesWhenBatchSizeExceeded() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1),
                Map.of("id", 2),
                Map.of("id", 3),
                Map.of("id", 4),
                Map.of("id", 5)
        );
        Map<String, Object> params = mutableMap(
                "mode", "batchSize",
                "batchSize", 3,
                "flushOnEmpty", true
        );
        Map<String, Object> staticData = new HashMap<>();

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params, staticData));

        // More than batchSize items: all are released
        List<Map<String, Object>> released = output(result, 0);
        assertThat(released).hasSize(5);
    }

    // -- Buffer items below threshold --

    @Test
    void buffersWhenBelowBatchSize() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1),
                Map.of("id", 2)
        );
        Map<String, Object> params = mutableMap(
                "mode", "batchSize",
                "batchSize", 5,
                "flushOnEmpty", true
        );
        Map<String, Object> staticData = new HashMap<>();

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params, staticData));

        // Below batch size: items are buffered, not released
        List<Map<String, Object>> released = output(result, 0);
        assertThat(released).isEmpty();

        // Output 1 (buffered) has a status item
        List<Map<String, Object>> buffered = output(result, 1);
        assertThat(buffered).hasSize(1);
        assertThat(jsonAt(result, 1, 0)).containsEntry("bufferedCount", 2);
        assertThat(jsonAt(result, 1, 0)).containsEntry("mode", "batchSize");
    }

    // -- Accumulation across executions via static data --

    @Test
    void accumulatesAcrossExecutions() {
        Map<String, Object> params = mutableMap(
                "mode", "batchSize",
                "batchSize", 4,
                "flushOnEmpty", true
        );
        Map<String, Object> staticData = new HashMap<>();

        // First execution: 2 items (below threshold)
        List<Map<String, Object>> input1 = items(Map.of("id", 1), Map.of("id", 2));
        NodeExecutionResult result1 = node.execute(ctxWithStaticData(input1, params, staticData));
        assertThat(output(result1, 0)).isEmpty(); // buffered

        // Second execution: 2 more items (now 4 total, equals threshold)
        List<Map<String, Object>> input2 = items(Map.of("id", 3), Map.of("id", 4));
        NodeExecutionResult result2 = node.execute(ctxWithStaticData(input2, params, staticData));
        assertThat(output(result2, 0)).hasSize(4); // released
    }

    // -- Flush on empty input --

    @Test
    void flushesOnEmptyInputWhenEnabled() {
        Map<String, Object> params = mutableMap(
                "mode", "batchSize",
                "batchSize", 10,
                "flushOnEmpty", true
        );
        Map<String, Object> staticData = new HashMap<>();

        // First: buffer some items
        List<Map<String, Object>> input1 = items(Map.of("id", 1), Map.of("id", 2));
        node.execute(ctxWithStaticData(input1, params, staticData));

        // Second: empty input triggers flush
        NodeExecutionResult result2 = node.execute(ctxWithStaticData(List.of(), params, staticData));

        List<Map<String, Object>> released = output(result2, 0);
        assertThat(released).hasSize(2);
    }

    @Test
    void doesNotFlushOnEmptyInputWhenDisabled() {
        Map<String, Object> params = mutableMap(
                "mode", "batchSize",
                "batchSize", 10,
                "flushOnEmpty", false
        );
        Map<String, Object> staticData = new HashMap<>();

        // First: buffer some items
        List<Map<String, Object>> input1 = items(Map.of("id", 1));
        node.execute(ctxWithStaticData(input1, params, staticData));

        // Second: empty input does NOT trigger flush
        NodeExecutionResult result2 = node.execute(ctxWithStaticData(List.of(), params, staticData));

        assertThat(output(result2, 0)).isEmpty();
        assertThat(output(result2, 1)).isEmpty();
    }

    // -- Empty input with empty buffer --

    @Test
    void emptyInputWithEmptyBufferProducesEmptyOutputs() {
        Map<String, Object> params = mutableMap(
                "mode", "batchSize",
                "batchSize", 5,
                "flushOnEmpty", true
        );
        Map<String, Object> staticData = new HashMap<>();

        NodeExecutionResult result = node.execute(ctxWithStaticData(List.of(), params, staticData));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).isEmpty();
    }

    // -- Null input --

    @Test
    void nullInputWithEmptyBufferProducesEmptyOutputs() {
        Map<String, Object> params = mutableMap(
                "mode", "batchSize",
                "batchSize", 5,
                "flushOnEmpty", true
        );
        Map<String, Object> staticData = new HashMap<>();

        NodeExecutionResult result = node.execute(ctxWithStaticData(null, params, staticData));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).isEmpty();
    }

    // -- Time window mode (immediate release since elapsed >= 0 when windowSeconds is 0) --

    @Test
    void timeWindowModeReleasesWhenTimeExpires() {
        List<Map<String, Object>> input = items(Map.of("id", 1));
        Map<String, Object> params = mutableMap(
                "mode", "timeWindow",
                "windowSeconds", 0, // 0 seconds means immediate release
                "flushOnEmpty", true
        );
        Map<String, Object> staticData = new HashMap<>();

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params, staticData));

        assertThat(output(result, 0)).hasSize(1);
    }

    @Test
    void timeWindowModeBuffersWhenTimeNotExpired() {
        List<Map<String, Object>> input = items(Map.of("id", 1));
        Map<String, Object> params = mutableMap(
                "mode", "timeWindow",
                "windowSeconds", 9999, // very long window, won't expire
                "flushOnEmpty", true
        );
        Map<String, Object> staticData = new HashMap<>();

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params, staticData));

        // Time window not expired: items are buffered
        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).hasSize(1);
    }

    // -- Both mode --

    @Test
    void bothModeReleasesWhenBatchSizeReached() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1), Map.of("id", 2), Map.of("id", 3)
        );
        Map<String, Object> params = mutableMap(
                "mode", "both",
                "batchSize", 3,
                "windowSeconds", 9999,
                "flushOnEmpty", true
        );
        Map<String, Object> staticData = new HashMap<>();

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params, staticData));

        // Batch size met, so release
        assertThat(output(result, 0)).hasSize(3);
    }

    @Test
    void bothModeReleasesWhenTimeWindowExpires() {
        List<Map<String, Object>> input = items(Map.of("id", 1));
        Map<String, Object> params = mutableMap(
                "mode", "both",
                "batchSize", 100,
                "windowSeconds", 0, // instant expiry
                "flushOnEmpty", true
        );
        Map<String, Object> staticData = new HashMap<>();

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params, staticData));

        // Time window expired, so release despite being below batch size
        assertThat(output(result, 0)).hasSize(1);
    }

    // -- Buffer accumulates across multiple calls with reused staticData --

    @Test
    void bufferAccumulatesAcrossMultipleCalls() {
        Map<String, Object> params = mutableMap(
                "mode", "batchSize",
                "batchSize", 6,
                "flushOnEmpty", true
        );
        Map<String, Object> staticData = new HashMap<>();

        // Call 1: 2 items
        List<Map<String, Object>> input1 = items(Map.of("id", 1), Map.of("id", 2));
        NodeExecutionResult result1 = node.execute(ctxWithStaticData(input1, params, staticData));
        assertThat(output(result1, 0)).isEmpty();

        // Call 2: 2 more items (total 4, still below 6)
        List<Map<String, Object>> input2 = items(Map.of("id", 3), Map.of("id", 4));
        NodeExecutionResult result2 = node.execute(ctxWithStaticData(input2, params, staticData));
        assertThat(output(result2, 0)).isEmpty();

        // Call 3: 2 more items (total 6, meets threshold)
        List<Map<String, Object>> input3 = items(Map.of("id", 5), Map.of("id", 6));
        NodeExecutionResult result3 = node.execute(ctxWithStaticData(input3, params, staticData));
        assertThat(output(result3, 0)).hasSize(6);
    }

    // -- Has correct inputs and outputs --

    @Test
    void hasOneInput() {
        assertThat(node.getInputs()).hasSize(1);
        assertThat(node.getInputs().get(0).getName()).isEqualTo("main");
    }

    @Test
    void hasTwoOutputs() {
        assertThat(node.getOutputs()).hasSize(2);
        assertThat(node.getOutputs().get(0).getName()).isEqualTo("released");
        assertThat(node.getOutputs().get(1).getName()).isEqualTo("buffered");
    }
}
