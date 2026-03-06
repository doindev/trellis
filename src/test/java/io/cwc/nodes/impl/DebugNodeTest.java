package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class DebugNodeTest {

    private DebugNode node;

    @BeforeEach
    void setUp() {
        node = new DebugNode();
    }

    // ── Default behavior - data passes through ──

    @Test
    void dataPassesThroughWithOriginalFields() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice", "age", 30)
        );

        NodeExecutionResult result = node.execute(ctx(input));

        assertThat(output(result)).hasSize(1);
        Map<String, Object> json = firstJson(result);
        assertThat(json).containsEntry("name", "Alice");
        assertThat(json).containsEntry("age", 30);
    }

    @Test
    void multipleItemsPassThrough() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1),
                Map.of("id", 2),
                Map.of("id", 3)
        );

        NodeExecutionResult result = node.execute(ctx(input));

        assertThat(output(result)).hasSize(3);
        assertThat(jsonAt(result, 0)).containsEntry("id", 1);
        assertThat(jsonAt(result, 1)).containsEntry("id", 2);
        assertThat(jsonAt(result, 2)).containsEntry("id", 3);
    }

    // ── _debug metadata added ──

    @Test
    void addsDebugMetadataToEachItem() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice")
        );

        NodeExecutionResult result = node.execute(ctx(input));

        Map<String, Object> json = firstJson(result);
        assertThat(json).containsKey("_debug");
        @SuppressWarnings("unchecked")
        Map<String, Object> debug = (Map<String, Object>) json.get("_debug");
        assertThat(debug).containsKey("_timestamp");
        assertThat(debug).containsEntry("_nodeId", "node-test");
        assertThat(debug).containsEntry("_itemCount", 1);
        assertThat(debug).containsKey("_fieldNames");
    }

    @Test
    void debugMetadataContainsCorrectItemCount() {
        List<Map<String, Object>> input = items(
                Map.of("a", 1),
                Map.of("b", 2)
        );

        NodeExecutionResult result = node.execute(ctx(input));

        @SuppressWarnings("unchecked")
        Map<String, Object> debug0 = (Map<String, Object>) firstJson(result).get("_debug");
        assertThat(debug0).containsEntry("_itemCount", 2);

        @SuppressWarnings("unchecked")
        Map<String, Object> debug1 = (Map<String, Object>) jsonAt(result, 1).get("_debug");
        assertThat(debug1).containsEntry("_itemCount", 2);
    }

    @Test
    void debugMetadataContainsFieldNames() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice", "email", "alice@example.com")
        );

        NodeExecutionResult result = node.execute(ctx(input));

        @SuppressWarnings("unchecked")
        Map<String, Object> debug = (Map<String, Object>) firstJson(result).get("_debug");
        @SuppressWarnings("unchecked")
        List<String> fieldNames = (List<String>) debug.get("_fieldNames");
        assertThat(fieldNames).contains("name", "email");
    }

    // ── Custom log message ──

    @Test
    void executesWithCustomLogMessage() {
        List<Map<String, Object>> input = items(
                Map.of("x", 1)
        );
        Map<String, Object> params = mutableMap(
                "logMessage", "Checkpoint A"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        // Node should execute successfully and pass data through
        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("x", 1);
        assertThat(result.isContinueExecution()).isTrue();
    }

    // ── Pause execution ──

    @Test
    void pauseExecutionWaitsSpecifiedDuration() {
        List<Map<String, Object>> input = items(
                Map.of("x", 1)
        );
        Map<String, Object> params = mutableMap(
                "pauseExecution", true,
                "pauseMs", 50 // short delay for test
        );

        long start = System.currentTimeMillis();
        NodeExecutionResult result = node.execute(ctx(input, params));
        long elapsed = System.currentTimeMillis() - start;

        assertThat(output(result)).hasSize(1);
        assertThat(elapsed).isGreaterThanOrEqualTo(40); // allow small margin
    }

    @Test
    void noPauseWhenPauseExecutionIsFalse() {
        List<Map<String, Object>> input = items(
                Map.of("x", 1)
        );
        Map<String, Object> params = mutableMap(
                "pauseExecution", false,
                "pauseMs", 5000
        );

        long start = System.currentTimeMillis();
        NodeExecutionResult result = node.execute(ctx(input, params));
        long elapsed = System.currentTimeMillis() - start;

        assertThat(output(result)).hasSize(1);
        assertThat(elapsed).isLessThan(1000);
    }

    // ── Empty input ──

    @Test
    void emptyInputReturnsEmptyResult() {
        NodeExecutionResult result = node.execute(ctx(List.of()));

        assertThat(output(result)).isEmpty();
    }

    @Test
    void nullInputReturnsEmptyResult() {
        NodeExecutionResult result = node.execute(ctx(null));

        assertThat(output(result)).isEmpty();
    }

    // ── Result metadata ──

    @Test
    void resultContinuesExecution() {
        List<Map<String, Object>> input = items(Map.of("x", 1));

        NodeExecutionResult result = node.execute(ctx(input));

        assertThat(result.isContinueExecution()).isTrue();
        assertThat(result.getError()).isNull();
    }

    // ── Log level parameter does not affect output ──

    @Test
    void differentLogLevelsProduceSameOutput() {
        List<Map<String, Object>> input = items(Map.of("val", 42));

        for (String level : List.of("info", "debug", "warn")) {
            Map<String, Object> params = mutableMap("logLevel", level);
            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result)).hasSize(1);
            assertThat(firstJson(result)).containsEntry("val", 42);
        }
    }
}
