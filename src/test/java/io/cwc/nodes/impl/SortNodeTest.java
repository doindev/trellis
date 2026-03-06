package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class SortNodeTest {

    private SortNode node;

    @BeforeEach
    void setUp() {
        node = new SortNode();
    }

    // -- Sort ascending by string field --

    @Test
    void sortAscendingByStringField() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Charlie"),
                Map.of("name", "Alice"),
                Map.of("name", "Bob")
        );
        Map<String, Object> params = mutableMap(
                "type", "simple",
                "sortFieldsUi", List.of(
                        mutableMap("fieldName", "name", "order", "ascending")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(3);
        assertThat(jsonAt(result, 0)).containsEntry("name", "Alice");
        assertThat(jsonAt(result, 1)).containsEntry("name", "Bob");
        assertThat(jsonAt(result, 2)).containsEntry("name", "Charlie");
    }

    // -- Sort descending by string field --

    @Test
    void sortDescendingByStringField() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice"),
                Map.of("name", "Charlie"),
                Map.of("name", "Bob")
        );
        Map<String, Object> params = mutableMap(
                "type", "simple",
                "sortFieldsUi", List.of(
                        mutableMap("fieldName", "name", "order", "descending")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(3);
        assertThat(jsonAt(result, 0)).containsEntry("name", "Charlie");
        assertThat(jsonAt(result, 1)).containsEntry("name", "Bob");
        assertThat(jsonAt(result, 2)).containsEntry("name", "Alice");
    }

    // -- Sort by numeric field --

    @Test
    void sortAscendingByNumericField() {
        List<Map<String, Object>> input = items(
                Map.of("score", 75),
                Map.of("score", 30),
                Map.of("score", 90),
                Map.of("score", 50)
        );
        Map<String, Object> params = mutableMap(
                "type", "simple",
                "sortFieldsUi", List.of(
                        mutableMap("fieldName", "score", "order", "ascending")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(4);
        assertThat(jsonAt(result, 0)).containsEntry("score", 30);
        assertThat(jsonAt(result, 1)).containsEntry("score", 50);
        assertThat(jsonAt(result, 2)).containsEntry("score", 75);
        assertThat(jsonAt(result, 3)).containsEntry("score", 90);
    }

    @Test
    void sortDescendingByNumericField() {
        List<Map<String, Object>> input = items(
                Map.of("score", 75),
                Map.of("score", 30),
                Map.of("score", 90)
        );
        Map<String, Object> params = mutableMap(
                "type", "simple",
                "sortFieldsUi", List.of(
                        mutableMap("fieldName", "score", "order", "descending")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(3);
        assertThat(jsonAt(result, 0)).containsEntry("score", 90);
        assertThat(jsonAt(result, 1)).containsEntry("score", 75);
        assertThat(jsonAt(result, 2)).containsEntry("score", 30);
    }

    // -- Sort by multiple fields --

    @Test
    void sortByMultipleFields() {
        List<Map<String, Object>> input = items(
                Map.of("department", "Sales", "name", "Charlie"),
                Map.of("department", "Engineering", "name", "Bob"),
                Map.of("department", "Sales", "name", "Alice"),
                Map.of("department", "Engineering", "name", "Alice")
        );
        Map<String, Object> params = mutableMap(
                "type", "simple",
                "sortFieldsUi", List.of(
                        mutableMap("fieldName", "department", "order", "ascending"),
                        mutableMap("fieldName", "name", "order", "ascending")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(4);
        // Engineering comes before Sales
        assertThat(jsonAt(result, 0)).containsEntry("department", "Engineering").containsEntry("name", "Alice");
        assertThat(jsonAt(result, 1)).containsEntry("department", "Engineering").containsEntry("name", "Bob");
        assertThat(jsonAt(result, 2)).containsEntry("department", "Sales").containsEntry("name", "Alice");
        assertThat(jsonAt(result, 3)).containsEntry("department", "Sales").containsEntry("name", "Charlie");
    }

    // -- Random sort --

    @Test
    void randomSortContainsAllItems() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1),
                Map.of("id", 2),
                Map.of("id", 3),
                Map.of("id", 4),
                Map.of("id", 5)
        );
        Map<String, Object> params = mutableMap("type", "random");

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(5);

        // Verify all items are present regardless of order
        Set<Object> ids = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            ids.add(jsonAt(result, i).get("id"));
        }
        assertThat(ids).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
    }

    // -- Null value handling --

    @Test
    void nullValuesAreSortedLast() {
        // Items where some have the sort field and some don't
        List<Map<String, Object>> input = items(
                Map.of("name", "Charlie"),
                Map.of("other", "no-name"),   // "name" field is missing -> null
                Map.of("name", "Alice")
        );
        Map<String, Object> params = mutableMap(
                "type", "simple",
                "sortFieldsUi", List.of(
                        mutableMap("fieldName", "name", "order", "ascending")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(3);
        // Null sorts last; Alice and Charlie come first
        assertThat(jsonAt(result, 0)).containsEntry("name", "Alice");
        assertThat(jsonAt(result, 1)).containsEntry("name", "Charlie");
        // The third item has no "name" key
        assertThat(jsonAt(result, 2)).doesNotContainKey("name");
    }

    // -- Empty input --

    @Test
    void emptyInputReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "type", "simple",
                "sortFieldsUi", List.of(
                        mutableMap("fieldName", "name", "order", "ascending")
                )
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).isEmpty();
    }

    @Test
    void nullInputReturnsEmpty() {
        Map<String, Object> params = mutableMap("type", "simple");

        NodeExecutionResult result = node.execute(ctx(null, params));

        assertThat(output(result)).isEmpty();
    }

    // -- Single item input --

    @Test
    void singleItemInputReturnsSameItem() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice"));
        Map<String, Object> params = mutableMap(
                "type", "simple",
                "sortFieldsUi", List.of(
                        mutableMap("fieldName", "name", "order", "ascending")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("name", "Alice");
    }

    // -- No sort fields specified returns items unchanged --

    @Test
    void noSortFieldsReturnsUnchanged() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Charlie"),
                Map.of("name", "Alice")
        );
        Map<String, Object> params = mutableMap(
                "type", "simple",
                "sortFieldsUi", List.of()
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(2);
        // Order should remain unchanged
        assertThat(jsonAt(result, 0)).containsEntry("name", "Charlie");
        assertThat(jsonAt(result, 1)).containsEntry("name", "Alice");
    }
}
