package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;

class LoopOverItemsNodeTest {

    private LoopOverItemsNode node;

    @BeforeEach
    void setUp() {
        node = new LoopOverItemsNode();
    }

    // ── First batch outputs to "loop" output ──

    @Test
    void firstRunOutputsFirstBatchToLoopOutput() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1),
                Map.of("id", 2),
                Map.of("id", 3)
        );
        Map<String, Object> params = mutableMap("batchSize", 1);
        Map<String, Object> nodeCtx = new HashMap<>();

        NodeExecutionContext context = contextBuilder()
                .inputData(input)
                .parameters(params)
                .nodeContextData(nodeCtx)
                .build();

        NodeExecutionResult result = node.execute(context);

        // Output 0 (Done) = empty on first run
        assertThat(output(result, 0)).isEmpty();
        // Output 1 (Loop) = first batch
        assertThat(output(result, 1)).hasSize(1);
        assertThat(jsonAt(result, 1, 0)).containsEntry("id", 1);
    }

    @Test
    void firstRunWithBatchSizeLargerThanInputOutputsAll() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1),
                Map.of("id", 2)
        );
        Map<String, Object> params = mutableMap("batchSize", 10);
        Map<String, Object> nodeCtx = new HashMap<>();

        NodeExecutionContext context = contextBuilder()
                .inputData(input)
                .parameters(params)
                .nodeContextData(nodeCtx)
                .build();

        NodeExecutionResult result = node.execute(context);

        // Output 0 (Done) = empty
        assertThat(output(result, 0)).isEmpty();
        // Output 1 (Loop) = all items
        assertThat(output(result, 1)).hasSize(2);
    }

    // ── Batch size parameter ──

    @Nested
    class BatchSize {

        @Test
        void batchSizeOfTwoOutputsTwoItemsAtATime() {
            List<Map<String, Object>> input = items(
                    Map.of("id", 1),
                    Map.of("id", 2),
                    Map.of("id", 3),
                    Map.of("id", 4),
                    Map.of("id", 5)
            );
            Map<String, Object> params = mutableMap("batchSize", 2);
            Map<String, Object> nodeCtx = new HashMap<>();

            NodeExecutionContext context = contextBuilder()
                    .inputData(input)
                    .parameters(params)
                    .nodeContextData(nodeCtx)
                    .build();

            NodeExecutionResult result = node.execute(context);

            // First batch: 2 items on loop output
            assertThat(output(result, 1)).hasSize(2);
            assertThat(jsonAt(result, 1, 0)).containsEntry("id", 1);
            assertThat(jsonAt(result, 1, 1)).containsEntry("id", 2);
        }

        @Test
        void defaultBatchSizeIsOne() {
            List<Map<String, Object>> input = items(
                    Map.of("id", 1),
                    Map.of("id", 2)
            );
            Map<String, Object> nodeCtx = new HashMap<>();

            NodeExecutionContext context = contextBuilder()
                    .inputData(input)
                    .parameters(Map.of())
                    .nodeContextData(nodeCtx)
                    .build();

            NodeExecutionResult result = node.execute(context);

            assertThat(output(result, 1)).hasSize(1);
        }

        @Test
        void batchSizeBelowOneIsTreatedAsOne() {
            List<Map<String, Object>> input = items(
                    Map.of("id", 1),
                    Map.of("id", 2)
            );
            Map<String, Object> params = mutableMap("batchSize", 0);
            Map<String, Object> nodeCtx = new HashMap<>();

            NodeExecutionContext context = contextBuilder()
                    .inputData(input)
                    .parameters(params)
                    .nodeContextData(nodeCtx)
                    .build();

            NodeExecutionResult result = node.execute(context);

            assertThat(output(result, 1)).hasSize(1);
        }
    }

    // ── Empty input ──

    @Test
    void emptyInputOnFirstRunProducesAllDone() {
        Map<String, Object> params = mutableMap("batchSize", 1);
        Map<String, Object> nodeCtx = new HashMap<>();

        NodeExecutionContext context = contextBuilder()
                .inputData(List.of())
                .parameters(params)
                .nodeContextData(nodeCtx)
                .build();

        NodeExecutionResult result = node.execute(context);

        // With empty input, first batch is empty, so we go straight to done path
        // returnItems is empty, so output 0 (Done) = processedItems (empty)
        // output 1 (Loop) = empty
        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).isEmpty();
    }

    @Test
    void nullInputOnFirstRunProducesAllDone() {
        Map<String, Object> params = mutableMap("batchSize", 1);
        Map<String, Object> nodeCtx = new HashMap<>();

        NodeExecutionContext context = contextBuilder()
                .inputData(null)
                .parameters(params)
                .nodeContextData(nodeCtx)
                .build();

        NodeExecutionResult result = node.execute(context);

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).isEmpty();
    }

    // ── State management ──

    @Test
    void nodeContextDataStoresRemainingItems() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1),
                Map.of("id", 2),
                Map.of("id", 3)
        );
        Map<String, Object> params = mutableMap("batchSize", 1);
        Map<String, Object> nodeCtx = new HashMap<>();

        NodeExecutionContext context = contextBuilder()
                .inputData(input)
                .parameters(params)
                .nodeContextData(nodeCtx)
                .build();

        node.execute(context);

        // After first run, remaining items should be stored
        assertThat(nodeCtx).containsKey("items");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> remaining = (List<Map<String, Object>>) nodeCtx.get("items");
        assertThat(remaining).hasSize(2);
    }

    @Test
    void nodeContextDataStoresRunIndex() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1),
                Map.of("id", 2)
        );
        Map<String, Object> params = mutableMap("batchSize", 1);
        Map<String, Object> nodeCtx = new HashMap<>();

        NodeExecutionContext context = contextBuilder()
                .inputData(input)
                .parameters(params)
                .nodeContextData(nodeCtx)
                .build();

        node.execute(context);

        assertThat(nodeCtx).containsEntry("currentRunIndex", 0);
    }

    // ── Result structure ──

    @Test
    void resultAlwaysContinuesExecution() {
        List<Map<String, Object>> input = items(Map.of("id", 1));
        Map<String, Object> params = mutableMap("batchSize", 1);
        Map<String, Object> nodeCtx = new HashMap<>();

        NodeExecutionContext context = contextBuilder()
                .inputData(input)
                .parameters(params)
                .nodeContextData(nodeCtx)
                .build();

        NodeExecutionResult result = node.execute(context);

        assertThat(result.isContinueExecution()).isTrue();
        assertThat(result.getError()).isNull();
    }

    @Test
    void resultHasTwoOutputs() {
        List<Map<String, Object>> input = items(Map.of("id", 1));
        Map<String, Object> params = mutableMap("batchSize", 1);
        Map<String, Object> nodeCtx = new HashMap<>();

        NodeExecutionContext context = contextBuilder()
                .inputData(input)
                .parameters(params)
                .nodeContextData(nodeCtx)
                .build();

        NodeExecutionResult result = node.execute(context);

        assertThat(result.getOutput()).hasSize(2);
    }

    // ── Subsequent run simulation ──

    @Test
    void subsequentRunProcessesNextBatch() {
        // Simulate state after first run
        Map<String, Object> nodeCtx = new HashMap<>();
        nodeCtx.put("items", new java.util.ArrayList<>(List.of(
                item(Map.of("id", 2)),
                item(Map.of("id", 3))
        )));
        nodeCtx.put("processedItems", new java.util.ArrayList<>());
        nodeCtx.put("currentRunIndex", 0);

        // Subsequent run: input is the processed result from previous batch
        List<Map<String, Object>> processedBatch = items(Map.of("id", 1, "processed", true));
        Map<String, Object> params = mutableMap("batchSize", 1);

        NodeExecutionContext context = contextBuilder()
                .inputData(processedBatch)
                .parameters(params)
                .nodeContextData(nodeCtx)
                .build();

        NodeExecutionResult result = node.execute(context);

        // Should output next batch (id=2) on loop output
        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).hasSize(1);
        assertThat(jsonAt(result, 1, 0)).containsEntry("id", 2);
    }

    @Test
    void finalRunOutputsAllProcessedItemsToDoneOutput() {
        // Simulate state where all items have been consumed
        Map<String, Object> nodeCtx = new HashMap<>();
        nodeCtx.put("items", new java.util.ArrayList<>()); // no remaining items
        nodeCtx.put("processedItems", new java.util.ArrayList<>(List.of(
                item(Map.of("id", 1, "done", true))
        )));
        nodeCtx.put("currentRunIndex", 1);

        // Last processed batch
        List<Map<String, Object>> processedBatch = items(Map.of("id", 2, "done", true));
        Map<String, Object> params = mutableMap("batchSize", 1);

        NodeExecutionContext context = contextBuilder()
                .inputData(processedBatch)
                .parameters(params)
                .nodeContextData(nodeCtx)
                .build();

        NodeExecutionResult result = node.execute(context);

        // Output 0 (Done) = accumulated processed items
        assertThat(output(result, 0)).hasSize(2);
        // Output 1 (Loop) = empty
        assertThat(output(result, 1)).isEmpty();
    }
}
