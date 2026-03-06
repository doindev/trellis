package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class RetryNodeTest {

    private RetryNode node;

    @BeforeEach
    void setUp() {
        node = new RetryNode();
    }

    // ── Empty / null input ──

    @Test
    void emptyInputReturnsTwoEmptyOutputs() {
        Map<String, Object> params = mutableMap("maxAttempts", 3, "waitBetweenRetries", 0);

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).isEmpty();
    }

    @Test
    void nullInputReturnsTwoEmptyOutputs() {
        Map<String, Object> params = mutableMap("maxAttempts", 3, "waitBetweenRetries", 0);

        NodeExecutionResult result = node.execute(ctx(null, params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).isEmpty();
    }

    // ── Items without errors pass through to success (output 0) ──

    @Nested
    class SuccessItems {

        @Test
        void cleanItemsPassThroughToSuccessOutput() {
            List<Map<String, Object>> input = items(
                    Map.of("name", "Alice"),
                    Map.of("name", "Bob")
            );
            Map<String, Object> params = mutableMap("maxAttempts", 3, "waitBetweenRetries", 0);

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(2);
            assertThat(jsonAt(result, 0, 0)).containsEntry("name", "Alice");
            assertThat(jsonAt(result, 0, 1)).containsEntry("name", "Bob");
            assertThat(output(result, 1)).isEmpty();
        }

        @Test
        void mixOfCleanAndErrorItemsRoutedCorrectly() {
            List<Map<String, Object>> input = items(
                    Map.of("name", "Alice"),
                    Map.of("error", "timeout", "data", "x")
            );
            Map<String, Object> params = mutableMap("maxAttempts", 3, "waitBetweenRetries", 0);

            NodeExecutionResult result = node.execute(ctx(input, params));

            // Alice (clean) + retried error item both go to success
            assertThat(output(result, 0)).hasSize(2);
            // No exhausted items (retryCount=1, max=3)
            assertThat(output(result, 1)).isEmpty();
        }
    }

    // ── Error items with _retryCount < maxAttempts get retried ──

    @Nested
    class RetryItems {

        @Test
        void errorItemOnFirstAttemptGetsRetried() {
            List<Map<String, Object>> input = items(
                    Map.of("error", "connection reset", "_retryCount", 0)
            );
            Map<String, Object> params = mutableMap("maxAttempts", 3, "waitBetweenRetries", 0);

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            Map<String, Object> retried = jsonAt(result, 0, 0);
            assertThat(retried).doesNotContainKey("error");
            assertThat(retried).containsEntry("_retryCount", 1);
            assertThat(retried).containsEntry("_retryAttempt", 1);
            assertThat(output(result, 1)).isEmpty();
        }

        @Test
        void errorItemWithNoRetryCountStartsAtOne() {
            List<Map<String, Object>> input = items(
                    Map.of("error", "some error", "data", "payload")
            );
            Map<String, Object> params = mutableMap("maxAttempts", 3, "waitBetweenRetries", 0);

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            Map<String, Object> retried = jsonAt(result, 0, 0);
            assertThat(retried).containsEntry("_retryCount", 1);
            assertThat(retried).containsEntry("data", "payload");
            assertThat(retried).doesNotContainKey("error");
        }

        @Test
        void retriedItemHasErrorFieldRemoved() {
            List<Map<String, Object>> input = items(
                    Map.of("error", "fail", "data", "keep")
            );
            Map<String, Object> params = mutableMap("maxAttempts", 3, "waitBetweenRetries", 0);

            NodeExecutionResult result = node.execute(ctx(input, params));

            Map<String, Object> retried = jsonAt(result, 0, 0);
            assertThat(retried).doesNotContainKey("error");
            assertThat(retried).containsEntry("data", "keep");
        }

        @Test
        void retriedItemHasErrorAndMessageRemoved() {
            List<Map<String, Object>> input = items(
                    Map.of("error", "fail", "message", "details about failure", "data", "keep")
            );
            Map<String, Object> params = mutableMap("maxAttempts", 3, "waitBetweenRetries", 0);

            NodeExecutionResult result = node.execute(ctx(input, params));

            Map<String, Object> retried = jsonAt(result, 0, 0);
            assertThat(retried).doesNotContainKey("error");
            assertThat(retried).doesNotContainKey("message");
            assertThat(retried).containsEntry("data", "keep");
        }
    }

    // ── Error items with _retryCount >= maxAttempts go to exhausted (output 1) ──

    @Nested
    class ExhaustedItems {

        @Test
        void itemWithRetryCountAtMaxGoesToExhausted() {
            List<Map<String, Object>> input = items(
                    Map.of("error", "persistent error", "_retryCount", 2)
            );
            Map<String, Object> params = mutableMap("maxAttempts", 3, "waitBetweenRetries", 0);

            NodeExecutionResult result = node.execute(ctx(input, params));

            // retryCount is incremented to 3, which equals maxAttempts
            assertThat(output(result, 0)).isEmpty();
            assertThat(output(result, 1)).hasSize(1);
            Map<String, Object> exhausted = jsonAt(result, 1, 0);
            assertThat(exhausted).containsEntry("_retryCount", 3);
            assertThat(exhausted).containsEntry("_retriesExhausted", true);
        }

        @Test
        void itemWithRetryCountOverMaxGoesToExhausted() {
            List<Map<String, Object>> input = items(
                    Map.of("error", "still failing", "_retryCount", 5)
            );
            Map<String, Object> params = mutableMap("maxAttempts", 3, "waitBetweenRetries", 0);

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).isEmpty();
            assertThat(output(result, 1)).hasSize(1);
            Map<String, Object> exhausted = jsonAt(result, 1, 0);
            assertThat(exhausted).containsEntry("_retriesExhausted", true);
        }

        @Test
        void exhaustedItemsHaveRetriesExhaustedTrue() {
            List<Map<String, Object>> input = items(
                    Map.of("error", "fail", "_retryCount", 2)
            );
            Map<String, Object> params = mutableMap("maxAttempts", 3, "waitBetweenRetries", 0);

            NodeExecutionResult result = node.execute(ctx(input, params));

            Map<String, Object> exhausted = jsonAt(result, 1, 0);
            assertThat(exhausted).containsEntry("_retriesExhausted", true);
            assertThat(exhausted).containsEntry("_retryCount", 3);
            assertThat(exhausted).containsKey("error");
        }

        @Test
        void maxAttemptsOfOneExhaustsImmediately() {
            List<Map<String, Object>> input = items(
                    Map.of("error", "fail")
            );
            Map<String, Object> params = mutableMap("maxAttempts", 1, "waitBetweenRetries", 0);

            NodeExecutionResult result = node.execute(ctx(input, params));

            // retryCount goes from 0 -> 1, which equals maxAttempts (1)
            assertThat(output(result, 0)).isEmpty();
            assertThat(output(result, 1)).hasSize(1);
            assertThat(jsonAt(result, 1, 0)).containsEntry("_retriesExhausted", true);
            assertThat(jsonAt(result, 1, 0)).containsEntry("_retryCount", 1);
        }
    }

    // ── Multiple items with mixed results ──

    @Test
    void multipleItemsRoutedCorrectly() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1),                                      // no error -> success
                Map.of("id", 2, "error", "fail", "_retryCount", 0),  // retry
                Map.of("id", 3, "error", "fail", "_retryCount", 2),  // exhausted (retry=3 >= max=3)
                Map.of("id", 4)                                       // no error -> success
        );
        Map<String, Object> params = mutableMap("maxAttempts", 3, "waitBetweenRetries", 0);

        NodeExecutionResult result = node.execute(ctx(input, params));

        // Success: id=1, id=2 (retried), id=4
        assertThat(output(result, 0)).hasSize(3);
        // Exhausted: id=3
        assertThat(output(result, 1)).hasSize(1);
        assertThat(jsonAt(result, 1, 0)).containsEntry("id", 3);
    }

    // ── Default max attempts ──

    @Test
    void defaultMaxAttemptsIsThree() {
        List<Map<String, Object>> input = items(
                Map.of("error", "fail", "_retryCount", 2)
        );
        // No maxAttempts param -- should default to 3

        NodeExecutionResult result = node.execute(ctx(input, mutableMap("waitBetweenRetries", 0)));

        // retryCount 2 -> 3, 3 >= 3 = exhausted
        assertThat(output(result, 1)).hasSize(1);
    }

    // ── Result structure ──

    @Test
    void resultContinuesExecution() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap("maxAttempts", 3, "waitBetweenRetries", 0);

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.isContinueExecution()).isTrue();
        assertThat(result.getError()).isNull();
    }

    @Test
    void resultAlwaysHasTwoOutputs() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap("maxAttempts", 3, "waitBetweenRetries", 0);

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getOutput()).hasSize(2);
    }
}
