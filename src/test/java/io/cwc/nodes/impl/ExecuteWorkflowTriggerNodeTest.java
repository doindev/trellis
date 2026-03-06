package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class ExecuteWorkflowTriggerNodeTest {

    private ExecuteWorkflowTriggerNode node;

    @BeforeEach
    void setUp() {
        node = new ExecuteWorkflowTriggerNode();
    }

    // -- Passthrough mode --

    @Test
    void passthroughModeReturnsAllInputDataUnchanged() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice", "age", 30),
                Map.of("name", "Bob", "age", 25)
        );
        Map<String, Object> params = mutableMap("inputSource", "passthrough");

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(2);
        assertThat(jsonAt(result, 0)).containsEntry("name", "Alice");
        assertThat(jsonAt(result, 0)).containsEntry("age", 30);
        assertThat(jsonAt(result, 1)).containsEntry("name", "Bob");
        assertThat(jsonAt(result, 1)).containsEntry("age", 25);
    }

    @Test
    void passthroughIsDefaultInputSource() {
        List<Map<String, Object>> input = items(Map.of("key", "value"));
        // No inputSource param - defaults to "passthrough"

        NodeExecutionResult result = node.execute(ctx(input, Map.of()));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("key", "value");
    }

    // -- Defined fields mode (workflowInputs) --

    @Test
    void workflowInputsModeFiltersToOnlyDeclaredFields() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice", "age", 30, "email", "alice@example.com")
        );
        Map<String, Object> params = mutableMap(
                "inputSource", "workflowInputs",
                "workflowInputs", mutableMap(
                        "values", List.of(
                                mutableMap("name", "name", "type", "string"),
                                mutableMap("name", "email", "type", "string")
                        )
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("name", "Alice");
        assertThat(firstJson(result)).containsEntry("email", "alice@example.com");
        assertThat(firstJson(result)).doesNotContainKey("age");
    }

    @Test
    void workflowInputsModeWithMultipleItems() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice", "extra", "x"),
                Map.of("name", "Bob", "extra", "y")
        );
        Map<String, Object> params = mutableMap(
                "inputSource", "workflowInputs",
                "workflowInputs", mutableMap(
                        "values", List.of(
                                mutableMap("name", "name", "type", "string")
                        )
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(2);
        assertThat(jsonAt(result, 0)).containsEntry("name", "Alice");
        assertThat(jsonAt(result, 0)).doesNotContainKey("extra");
        assertThat(jsonAt(result, 1)).containsEntry("name", "Bob");
        assertThat(jsonAt(result, 1)).doesNotContainKey("extra");
    }

    // -- Missing fields set to null --

    @Test
    void workflowInputsModeMissingFieldsSetToNull() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice")
        );
        Map<String, Object> params = mutableMap(
                "inputSource", "workflowInputs",
                "workflowInputs", mutableMap(
                        "values", List.of(
                                mutableMap("name", "name", "type", "string"),
                                mutableMap("name", "missingField", "type", "string")
                        )
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("name", "Alice");
        assertThat(firstJson(result)).containsKey("missingField");
        assertThat(firstJson(result).get("missingField")).isNull();
    }

    // -- Workflow inputs with empty fields falls back to passthrough --

    @Test
    void workflowInputsModeWithEmptyFieldsFallsBackToPassthrough() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice", "age", 30));
        Map<String, Object> params = mutableMap(
                "inputSource", "workflowInputs",
                "workflowInputs", mutableMap("values", List.of())
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        // Falls back to passthrough since no fields defined
        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("name", "Alice");
        assertThat(firstJson(result)).containsEntry("age", 30);
    }

    // -- JSON example mode --

    @Test
    void jsonExampleModeFiltersToKeysFromExample() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice", "count", 5, "active", true, "extra", "remove")
        );
        Map<String, Object> params = mutableMap(
                "inputSource", "jsonExample",
                "jsonExample", "{\"name\": \"example\", \"count\": 123, \"active\": true}"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("name", "Alice");
        assertThat(firstJson(result)).containsEntry("count", 5);
        assertThat(firstJson(result)).containsEntry("active", true);
        assertThat(firstJson(result)).doesNotContainKey("extra");
    }

    @Test
    void jsonExampleModeWithMissingFieldsSetsToNull() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice")
        );
        Map<String, Object> params = mutableMap(
                "inputSource", "jsonExample",
                "jsonExample", "{\"name\": \"example\", \"age\": 0}"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("name", "Alice");
        assertThat(firstJson(result)).containsKey("age");
        assertThat(firstJson(result).get("age")).isNull();
    }

    @Test
    void jsonExampleModeWithInvalidJsonFallsBackToPassthrough() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice"));
        Map<String, Object> params = mutableMap(
                "inputSource", "jsonExample",
                "jsonExample", "not valid json"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        // Invalid JSON yields empty field set -> falls back to passthrough
        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("name", "Alice");
    }

    @Test
    void jsonExampleModeWithEmptyObjectFallsBackToPassthrough() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice"));
        Map<String, Object> params = mutableMap(
                "inputSource", "jsonExample",
                "jsonExample", "{}"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        // Empty JSON object yields empty field set -> falls back to passthrough
        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("name", "Alice");
    }

    // -- No input data produces empty trigger item --

    @Test
    void noInputDataProducesEmptyTriggerItem() {
        Map<String, Object> params = mutableMap("inputSource", "passthrough");

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsKey("_triggerTimestamp");
    }

    @Test
    void nullInputDataProducesEmptyTriggerItem() {
        Map<String, Object> params = mutableMap("inputSource", "passthrough");

        NodeExecutionResult result = node.execute(ctx(null, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsKey("_triggerTimestamp");
    }

    // -- No inputs defined (trigger node) --

    @Test
    void hasNoInputs() {
        assertThat(node.getInputs()).isEmpty();
    }

    // -- Has one output --

    @Test
    void hasOneMainOutput() {
        assertThat(node.getOutputs()).hasSize(1);
    }
}
