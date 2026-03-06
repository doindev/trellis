package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class AggregateNodeTest {

    private AggregateNode node;

    @BeforeEach
    void setUp() {
        node = new AggregateNode();
    }

    // -- Aggregate individual fields --

    @Test
    void aggregateIndividualFieldsCollectsValues() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice", "score", 90),
                Map.of("name", "Bob", "score", 80),
                Map.of("name", "Charlie", "score", 70)
        );
        Map<String, Object> params = mutableMap(
                "aggregate", "aggregateIndividualFields",
                "fieldsToAggregate", List.of(
                        mutableMap("fieldToAggregate", "name", "renameField", "")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        Object nameValues = firstJson(result).get("name");
        assertThat(nameValues).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Object> names = (List<Object>) nameValues;
        assertThat(names).containsExactly("Alice", "Bob", "Charlie");
    }

    @Test
    void aggregateMultipleIndividualFields() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice", "score", 90),
                Map.of("name", "Bob", "score", 80)
        );
        Map<String, Object> params = mutableMap(
                "aggregate", "aggregateIndividualFields",
                "fieldsToAggregate", List.of(
                        mutableMap("fieldToAggregate", "name", "renameField", ""),
                        mutableMap("fieldToAggregate", "score", "renameField", "")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        @SuppressWarnings("unchecked")
        List<Object> names = (List<Object>) firstJson(result).get("name");
        @SuppressWarnings("unchecked")
        List<Object> scores = (List<Object>) firstJson(result).get("score");
        assertThat(names).containsExactly("Alice", "Bob");
        assertThat(scores).containsExactly(90, 80);
    }

    // -- Aggregate all item data --

    @Test
    void aggregateAllItemDataIntoDefaultField() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1, "name", "Alice"),
                Map.of("id", 2, "name", "Bob")
        );
        Map<String, Object> params = mutableMap(
                "aggregate", "aggregateAllItemData",
                "destinationFieldName", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        Object data = firstJson(result).get("data");
        assertThat(data).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) data;
        assertThat(dataList).hasSize(2);
        assertThat(dataList.get(0)).containsEntry("id", 1).containsEntry("name", "Alice");
        assertThat(dataList.get(1)).containsEntry("id", 2).containsEntry("name", "Bob");
    }

    // -- Keep only unique values --

    @Test
    void keepOnlyUniqueValues() {
        List<Map<String, Object>> input = items(
                Map.of("color", "red"),
                Map.of("color", "blue"),
                Map.of("color", "red"),
                Map.of("color", "green"),
                Map.of("color", "blue")
        );
        Map<String, Object> params = mutableMap(
                "aggregate", "aggregateIndividualFields",
                "fieldsToAggregate", List.of(
                        mutableMap("fieldToAggregate", "color", "renameField", "")
                ),
                "options", mutableMap("keepOnlyUnique", true)
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        @SuppressWarnings("unchecked")
        List<Object> colors = (List<Object>) firstJson(result).get("color");
        assertThat(colors).containsExactly("red", "blue", "green");
    }

    @Test
    void keepOnlyUniqueValuesFalseKeepsDuplicates() {
        List<Map<String, Object>> input = items(
                Map.of("color", "red"),
                Map.of("color", "red"),
                Map.of("color", "blue")
        );
        Map<String, Object> params = mutableMap(
                "aggregate", "aggregateIndividualFields",
                "fieldsToAggregate", List.of(
                        mutableMap("fieldToAggregate", "color", "renameField", "")
                ),
                "options", mutableMap("keepOnlyUnique", false)
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        List<Object> colors = (List<Object>) firstJson(result).get("color");
        assertThat(colors).containsExactly("red", "red", "blue");
    }

    // -- Rename aggregated field --

    @Test
    void renameAggregatedField() {
        List<Map<String, Object>> input = items(
                Map.of("email", "alice@test.com"),
                Map.of("email", "bob@test.com")
        );
        Map<String, Object> params = mutableMap(
                "aggregate", "aggregateIndividualFields",
                "fieldsToAggregate", List.of(
                        mutableMap("fieldToAggregate", "email", "renameField", "allEmails")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsKey("allEmails");
        assertThat(firstJson(result)).doesNotContainKey("email");
        @SuppressWarnings("unchecked")
        List<Object> emails = (List<Object>) firstJson(result).get("allEmails");
        assertThat(emails).containsExactly("alice@test.com", "bob@test.com");
    }

    // -- Custom destination field --

    @Test
    void customDestinationFieldForAllItemData() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1),
                Map.of("id", 2)
        );
        Map<String, Object> params = mutableMap(
                "aggregate", "aggregateAllItemData",
                "destinationFieldName", "results"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsKey("results");
        assertThat(firstJson(result)).doesNotContainKey("data");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) firstJson(result).get("results");
        assertThat(results).hasSize(2);
    }

    @Test
    void blankDestinationFieldDefaultsToData() {
        List<Map<String, Object>> input = items(Map.of("id", 1));
        Map<String, Object> params = mutableMap(
                "aggregate", "aggregateAllItemData",
                "destinationFieldName", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsKey("data");
    }

    // -- Empty input --

    @Test
    void emptyInputReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "aggregate", "aggregateIndividualFields",
                "fieldsToAggregate", List.of(
                        mutableMap("fieldToAggregate", "name", "renameField", "")
                )
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).isEmpty();
    }

    @Test
    void nullInputReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "aggregate", "aggregateAllItemData",
                "destinationFieldName", "data"
        );

        NodeExecutionResult result = node.execute(ctx(null, params));

        assertThat(output(result)).isEmpty();
    }

    // -- No fields specified in individual mode returns error --

    @Test
    void noFieldsSpecifiedReturnsError() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice"));
        Map<String, Object> params = mutableMap(
                "aggregate", "aggregateIndividualFields"
                // no "fieldsToAggregate" param
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getError()).isNotNull();
        assertThat(result.getError().getMessage()).contains("No fields specified");
    }

    // -- Null values in aggregated fields are excluded --

    @Test
    void nullValuesInFieldsAreExcluded() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice"),
                Map.of("other", "data"),  // no "name" field -> null
                Map.of("name", "Charlie")
        );
        Map<String, Object> params = mutableMap(
                "aggregate", "aggregateIndividualFields",
                "fieldsToAggregate", List.of(
                        mutableMap("fieldToAggregate", "name", "renameField", "")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        List<Object> names = (List<Object>) firstJson(result).get("name");
        // null values are skipped
        assertThat(names).containsExactly("Alice", "Charlie");
    }
}
