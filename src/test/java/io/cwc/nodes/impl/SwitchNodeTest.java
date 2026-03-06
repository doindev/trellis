package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class SwitchNodeTest {

    private SwitchNode node;

    @BeforeEach
    void setUp() {
        node = new SwitchNode();
    }

    // ── Rules mode ──

    @Nested
    class RulesMode {

        @Test
        void singleRuleRoutesMatchingItems() {
            List<Map<String, Object>> input = items(
                    Map.of("status", "active"),
                    Map.of("status", "inactive")
            );
            Map<String, Object> params = mutableMap(
                    "mode", "rules",
                    "rules", List.of(
                            mutableMap("value1", "json.status", "operation", "equals", "value2", "active")
                    ),
                    "fallbackOutput", "none"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            // Output 0 gets items matching the rule
            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("status", "active");
        }

        @Test
        void multipleRulesRouteToSeparateOutputs() {
            List<Map<String, Object>> input = items(
                    Map.of("color", "red"),
                    Map.of("color", "blue"),
                    Map.of("color", "green")
            );
            Map<String, Object> params = mutableMap(
                    "mode", "rules",
                    "rules", List.of(
                            mutableMap("value1", "json.color", "operation", "equals", "value2", "red"),
                            mutableMap("value1", "json.color", "operation", "equals", "value2", "blue")
                    ),
                    "fallbackOutput", "none"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("color", "red");
            assertThat(output(result, 1)).hasSize(1);
            assertThat(jsonAt(result, 1, 0)).containsEntry("color", "blue");
            // "green" matches no rule and fallback is "none", so it is dropped
        }

        @Test
        void firstMatchOnlyIsDefault() {
            List<Map<String, Object>> input = items(
                    Map.of("val", "ab")
            );
            // Both rules could match ("ab" contains "a" and contains "b")
            Map<String, Object> params = mutableMap(
                    "mode", "rules",
                    "rules", List.of(
                            mutableMap("value1", "json.val", "operation", "contains", "value2", "a"),
                            mutableMap("value1", "json.val", "operation", "contains", "value2", "b")
                    ),
                    "fallbackOutput", "none",
                    "allMatchingOutputs", false
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            // Item routes to first matching rule only
            assertThat(output(result, 0)).hasSize(1);
            assertThat(output(result, 1)).isEmpty();
        }

        @Test
        void allMatchingOutputsSendsToEveryMatch() {
            List<Map<String, Object>> input = items(
                    Map.of("val", "ab")
            );
            Map<String, Object> params = mutableMap(
                    "mode", "rules",
                    "rules", List.of(
                            mutableMap("value1", "json.val", "operation", "contains", "value2", "a"),
                            mutableMap("value1", "json.val", "operation", "contains", "value2", "b")
                    ),
                    "fallbackOutput", "none",
                    "allMatchingOutputs", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(output(result, 1)).hasSize(1);
        }

        @Test
        void fallbackExtraOutputCapturesUnmatched() {
            List<Map<String, Object>> input = items(
                    Map.of("type", "A"),
                    Map.of("type", "B"),
                    Map.of("type", "C")
            );
            Map<String, Object> params = mutableMap(
                    "mode", "rules",
                    "rules", List.of(
                            mutableMap("value1", "json.type", "operation", "equals", "value2", "A")
                    ),
                    "fallbackOutput", "extra"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            // Output 0 = rule match
            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("type", "A");
            // Output 1 = fallback (extra)
            assertThat(output(result, 1)).hasSize(2);
        }

        @Test
        void fallbackNoneDropsUnmatched() {
            List<Map<String, Object>> input = items(
                    Map.of("type", "A"),
                    Map.of("type", "X")
            );
            Map<String, Object> params = mutableMap(
                    "mode", "rules",
                    "rules", List.of(
                            mutableMap("value1", "json.type", "operation", "equals", "value2", "A")
                    ),
                    "fallbackOutput", "none"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            // No fallback output; only 1 output bucket for the single rule
        }

        @Test
        void containsOperation() {
            List<Map<String, Object>> input = items(
                    Map.of("name", "hello world"),
                    Map.of("name", "goodbye")
            );
            Map<String, Object> params = mutableMap(
                    "mode", "rules",
                    "rules", List.of(
                            mutableMap("value1", "json.name", "operation", "contains", "value2", "hello")
                    ),
                    "fallbackOutput", "none"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("name", "hello world");
        }

        @Test
        void startsWithOperation() {
            List<Map<String, Object>> input = items(
                    Map.of("url", "https://example.com"),
                    Map.of("url", "http://other.com")
            );
            Map<String, Object> params = mutableMap(
                    "mode", "rules",
                    "rules", List.of(
                            mutableMap("value1", "json.url", "operation", "startsWith", "value2", "https")
                    ),
                    "fallbackOutput", "none"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("url", "https://example.com");
        }

        @Test
        void endsWithOperation() {
            List<Map<String, Object>> input = items(
                    Map.of("file", "doc.pdf"),
                    Map.of("file", "img.png")
            );
            Map<String, Object> params = mutableMap(
                    "mode", "rules",
                    "rules", List.of(
                            mutableMap("value1", "json.file", "operation", "endsWith", "value2", ".pdf")
                    ),
                    "fallbackOutput", "none"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("file", "doc.pdf");
        }

        @Test
        void greaterThanOperation() {
            List<Map<String, Object>> input = items(
                    Map.of("score", 80),
                    Map.of("score", 40)
            );
            Map<String, Object> params = mutableMap(
                    "mode", "rules",
                    "rules", List.of(
                            mutableMap("value1", "json.score", "operation", "greaterThan", "value2", "50")
                    ),
                    "fallbackOutput", "none"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("score", 80);
        }

        @Test
        void lessThanOperation() {
            List<Map<String, Object>> input = items(
                    Map.of("score", 80),
                    Map.of("score", 40)
            );
            Map<String, Object> params = mutableMap(
                    "mode", "rules",
                    "rules", List.of(
                            mutableMap("value1", "json.score", "operation", "lessThan", "value2", "50")
                    ),
                    "fallbackOutput", "none"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("score", 40);
        }

        @Test
        void regexOperation() {
            List<Map<String, Object>> input = items(
                    Map.of("code", "ABC-123"),
                    Map.of("code", "invalid")
            );
            Map<String, Object> params = mutableMap(
                    "mode", "rules",
                    "rules", List.of(
                            mutableMap("value1", "json.code", "operation", "regex", "value2", "^[A-Z]+-\\d+$")
                    ),
                    "fallbackOutput", "none"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("code", "ABC-123");
        }

        @Test
        void isEmptyOperation() {
            Map<String, Object> item1 = new java.util.HashMap<>();
            item1.put("json", new java.util.HashMap<>(Map.of("name", "")));
            Map<String, Object> item2 = new java.util.HashMap<>();
            item2.put("json", new java.util.HashMap<>(Map.of("name", "Alice")));

            List<Map<String, Object>> input = List.of(item1, item2);
            Map<String, Object> params = mutableMap(
                    "mode", "rules",
                    "rules", List.of(
                            mutableMap("value1", "json.name", "operation", "isEmpty", "value2", "")
                    ),
                    "fallbackOutput", "none"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("name", "");
        }

        @Test
        void isNotEmptyOperation() {
            List<Map<String, Object>> input = items(
                    Map.of("name", ""),
                    Map.of("name", "Alice")
            );
            Map<String, Object> params = mutableMap(
                    "mode", "rules",
                    "rules", List.of(
                            mutableMap("value1", "json.name", "operation", "isNotEmpty", "value2", "")
                    ),
                    "fallbackOutput", "none"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("name", "Alice");
        }

        @Test
        void notEqualsOperation() {
            List<Map<String, Object>> input = items(
                    Map.of("status", "ok"),
                    Map.of("status", "error")
            );
            Map<String, Object> params = mutableMap(
                    "mode", "rules",
                    "rules", List.of(
                            mutableMap("value1", "json.status", "operation", "notEquals", "value2", "ok")
                    ),
                    "fallbackOutput", "none"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("status", "error");
        }
    }

    // ── Expression mode ──

    @Nested
    class ExpressionMode {

        @Test
        void routesByFieldValueIndex() {
            List<Map<String, Object>> input = items(
                    Map.of("outputIndex", 0),
                    Map.of("outputIndex", 1),
                    Map.of("outputIndex", 2),
                    Map.of("outputIndex", 1)
            );
            Map<String, Object> params = mutableMap(
                    "mode", "expression",
                    "expression", "json.outputIndex",
                    "numberOutputs", 4
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(output(result, 1)).hasSize(2);
            assertThat(output(result, 2)).hasSize(1);
            assertThat(output(result, 3)).isEmpty();
        }

        @Test
        void outOfRangeIndexClampedToLastOutput() {
            List<Map<String, Object>> input = items(
                    Map.of("idx", 99)
            );
            Map<String, Object> params = mutableMap(
                    "mode", "expression",
                    "expression", "json.idx",
                    "numberOutputs", 3
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            // index 99 clamped to 2 (numOutputs - 1)
            assertThat(output(result, 0)).isEmpty();
            assertThat(output(result, 1)).isEmpty();
            assertThat(output(result, 2)).hasSize(1);
        }

        @Test
        void negativeIndexClampedToZero() {
            List<Map<String, Object>> input = items(
                    Map.of("idx", -5)
            );
            Map<String, Object> params = mutableMap(
                    "mode", "expression",
                    "expression", "json.idx",
                    "numberOutputs", 3
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
        }

        @Test
        void nullExpressionFieldRoutesToOutputZero() {
            List<Map<String, Object>> input = items(
                    Map.of("other", "value")
            );
            Map<String, Object> params = mutableMap(
                    "mode", "expression",
                    "expression", "json.missing",
                    "numberOutputs", 3
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
        }

        @Test
        void emptyExpressionRoutesAllToOutputZero() {
            List<Map<String, Object>> input = items(
                    Map.of("x", 1),
                    Map.of("x", 2)
            );
            Map<String, Object> params = mutableMap(
                    "mode", "expression",
                    "expression", "",
                    "numberOutputs", 3
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(2);
        }
    }

    // ── Empty input ──

    @Test
    void emptyInputRulesModeReturnsEmptyOutputs() {
        Map<String, Object> params = mutableMap(
                "mode", "rules",
                "rules", List.of(
                        mutableMap("value1", "json.x", "operation", "equals", "value2", "y")
                ),
                "fallbackOutput", "none"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result, 0)).isEmpty();
    }

    @Test
    void emptyInputExpressionModeReturnsEmptyOutputs() {
        Map<String, Object> params = mutableMap(
                "mode", "expression",
                "expression", "json.idx",
                "numberOutputs", 3
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).isEmpty();
        assertThat(output(result, 2)).isEmpty();
    }

    @Test
    void resultAlwaysContinuesExecution() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap("mode", "rules",
                "rules", List.of(),
                "fallbackOutput", "none");

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.isContinueExecution()).isTrue();
        assertThat(result.getError()).isNull();
    }
}
