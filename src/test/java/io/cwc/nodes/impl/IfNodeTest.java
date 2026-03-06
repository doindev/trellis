package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class IfNodeTest {

    private IfNode node;

    @BeforeEach
    void setUp() {
        node = new IfNode();
    }

    // -- equals condition --

    @Test
    void equalsConditionTrue() {
        List<Map<String, Object>> input = items(Map.of("status", "active"));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.status", "equals", "active")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).hasSize(1); // true branch
        assertThat(output(result, 1)).isEmpty();   // false branch
    }

    @Test
    void equalsConditionFalse() {
        List<Map<String, Object>> input = items(Map.of("status", "inactive"));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.status", "equals", "active")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).isEmpty();   // true branch
        assertThat(output(result, 1)).hasSize(1);  // false branch
    }

    // -- notEquals condition --

    @Test
    void notEqualsConditionTrue() {
        List<Map<String, Object>> input = items(Map.of("status", "inactive"));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.status", "notEquals", "active")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).hasSize(1);
        assertThat(output(result, 1)).isEmpty();
    }

    @Test
    void notEqualsConditionFalse() {
        List<Map<String, Object>> input = items(Map.of("status", "active"));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.status", "notEquals", "active")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).hasSize(1);
    }

    // -- contains condition --

    @Test
    void containsConditionTrue() {
        List<Map<String, Object>> input = items(Map.of("message", "hello world"));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.message", "contains", "world")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).hasSize(1);
        assertThat(output(result, 1)).isEmpty();
    }

    @Test
    void containsConditionFalse() {
        List<Map<String, Object>> input = items(Map.of("message", "hello world"));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.message", "contains", "universe")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).hasSize(1);
    }

    // -- startsWith condition --

    @Test
    void startsWithConditionTrue() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice Smith"));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.name", "startsWith", "Alice")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).hasSize(1);
        assertThat(output(result, 1)).isEmpty();
    }

    @Test
    void startsWithConditionFalse() {
        List<Map<String, Object>> input = items(Map.of("name", "Bob Smith"));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.name", "startsWith", "Alice")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).hasSize(1);
    }

    // -- endsWith condition --

    @Test
    void endsWithConditionTrue() {
        List<Map<String, Object>> input = items(Map.of("email", "user@example.com"));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.email", "endsWith", "@example.com")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).hasSize(1);
        assertThat(output(result, 1)).isEmpty();
    }

    @Test
    void endsWithConditionFalse() {
        List<Map<String, Object>> input = items(Map.of("email", "user@other.com"));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.email", "endsWith", "@example.com")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).hasSize(1);
    }

    // -- greaterThan with numbers --

    @Test
    void greaterThanTrue() {
        List<Map<String, Object>> input = items(Map.of("score", 85));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.score", "greaterThan", "50")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).hasSize(1);
        assertThat(output(result, 1)).isEmpty();
    }

    @Test
    void greaterThanFalse() {
        List<Map<String, Object>> input = items(Map.of("score", 30));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.score", "greaterThan", "50")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).hasSize(1);
    }

    // -- lessThan with numbers --

    @Test
    void lessThanTrue() {
        List<Map<String, Object>> input = items(Map.of("score", 30));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.score", "lessThan", "50")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).hasSize(1);
        assertThat(output(result, 1)).isEmpty();
    }

    @Test
    void lessThanFalse() {
        List<Map<String, Object>> input = items(Map.of("score", 85));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.score", "lessThan", "50")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).hasSize(1);
    }

    // -- isEmpty --

    @Test
    void isEmptyWithEmptyString() {
        List<Map<String, Object>> input = items(Map.of("value", ""));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.value", "isEmpty", "")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).hasSize(1);
        assertThat(output(result, 1)).isEmpty();
    }

    @Test
    void isEmptyWithMissingField() {
        List<Map<String, Object>> input = items(Map.of("other", "data"));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.missing", "isEmpty", "")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        // Missing field resolves to null, so isEmpty is true
        assertThat(output(result, 0)).hasSize(1);
        assertThat(output(result, 1)).isEmpty();
    }

    @Test
    void isEmptyWithNonEmptyString() {
        List<Map<String, Object>> input = items(Map.of("value", "hello"));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.value", "isEmpty", "")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).hasSize(1);
    }

    // -- isNotEmpty --

    @Test
    void isNotEmptyWithValue() {
        List<Map<String, Object>> input = items(Map.of("value", "hello"));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.value", "isNotEmpty", "")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).hasSize(1);
        assertThat(output(result, 1)).isEmpty();
    }

    @Test
    void isNotEmptyWithEmptyString() {
        List<Map<String, Object>> input = items(Map.of("value", ""));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.value", "isNotEmpty", "")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).hasSize(1);
    }

    // -- regex --

    @Test
    void regexMatchTrue() {
        List<Map<String, Object>> input = items(Map.of("email", "alice@example.com"));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.email", "regex", "^[a-z]+@[a-z]+\\.[a-z]+$")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).hasSize(1);
        assertThat(output(result, 1)).isEmpty();
    }

    @Test
    void regexMatchFalse() {
        List<Map<String, Object>> input = items(Map.of("email", "not-an-email"));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.email", "regex", "^[a-z]+@[a-z]+\\.[a-z]+$")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).hasSize(1);
    }

    // -- isTrue --

    @Test
    void isTrueWithBooleanTrue() {
        List<Map<String, Object>> input = items(Map.of("active", true));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.active", "isTrue", "")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).hasSize(1);
        assertThat(output(result, 1)).isEmpty();
    }

    @Test
    void isTrueWithBooleanFalse() {
        List<Map<String, Object>> input = items(Map.of("active", false));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.active", "isTrue", "")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).hasSize(1);
    }

    // -- isFalse --

    @Test
    void isFalseWithBooleanFalse() {
        List<Map<String, Object>> input = items(Map.of("active", false));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.active", "isFalse", "")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).hasSize(1);
        assertThat(output(result, 1)).isEmpty();
    }

    @Test
    void isFalseWithBooleanTrue() {
        List<Map<String, Object>> input = items(Map.of("active", true));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.active", "isFalse", "")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).hasSize(1);
    }

    // -- AND combine (all must match) --

    @Test
    void andCombineAllMatch() {
        List<Map<String, Object>> input = items(Map.of("status", "active", "score", 80));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(
                        condition("json.status", "equals", "active"),
                        condition("json.score", "greaterThan", "50")
                ),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).hasSize(1);
        assertThat(output(result, 1)).isEmpty();
    }

    @Test
    void andCombineOneFails() {
        List<Map<String, Object>> input = items(Map.of("status", "inactive", "score", 80));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(
                        condition("json.status", "equals", "active"),
                        condition("json.score", "greaterThan", "50")
                ),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).hasSize(1);
    }

    // -- OR combine (any must match) --

    @Test
    void orCombineOneMatches() {
        List<Map<String, Object>> input = items(Map.of("status", "inactive", "score", 80));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(
                        condition("json.status", "equals", "active"),
                        condition("json.score", "greaterThan", "50")
                ),
                "combineOperation", "or"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).hasSize(1);
        assertThat(output(result, 1)).isEmpty();
    }

    @Test
    void orCombineNoneMatch() {
        List<Map<String, Object>> input = items(Map.of("status", "inactive", "score", 30));
        Map<String, Object> params = mutableMap(
                "conditions", List.of(
                        condition("json.status", "equals", "active"),
                        condition("json.score", "greaterThan", "50")
                ),
                "combineOperation", "or"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).hasSize(1);
    }

    // -- No conditions (everything passes) --

    @Test
    void noConditionsEverythingGoesToTrue() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1),
                Map.of("id", 2)
        );
        Map<String, Object> params = mutableMap(
                "combineOperation", "and"
                // no "conditions" parameter
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).hasSize(2);
        assertThat(output(result, 1)).isEmpty();
    }

    // -- Empty input --

    @Test
    void emptyInputReturnsTwoEmptyOutputs() {
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.x", "equals", "y")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).isEmpty();
    }

    @Test
    void nullInputReturnsTwoEmptyOutputs() {
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.x", "equals", "y")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(null, params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).isEmpty();
    }

    // -- Nested field reference with dot notation --

    @Test
    void nestedFieldReferenceWithDotNotation() {
        List<Map<String, Object>> input = items(
                mutableMap("user", mutableMap("role", "admin"))
        );
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.user.role", "equals", "admin")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).hasSize(1);
        assertThat(output(result, 1)).isEmpty();
    }

    @Test
    void nestedFieldReferenceNotMatching() {
        List<Map<String, Object>> input = items(
                mutableMap("user", mutableMap("role", "viewer"))
        );
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.user.role", "equals", "admin")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).hasSize(1);
    }

    // -- Multiple items split between branches --

    @Test
    void multipleItemsSplitBetweenBranches() {
        List<Map<String, Object>> input = items(
                Map.of("score", 90),
                Map.of("score", 30),
                Map.of("score", 70),
                Map.of("score", 10)
        );
        Map<String, Object> params = mutableMap(
                "conditions", List.of(condition("json.score", "greaterThan", "50")),
                "combineOperation", "and"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result, 0)).hasSize(2); // 90, 70
        assertThat(output(result, 1)).hasSize(2); // 30, 10
    }
}
