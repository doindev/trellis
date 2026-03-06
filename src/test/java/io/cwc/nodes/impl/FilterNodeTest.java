package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class FilterNodeTest {

    private FilterNode node;

    @BeforeEach
    void setUp() {
        node = new FilterNode();
    }

    // ── Empty / no-condition edge cases ──

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

    @Test
    void noConditionsKeepsAllItems() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice"),
                Map.of("name", "Bob")
        );

        NodeExecutionResult result = node.execute(ctx(input, Map.of()));

        assertThat(output(result, 0)).hasSize(2);
        assertThat(output(result, 1)).isEmpty();
    }

    // ── String operations ──

    @Nested
    class StringOperations {

        @Test
        void stringEquals() {
            List<Map<String, Object>> input = items(
                    Map.of("status", "active"),
                    Map.of("status", "inactive")
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.status", "string", "equals", "active")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("status", "active");
            assertThat(output(result, 1)).hasSize(1);
            assertThat(jsonAt(result, 1, 0)).containsEntry("status", "inactive");
        }

        @Test
        void stringNotEquals() {
            List<Map<String, Object>> input = items(
                    Map.of("status", "active"),
                    Map.of("status", "inactive")
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.status", "string", "notEquals", "active")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("status", "inactive");
            assertThat(output(result, 1)).hasSize(1);
        }

        @Test
        void stringContains() {
            List<Map<String, Object>> input = items(
                    Map.of("email", "alice@example.com"),
                    Map.of("email", "bob@other.org")
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.email", "string", "contains", "example")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("email", "alice@example.com");
            assertThat(output(result, 1)).hasSize(1);
        }

        @Test
        void stringStartsWith() {
            List<Map<String, Object>> input = items(
                    Map.of("name", "Dr. Smith"),
                    Map.of("name", "John Doe")
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.name", "string", "startsWith", "dr.")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            // ignoreCase defaults to true so "Dr." matches "dr."
            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("name", "Dr. Smith");
        }

        @Test
        void stringEndsWith() {
            List<Map<String, Object>> input = items(
                    Map.of("file", "report.pdf"),
                    Map.of("file", "image.png")
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.file", "string", "endsWith", ".pdf")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("file", "report.pdf");
            assertThat(output(result, 1)).hasSize(1);
        }

        @Test
        void stringNotContains() {
            List<Map<String, Object>> input = items(
                    Map.of("msg", "hello world"),
                    Map.of("msg", "goodbye")
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.msg", "string", "notContains", "world")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("msg", "goodbye");
        }

        @Test
        void stringEqualsCaseSensitiveWhenIgnoreCaseDisabled() {
            List<Map<String, Object>> input = items(
                    Map.of("val", "Hello"),
                    Map.of("val", "hello")
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.val", "string", "equals", "Hello")),
                    "combineOperation", "and",
                    "ignoreCase", false
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("val", "Hello");
            assertThat(output(result, 1)).hasSize(1);
        }
    }

    // ── Number operations ──

    @Nested
    class NumberOperations {

        @Test
        void numberEquals() {
            List<Map<String, Object>> input = items(
                    Map.of("score", 100),
                    Map.of("score", 50)
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.score", "number", "equals", "100")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("score", 100);
        }

        @Test
        void numberGreaterThan() {
            List<Map<String, Object>> input = items(
                    Map.of("age", 25),
                    Map.of("age", 17),
                    Map.of("age", 30)
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.age", "number", "gt", "18")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(2);
            assertThat(output(result, 1)).hasSize(1);
            assertThat(jsonAt(result, 1, 0)).containsEntry("age", 17);
        }

        @Test
        void numberLessThan() {
            List<Map<String, Object>> input = items(
                    Map.of("price", 9.99),
                    Map.of("price", 49.99)
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.price", "number", "lt", "20")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("price", 9.99);
        }

        @Test
        void numberGreaterThanOrEqual() {
            List<Map<String, Object>> input = items(
                    Map.of("count", 5),
                    Map.of("count", 10),
                    Map.of("count", 3)
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.count", "number", "gte", "5")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(2);
            assertThat(output(result, 1)).hasSize(1);
        }

        @Test
        void numberLessThanOrEqual() {
            List<Map<String, Object>> input = items(
                    Map.of("count", 5),
                    Map.of("count", 10),
                    Map.of("count", 3)
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.count", "number", "lte", "5")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(2);
            assertThat(output(result, 1)).hasSize(1);
        }

        @Test
        void numberNotEquals() {
            List<Map<String, Object>> input = items(
                    Map.of("val", 0),
                    Map.of("val", 1)
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.val", "number", "notEquals", "0")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("val", 1);
        }
    }

    // ── Boolean operations ──

    @Nested
    class BooleanOperations {

        @Test
        void booleanIsTrue() {
            List<Map<String, Object>> input = items(
                    Map.of("active", true),
                    Map.of("active", false)
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.active", "boolean", "isTrue", "")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("active", true);
        }

        @Test
        void booleanIsFalse() {
            List<Map<String, Object>> input = items(
                    Map.of("active", true),
                    Map.of("active", false)
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.active", "boolean", "isFalse", "")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("active", false);
        }

        @Test
        void booleanEquals() {
            List<Map<String, Object>> input = items(
                    Map.of("flag", true),
                    Map.of("flag", false)
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.flag", "boolean", "equals", "true")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("flag", true);
        }
    }

    // ── Empty / not-empty operations ──

    @Nested
    class EmptyNotEmptyOperations {

        @Test
        void stringIsEmpty() {
            List<Map<String, Object>> input = items(
                    Map.of("name", ""),
                    Map.of("name", "Alice")
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.name", "string", "empty", "")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("name", "");
        }

        @Test
        void stringIsNotEmpty() {
            List<Map<String, Object>> input = items(
                    Map.of("name", ""),
                    Map.of("name", "Alice")
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.name", "string", "notEmpty", "")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("name", "Alice");
        }

        @Test
        void numberEmptyMeansNotExists() {
            // Build items where one has no "val" field at all
            Map<String, Object> item1 = new HashMap<>();
            item1.put("other", "x");
            Map<String, Object> wrapped1 = new HashMap<>();
            wrapped1.put("json", item1);

            Map<String, Object> item2 = new HashMap<>();
            item2.put("val", 42);
            Map<String, Object> wrapped2 = new HashMap<>();
            wrapped2.put("json", item2);

            List<Map<String, Object>> input = List.of(wrapped1, wrapped2);

            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.val", "number", "empty", "")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(output(result, 1)).hasSize(1);
        }
    }

    // ── Regex operations ──

    @Nested
    class RegexOperations {

        @Test
        void regexMatch() {
            List<Map<String, Object>> input = items(
                    Map.of("email", "test@example.com"),
                    Map.of("email", "not-an-email")
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.email", "string", "regex", "^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("email", "test@example.com");
        }

        @Test
        void regexNotMatch() {
            List<Map<String, Object>> input = items(
                    Map.of("code", "ABC-123"),
                    Map.of("code", "XYZ-999")
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.code", "string", "notRegex", "^ABC")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("code", "XYZ-999");
        }

        @Test
        void regexWithSlashFlagsSyntax() {
            List<Map<String, Object>> input = items(
                    Map.of("text", "Hello World"),
                    Map.of("text", "goodbye")
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.text", "string", "regex", "/hello/i")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("text", "Hello World");
        }
    }

    // ── Exists / Not Exists ──

    @Nested
    class ExistsOperations {

        @Test
        void existsPassesWhenFieldPresent() {
            List<Map<String, Object>> input = items(
                    Map.of("name", "Alice"),
                    Map.of("other", "value")
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.name", "string", "exists", "")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("name", "Alice");
        }

        @Test
        void notExistsPassesWhenFieldAbsent() {
            List<Map<String, Object>> input = items(
                    Map.of("name", "Alice"),
                    Map.of("other", "value")
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(condition("json.name", "string", "notExists", "")),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("other", "value");
        }
    }

    // ── AND / OR logic ──

    @Nested
    class CombineLogic {

        @Test
        void andLogicRequiresAllConditionsTrue() {
            List<Map<String, Object>> input = items(
                    Map.of("age", 25, "status", "active"),
                    Map.of("age", 25, "status", "inactive"),
                    Map.of("age", 15, "status", "active")
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(
                            condition("json.age", "number", "gt", "18"),
                            condition("json.status", "string", "equals", "active")
                    ),
                    "combineOperation", "and"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("age", 25);
            assertThat(jsonAt(result, 0, 0)).containsEntry("status", "active");
            assertThat(output(result, 1)).hasSize(2);
        }

        @Test
        void orLogicRequiresAtLeastOneConditionTrue() {
            List<Map<String, Object>> input = items(
                    Map.of("age", 25, "status", "active"),
                    Map.of("age", 15, "status", "inactive"),
                    Map.of("age", 15, "status", "active")
            );
            Map<String, Object> params = mutableMap(
                    "conditions", List.of(
                            condition("json.age", "number", "gt", "18"),
                            condition("json.status", "string", "equals", "active")
                    ),
                    "combineOperation", "or"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(2);
            assertThat(output(result, 1)).hasSize(1);
            assertThat(jsonAt(result, 1, 0)).containsEntry("age", 15);
            assertThat(jsonAt(result, 1, 0)).containsEntry("status", "inactive");
        }
    }

    // ── Two outputs verification ──

    @Test
    void keptAndDiscardedOutputsCorrectlyPopulated() {
        List<Map<String, Object>> input = items(
                Map.of("val", 1),
                Map.of("val", 2),
                Map.of("val", 3),
                Map.of("val", 4),
                Map.of("val", 5)
        );
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.val", "number", "gt", "3")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        // Output 0 = kept
        assertThat(output(result, 0)).hasSize(2);
        // Output 1 = discarded
        assertThat(output(result, 1)).hasSize(3);
    }

    @Test
    void resultAlwaysContinuesExecution() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        NodeExecutionResult result = node.execute(ctx(input, Map.of()));

        assertThat(result.isContinueExecution()).isTrue();
        assertThat(result.getError()).isNull();
    }
}
