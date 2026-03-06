package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class StopAndErrorNodeTest {

    private StopAndErrorNode node;

    @BeforeEach
    void setUp() {
        node = new StopAndErrorNode();
    }

    // ── Simple error message ──

    @Nested
    class ErrorMessageMode {

        @Test
        void simpleErrorMessageReturnsError() {
            List<Map<String, Object>> input = items(Map.of("x", 1));
            Map<String, Object> params = mutableMap(
                    "errorType", "errorMessage",
                    "errorMessage", "Something went wrong"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(result.getError()).isNotNull();
            assertThat(result.getError().getMessage()).isEqualTo("Something went wrong");
            assertThat(result.isContinueExecution()).isFalse();
        }

        @Test
        void emptyErrorMessageUsesDefaultMessage() {
            List<Map<String, Object>> input = items(Map.of("x", 1));
            Map<String, Object> params = mutableMap(
                    "errorType", "errorMessage",
                    "errorMessage", ""
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(result.getError()).isNotNull();
            assertThat(result.getError().getMessage()).isEqualTo("Workflow stopped with error");
            assertThat(result.isContinueExecution()).isFalse();
        }

        @Test
        void blankErrorMessageUsesDefaultMessage() {
            List<Map<String, Object>> input = items(Map.of("x", 1));
            Map<String, Object> params = mutableMap(
                    "errorType", "errorMessage",
                    "errorMessage", "   "
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(result.getError()).isNotNull();
            assertThat(result.getError().getMessage()).isEqualTo("Workflow stopped with error");
        }

        @Test
        void errorNodeSetsNodeIdOnError() {
            List<Map<String, Object>> input = items(Map.of("x", 1));
            Map<String, Object> params = mutableMap(
                    "errorType", "errorMessage",
                    "errorMessage", "fail"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(result.getError().getNode()).isEqualTo("node-test");
        }
    }

    // ── JSON error object ──

    @Nested
    class ErrorObjectMode {

        @Test
        void errorObjectExtractsMessageField() {
            List<Map<String, Object>> input = items(Map.of("x", 1));
            Map<String, Object> params = mutableMap(
                    "errorType", "errorObject",
                    "errorObject", "{\"message\": \"Not found\", \"code\": 404}"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(result.getError()).isNotNull();
            assertThat(result.getError().getMessage()).isEqualTo("Not found");
            assertThat(result.isContinueExecution()).isFalse();
        }

        @Test
        void errorObjectFallsBackToDescriptionField() {
            List<Map<String, Object>> input = items(Map.of("x", 1));
            Map<String, Object> params = mutableMap(
                    "errorType", "errorObject",
                    "errorObject", "{\"description\": \"Resource missing\", \"code\": 404}"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(result.getError().getMessage()).isEqualTo("Resource missing");
        }

        @Test
        void errorObjectFallsBackToErrorField() {
            List<Map<String, Object>> input = items(Map.of("x", 1));
            Map<String, Object> params = mutableMap(
                    "errorType", "errorObject",
                    "errorObject", "{\"error\": \"Timeout\", \"code\": 504}"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(result.getError().getMessage()).isEqualTo("Timeout");
        }

        @Test
        void errorObjectWithNoKnownFieldUsesStringifiedJson() {
            List<Map<String, Object>> input = items(Map.of("x", 1));
            String json = "{\"code\": 500, \"status\": \"fail\"}";
            Map<String, Object> params = mutableMap(
                    "errorType", "errorObject",
                    "errorObject", json
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(result.getError().getMessage()).isEqualTo(json);
        }

        @Test
        void errorObjectIncludesContextMap() {
            List<Map<String, Object>> input = items(Map.of("x", 1));
            Map<String, Object> params = mutableMap(
                    "errorType", "errorObject",
                    "errorObject", "{\"message\": \"fail\", \"code\": 500, \"details\": \"server error\"}"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(result.getError().getContext()).isNotNull();
            assertThat(result.getError().getContext()).containsEntry("code", 500);
            assertThat(result.getError().getContext()).containsEntry("details", "server error");
        }

        @Test
        void invalidJsonReturnsInvalidErrorObjectMessage() {
            List<Map<String, Object>> input = items(Map.of("x", 1));
            Map<String, Object> params = mutableMap(
                    "errorType", "errorObject",
                    "errorObject", "not valid json {"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(result.getError()).isNotNull();
            assertThat(result.getError().getMessage()).startsWith("Invalid error object:");
            assertThat(result.isContinueExecution()).isFalse();
        }

        @Test
        void emptyJsonObjectReturnsUnknownError() {
            List<Map<String, Object>> input = items(Map.of("x", 1));
            Map<String, Object> params = mutableMap(
                    "errorType", "errorObject",
                    "errorObject", ""
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(result.getError()).isNotNull();
            assertThat(result.getError().getMessage()).isEqualTo("Unknown error");
        }
    }

    // ── continueExecution is always false ──

    @Test
    void continueExecutionIsAlwaysFalse() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "errorType", "errorMessage",
                "errorMessage", "stop"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.isContinueExecution()).isFalse();
    }

    @Test
    void defaultErrorTypeIsErrorMessage() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "errorMessage", "default type test"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getError()).isNotNull();
        assertThat(result.getError().getMessage()).isEqualTo("default type test");
    }
}
