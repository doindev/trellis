package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class TryCatchNodeTest {

    private TryCatchNode node;

    @BeforeEach
    void setUp() {
        node = new TryCatchNode();
    }

    // ── Empty / null input ──

    @Test
    void emptyInputReturnsTwoEmptyOutputs() {
        NodeExecutionResult result = node.execute(ctx(List.of(), Map.of()));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).isEmpty();
    }

    @Test
    void nullInputReturnsTwoEmptyOutputs() {
        NodeExecutionResult result = node.execute(ctx(null, Map.of()));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).isEmpty();
    }

    // ── Clean items route to success output (index 0) ──

    @Nested
    class SuccessRouting {

        @Test
        void allCleanItemsGoToSuccessOutput() {
            List<Map<String, Object>> input = items(
                    Map.of("name", "Alice"),
                    Map.of("name", "Bob"),
                    Map.of("name", "Charlie")
            );

            NodeExecutionResult result = node.execute(ctx(input, Map.of()));

            assertThat(output(result, 0)).hasSize(3);
            assertThat(jsonAt(result, 0, 0)).containsEntry("name", "Alice");
            assertThat(jsonAt(result, 0, 1)).containsEntry("name", "Bob");
            assertThat(jsonAt(result, 0, 2)).containsEntry("name", "Charlie");
            assertThat(output(result, 1)).isEmpty();
        }

        @Test
        void itemWithoutErrorFieldGoesToSuccess() {
            List<Map<String, Object>> input = items(
                    Map.of("data", "value", "status", "ok")
            );

            NodeExecutionResult result = node.execute(ctx(input, Map.of()));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(output(result, 1)).isEmpty();
        }
    }

    // ── Items with error field route to error output (index 1) ──

    @Nested
    class ErrorRouting {

        @Test
        void allErrorItemsGoToErrorOutput() {
            List<Map<String, Object>> input = items(
                    Map.of("error", "err1"),
                    Map.of("error", "err2"),
                    Map.of("error", "err3")
            );

            NodeExecutionResult result = node.execute(ctx(input, Map.of()));

            assertThat(output(result, 0)).isEmpty();
            assertThat(output(result, 1)).hasSize(3);
        }

        @Test
        void singleErrorItemGoesToErrorOutput() {
            List<Map<String, Object>> input = items(
                    Map.of("error", "Something broke", "data", "x")
            );

            NodeExecutionResult result = node.execute(ctx(input, Map.of()));

            assertThat(output(result, 0)).isEmpty();
            assertThat(output(result, 1)).hasSize(1);
        }

        @Test
        void mixOfCleanAndErrorItemsSplitsCorrectly() {
            List<Map<String, Object>> input = items(
                    Map.of("name", "Alice"),
                    Map.of("error", "timeout", "name", "Bob"),
                    Map.of("name", "Charlie"),
                    Map.of("error", "not found")
            );

            NodeExecutionResult result = node.execute(ctx(input, Map.of()));

            assertThat(output(result, 0)).hasSize(2);
            assertThat(jsonAt(result, 0, 0)).containsEntry("name", "Alice");
            assertThat(jsonAt(result, 0, 1)).containsEntry("name", "Charlie");
            assertThat(output(result, 1)).hasSize(2);
        }
    }

    // ── Error output format modes ──

    @Nested
    class ErrorOutputModes {

        @Test
        void fullErrorModePassesCompleteItemData() {
            List<Map<String, Object>> input = items(
                    Map.of("error", "timeout", "name", "Bob", "id", 42)
            );
            Map<String, Object> params = mutableMap("errorOutput", "fullError");

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 1)).hasSize(1);
            Map<String, Object> errorItem = jsonAt(result, 1, 0);
            assertThat(errorItem).containsEntry("error", "timeout");
            assertThat(errorItem).containsEntry("name", "Bob");
            assertThat(errorItem).containsEntry("id", 42);
        }

        @Test
        void errorMessageModeOnlyIncludesErrorAndMessageFields() {
            List<Map<String, Object>> input = items(
                    Map.of("error", "timeout", "name", "Bob", "id", 42)
            );
            Map<String, Object> params = mutableMap("errorOutput", "errorMessage");

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 1)).hasSize(1);
            Map<String, Object> errorItem = jsonAt(result, 1, 0);
            assertThat(errorItem).containsEntry("error", "timeout");
            assertThat(errorItem).containsKey("message");
            // Extra fields must NOT be present in errorMessage mode
            assertThat(errorItem).doesNotContainKey("name");
            assertThat(errorItem).doesNotContainKey("id");
        }

        @Test
        void errorMessageModeUsesErrorAsMessageWhenNoMessageField() {
            List<Map<String, Object>> input = items(
                    Map.of("error", "connection reset")
            );
            Map<String, Object> params = mutableMap("errorOutput", "errorMessage");

            NodeExecutionResult result = node.execute(ctx(input, params));

            Map<String, Object> errorItem = jsonAt(result, 1, 0);
            assertThat(errorItem).containsEntry("error", "connection reset");
            assertThat(errorItem).containsEntry("message", "connection reset");
        }

        @Test
        void errorMessageModePreservesExistingMessageField() {
            List<Map<String, Object>> input = items(
                    Map.of("error", "HTTP_ERROR", "message", "Request timed out", "extra", "data")
            );
            Map<String, Object> params = mutableMap("errorOutput", "errorMessage");

            NodeExecutionResult result = node.execute(ctx(input, params));

            Map<String, Object> errorItem = jsonAt(result, 1, 0);
            assertThat(errorItem).containsEntry("error", "HTTP_ERROR");
            assertThat(errorItem).containsEntry("message", "Request timed out");
            assertThat(errorItem).doesNotContainKey("extra");
        }

        @Test
        void defaultErrorOutputModeIsFullError() {
            List<Map<String, Object>> input = items(
                    Map.of("error", "fail", "extra", "data")
            );

            // No errorOutput param -- default is fullError
            NodeExecutionResult result = node.execute(ctx(input, Map.of()));

            Map<String, Object> errorItem = jsonAt(result, 1, 0);
            assertThat(errorItem).containsEntry("error", "fail");
            assertThat(errorItem).containsEntry("extra", "data");
        }
    }

    // ── Result structure ──

    @Test
    void resultContinuesExecution() {
        List<Map<String, Object>> input = items(Map.of("x", 1));

        NodeExecutionResult result = node.execute(ctx(input, Map.of()));

        assertThat(result.isContinueExecution()).isTrue();
        assertThat(result.getError()).isNull();
    }

    @Test
    void resultAlwaysHasTwoOutputs() {
        List<Map<String, Object>> input = items(Map.of("x", 1));

        NodeExecutionResult result = node.execute(ctx(input, Map.of()));

        assertThat(result.getOutput()).hasSize(2);
    }

    @Test
    void emptyResultAlwaysHasTwoOutputs() {
        NodeExecutionResult result = node.execute(ctx(List.of(), Map.of()));

        assertThat(result.getOutput()).hasSize(2);
    }
}
