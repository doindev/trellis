package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class RateLimiterNodeTest {

    private RateLimiterNode node;

    @BeforeEach
    void setUp() {
        node = new RateLimiterNode();
    }

    // ── Empty / null input ──

    @Test
    void emptyInputReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "maxItems", 10,
                "timeWindow", 0,
                "strategy", "delay"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).isEmpty();
    }

    @Test
    void nullInputReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "maxItems", 10,
                "timeWindow", 0,
                "strategy", "delay"
        );

        NodeExecutionResult result = node.execute(ctx(null, params));

        assertThat(output(result)).isEmpty();
    }

    // ── Drop strategy ──

    @Nested
    class DropStrategy {

        @Test
        void itemsWithinLimitAllPassThrough() {
            List<Map<String, Object>> input = items(
                    Map.of("id", 1),
                    Map.of("id", 2)
            );
            Map<String, Object> params = mutableMap(
                    "maxItems", 10,
                    "timeWindow", 0,
                    "strategy", "drop"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result)).hasSize(2);
        }

        @Test
        void itemsEqualToLimitAllPassThrough() {
            List<Map<String, Object>> input = items(
                    Map.of("id", 1),
                    Map.of("id", 2),
                    Map.of("id", 3)
            );
            Map<String, Object> params = mutableMap(
                    "maxItems", 3,
                    "timeWindow", 0,
                    "strategy", "drop"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result)).hasSize(3);
        }

        @Test
        void itemsExceedingLimitDropsExcess() {
            List<Map<String, Object>> input = items(
                    Map.of("id", 1),
                    Map.of("id", 2),
                    Map.of("id", 3),
                    Map.of("id", 4),
                    Map.of("id", 5)
            );
            Map<String, Object> params = mutableMap(
                    "maxItems", 3,
                    "timeWindow", 0,
                    "strategy", "drop"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result)).hasSize(3);
            assertThat(jsonAt(result, 0)).containsEntry("id", 1);
            assertThat(jsonAt(result, 1)).containsEntry("id", 2);
            assertThat(jsonAt(result, 2)).containsEntry("id", 3);
        }

        @Test
        void maxItemsOfOneDropsAllButFirst() {
            List<Map<String, Object>> input = items(
                    Map.of("id", 1),
                    Map.of("id", 2),
                    Map.of("id", 3),
                    Map.of("id", 4)
            );
            Map<String, Object> params = mutableMap(
                    "maxItems", 1,
                    "timeWindow", 0,
                    "strategy", "drop"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result)).hasSize(1);
            assertThat(jsonAt(result, 0)).containsEntry("id", 1);
        }
    }

    // ── Delay strategy ──

    @Nested
    class DelayStrategy {

        @Test
        void itemsWithinLimitAllPassThrough() {
            List<Map<String, Object>> input = items(
                    Map.of("id", 1),
                    Map.of("id", 2),
                    Map.of("id", 3)
            );
            Map<String, Object> params = mutableMap(
                    "maxItems", 10,
                    "timeWindow", 0,
                    "strategy", "delay"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result)).hasSize(3);
        }

        @Test
        void itemsExceedingLimitAllPassThroughEventually() {
            List<Map<String, Object>> input = items(
                    Map.of("id", 1),
                    Map.of("id", 2),
                    Map.of("id", 3),
                    Map.of("id", 4),
                    Map.of("id", 5)
            );
            Map<String, Object> params = mutableMap(
                    "maxItems", 2,
                    "timeWindow", 0,
                    "strategy", "delay"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            // All items pass through even though processed in batches
            assertThat(output(result)).hasSize(5);
            assertThat(jsonAt(result, 0)).containsEntry("id", 1);
            assertThat(jsonAt(result, 4)).containsEntry("id", 5);
        }

        @Test
        void singleBatchNoDelayNeeded() {
            List<Map<String, Object>> input = items(
                    Map.of("id", 1),
                    Map.of("id", 2)
            );
            Map<String, Object> params = mutableMap(
                    "maxItems", 5,
                    "timeWindow", 100,
                    "strategy", "delay"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result)).hasSize(2);
        }

        @Test
        void allItemsPassThroughWithLargeLimit() {
            List<Map<String, Object>> input = items(
                    Map.of("id", 1),
                    Map.of("id", 2),
                    Map.of("id", 3)
            );
            Map<String, Object> params = mutableMap(
                    "maxItems", 100,
                    "timeWindow", 0,
                    "strategy", "delay"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result)).hasSize(3);
        }
    }

    // ── Default strategy ──

    @Test
    void defaultStrategyIsDelay() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1),
                Map.of("id", 2),
                Map.of("id", 3)
        );
        // No strategy param - defaults to "delay"
        Map<String, Object> params = mutableMap(
                "maxItems", 2,
                "timeWindow", 0
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        // Delay mode: all items pass through
        assertThat(output(result)).hasSize(3);
    }

    // ── Node metadata ──

    @Test
    void hasOneInput() {
        assertThat(node.getInputs()).hasSize(1);
        assertThat(node.getInputs().get(0).getName()).isEqualTo("main");
    }

    @Test
    void hasOneOutput() {
        assertThat(node.getOutputs()).hasSize(1);
        assertThat(node.getOutputs().get(0).getName()).isEqualTo("main");
    }
}
