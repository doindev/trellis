package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class SplitOutNodeTest {

    private SplitOutNode node;

    @BeforeEach
    void setUp() {
        node = new SplitOutNode();
    }

    // -- Split array field into individual items --

    @Test
    void splitArrayFieldIntoIndividualItems() {
        List<Map<String, Object>> input = items(
                mutableMap("name", "Alice", "tags", List.of(
                        mutableMap("label", "vip"),
                        mutableMap("label", "premium")
                ))
        );
        Map<String, Object> params = mutableMap(
                "fieldToSplit", "json.tags",
                "include", "noOtherFields"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(2);
        assertThat(jsonAt(result, 0)).containsEntry("label", "vip");
        assertThat(jsonAt(result, 1)).containsEntry("label", "premium");
    }

    // -- Split array of primitives --

    @Test
    void splitArrayOfPrimitivesIntoValueField() {
        List<Map<String, Object>> input = items(
                mutableMap("numbers", List.of(10, 20, 30))
        );
        Map<String, Object> params = mutableMap(
                "fieldToSplit", "json.numbers",
                "include", "noOtherFields"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(3);
        // Primitives are wrapped in a "value" key
        assertThat(jsonAt(result, 0)).containsEntry("value", 10);
        assertThat(jsonAt(result, 1)).containsEntry("value", 20);
        assertThat(jsonAt(result, 2)).containsEntry("value", 30);
    }

    // -- Include no other fields --

    @Test
    void includeNoOtherFields() {
        List<Map<String, Object>> input = items(
                mutableMap("name", "Alice", "age", 30, "items", List.of(
                        mutableMap("id", 1)
                ))
        );
        Map<String, Object> params = mutableMap(
                "fieldToSplit", "json.items",
                "include", "noOtherFields"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(jsonAt(result, 0)).containsEntry("id", 1);
        assertThat(jsonAt(result, 0)).doesNotContainKey("name");
        assertThat(jsonAt(result, 0)).doesNotContainKey("age");
    }

    // -- Include all other fields --

    @Test
    void includeAllOtherFields() {
        List<Map<String, Object>> input = items(
                mutableMap("name", "Alice", "age", 30, "items", List.of(
                        mutableMap("id", 1),
                        mutableMap("id", 2)
                ))
        );
        Map<String, Object> params = mutableMap(
                "fieldToSplit", "json.items",
                "include", "allOtherFields"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(2);
        // Each split item should include all other fields
        assertThat(jsonAt(result, 0)).containsEntry("name", "Alice");
        assertThat(jsonAt(result, 0)).containsEntry("age", 30);
        assertThat(jsonAt(result, 0)).containsEntry("id", 1);
        assertThat(jsonAt(result, 0)).doesNotContainKey("items"); // split field removed

        assertThat(jsonAt(result, 1)).containsEntry("name", "Alice");
        assertThat(jsonAt(result, 1)).containsEntry("age", 30);
        assertThat(jsonAt(result, 1)).containsEntry("id", 2);
    }

    // -- Include selected fields --

    @Test
    void includeSelectedFields() {
        List<Map<String, Object>> input = items(
                mutableMap("name", "Alice", "age", 30, "city", "NYC", "items", List.of(
                        mutableMap("id", 1)
                ))
        );
        Map<String, Object> params = mutableMap(
                "fieldToSplit", "json.items",
                "include", "selectedFields",
                "fieldsToInclude", "name, city"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(jsonAt(result, 0)).containsEntry("id", 1);
        assertThat(jsonAt(result, 0)).containsEntry("name", "Alice");
        assertThat(jsonAt(result, 0)).containsEntry("city", "NYC");
        assertThat(jsonAt(result, 0)).doesNotContainKey("age");
    }

    // -- Non-array field (pass through unchanged) --

    @Test
    void nonArrayFieldPassesThroughUnchanged() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice", "status", "active")
        );
        Map<String, Object> params = mutableMap(
                "fieldToSplit", "json.status",
                "include", "noOtherFields"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        // When field is not an array, the item is passed through as-is
        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("name", "Alice");
        assertThat(firstJson(result)).containsEntry("status", "active");
    }

    // -- Empty array --

    @Test
    void emptyArrayProducesNoItems() {
        List<Map<String, Object>> input = items(
                mutableMap("name", "Alice", "items", List.of())
        );
        Map<String, Object> params = mutableMap(
                "fieldToSplit", "json.items",
                "include", "noOtherFields"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        // Empty array = no output items for this input
        assertThat(output(result)).isEmpty();
    }

    // -- Missing field --

    @Test
    void missingFieldPassesThroughUnchanged() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice")
        );
        Map<String, Object> params = mutableMap(
                "fieldToSplit", "json.nonexistent",
                "include", "noOtherFields"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        // Missing field = getNestedValue returns null, not a List, so pass through
        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("name", "Alice");
    }

    // -- Empty input --

    @Test
    void emptyInputReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "fieldToSplit", "json.items",
                "include", "noOtherFields"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).isEmpty();
    }

    @Test
    void nullInputReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "fieldToSplit", "json.items",
                "include", "noOtherFields"
        );

        NodeExecutionResult result = node.execute(ctx(null, params));

        assertThat(output(result)).isEmpty();
    }

    // -- Multiple input items each with arrays --

    @Test
    void multipleInputItemsEachSplit() {
        List<Map<String, Object>> input = items(
                mutableMap("group", "A", "values", List.of(
                        mutableMap("v", 1), mutableMap("v", 2)
                )),
                mutableMap("group", "B", "values", List.of(
                        mutableMap("v", 3)
                ))
        );
        Map<String, Object> params = mutableMap(
                "fieldToSplit", "json.values",
                "include", "allOtherFields"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(3);
        assertThat(jsonAt(result, 0)).containsEntry("group", "A").containsEntry("v", 1);
        assertThat(jsonAt(result, 1)).containsEntry("group", "A").containsEntry("v", 2);
        assertThat(jsonAt(result, 2)).containsEntry("group", "B").containsEntry("v", 3);
    }

    // -- Blank field name returns error --

    @Test
    void blankFieldNameReturnsError() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice"));
        Map<String, Object> params = mutableMap(
                "fieldToSplit", "",
                "include", "noOtherFields"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getError()).isNotNull();
        assertThat(result.getError().getMessage()).contains("required");
    }
}
