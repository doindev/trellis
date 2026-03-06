package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class RemoveDuplicatesNodeTest {

    private RemoveDuplicatesNode node;

    @BeforeEach
    void setUp() {
        node = new RemoveDuplicatesNode();
    }

    // ── Empty input ──

    @Test
    void emptyInputReturnsTwoEmptyOutputs() {
        Map<String, Object> params = mutableMap(
                "operation", "removeDuplicateInputItems",
                "compare", "allFields"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).isEmpty();
    }

    @Test
    void nullInputReturnsTwoEmptyOutputs() {
        Map<String, Object> params = mutableMap(
                "operation", "removeDuplicateInputItems",
                "compare", "allFields"
        );

        NodeExecutionResult result = node.execute(ctx(null, params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).isEmpty();
    }

    // ── Remove duplicates comparing all fields ──

    @Nested
    class AllFields {

        @Test
        void removesExactDuplicates() {
            List<Map<String, Object>> input = items(
                    Map.of("name", "Alice", "age", 30),
                    Map.of("name", "Alice", "age", 30),
                    Map.of("name", "Bob", "age", 25)
            );
            Map<String, Object> params = mutableMap(
                    "operation", "removeDuplicateInputItems",
                    "compare", "allFields"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            // Output 0 = kept (unique)
            assertThat(output(result, 0)).hasSize(2);
            // Output 1 = duplicates
            assertThat(output(result, 1)).hasSize(1);
        }

        @Test
        void noDuplicatesKeepsAll() {
            List<Map<String, Object>> input = items(
                    Map.of("id", 1),
                    Map.of("id", 2),
                    Map.of("id", 3)
            );
            Map<String, Object> params = mutableMap(
                    "operation", "removeDuplicateInputItems",
                    "compare", "allFields"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(3);
            assertThat(output(result, 1)).isEmpty();
        }

        @Test
        void allDuplicatesKeepsFirstOnly() {
            List<Map<String, Object>> input = items(
                    Map.of("val", "x"),
                    Map.of("val", "x"),
                    Map.of("val", "x")
            );
            Map<String, Object> params = mutableMap(
                    "operation", "removeDuplicateInputItems",
                    "compare", "allFields"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(output(result, 1)).hasSize(2);
        }

        @Test
        void differentFieldsAreNotDuplicates() {
            List<Map<String, Object>> input = items(
                    Map.of("name", "Alice", "age", 30),
                    Map.of("name", "Alice", "age", 31)
            );
            Map<String, Object> params = mutableMap(
                    "operation", "removeDuplicateInputItems",
                    "compare", "allFields"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(2);
            assertThat(output(result, 1)).isEmpty();
        }
    }

    // ── Remove duplicates by selected fields ──

    @Nested
    class SelectedFields {

        @Test
        void deduplicatesBySelectedFieldsOnly() {
            List<Map<String, Object>> input = items(
                    Map.of("email", "a@b.com", "name", "Alice"),
                    Map.of("email", "a@b.com", "name", "Alice2"),
                    Map.of("email", "c@d.com", "name", "Charlie")
            );
            Map<String, Object> params = mutableMap(
                    "operation", "removeDuplicateInputItems",
                    "compare", "selectedFields",
                    "fieldsToCompare", "email"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            // Two unique emails
            assertThat(output(result, 0)).hasSize(2);
            // One duplicate (second a@b.com)
            assertThat(output(result, 1)).hasSize(1);
        }

        @Test
        void multipleSelectedFields() {
            List<Map<String, Object>> input = items(
                    Map.of("first", "Alice", "last", "Smith", "age", 30),
                    Map.of("first", "Alice", "last", "Smith", "age", 25),
                    Map.of("first", "Alice", "last", "Jones", "age", 30)
            );
            Map<String, Object> params = mutableMap(
                    "operation", "removeDuplicateInputItems",
                    "compare", "selectedFields",
                    "fieldsToCompare", "first, last"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            // "Alice Smith" appears twice but is deduped, "Alice Jones" is different
            assertThat(output(result, 0)).hasSize(2);
            assertThat(output(result, 1)).hasSize(1);
        }
    }

    // ── Remove duplicates by all fields except specified ──

    @Nested
    class AllFieldsExcept {

        @Test
        void excludesSpecifiedFieldsFromComparison() {
            List<Map<String, Object>> input = items(
                    Map.of("id", 1, "name", "Alice", "timestamp", "t1"),
                    Map.of("id", 2, "name", "Alice", "timestamp", "t2"),
                    Map.of("id", 3, "name", "Bob", "timestamp", "t3")
            );
            Map<String, Object> params = mutableMap(
                    "operation", "removeDuplicateInputItems",
                    "compare", "allFieldsExcept",
                    "fieldsToExclude", "id, timestamp"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            // After excluding id and timestamp, items 1 and 2 are both {name: Alice}
            assertThat(output(result, 0)).hasSize(2);
            assertThat(output(result, 1)).hasSize(1);
        }

        @Test
        void excludeNonExistentFieldDoesNotAffectComparison() {
            List<Map<String, Object>> input = items(
                    Map.of("name", "Alice"),
                    Map.of("name", "Alice")
            );
            Map<String, Object> params = mutableMap(
                    "operation", "removeDuplicateInputItems",
                    "compare", "allFieldsExcept",
                    "fieldsToExclude", "nonexistent"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(output(result, 1)).hasSize(1);
        }
    }

    // ── Two outputs verification ──

    @Test
    void keptAndDuplicatesOutputsCorrectlyPopulated() {
        List<Map<String, Object>> input = items(
                Map.of("x", 1),
                Map.of("x", 2),
                Map.of("x", 1),
                Map.of("x", 3),
                Map.of("x", 2)
        );
        Map<String, Object> params = mutableMap(
                "operation", "removeDuplicateInputItems",
                "compare", "allFields"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        // Output 0 = kept
        assertThat(output(result, 0)).hasSize(3);
        // Output 1 = duplicates
        assertThat(output(result, 1)).hasSize(2);
    }

    @Test
    void defaultOperationIsRemoveDuplicateInputItems() {
        List<Map<String, Object>> input = items(
                Map.of("a", 1),
                Map.of("a", 1)
        );

        NodeExecutionResult result = node.execute(ctx(input, Map.of()));

        assertThat(output(result, 0)).hasSize(1);
        assertThat(output(result, 1)).hasSize(1);
    }

    @Test
    void singleItemAlwaysKept() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice")
        );
        Map<String, Object> params = mutableMap(
                "operation", "removeDuplicateInputItems",
                "compare", "allFields"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).hasSize(1);
        assertThat(output(result, 1)).isEmpty();
    }
}
