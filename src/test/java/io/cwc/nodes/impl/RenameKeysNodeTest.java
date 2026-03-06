package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class RenameKeysNodeTest {

    private RenameKeysNode node;

    @BeforeEach
    void setUp() {
        node = new RenameKeysNode();
    }

    // ── Empty / null input ──

    @Test
    void emptyInputReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "keys", List.of(
                        mutableMap("currentKey", "old", "newKey", "new")
                )
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).isEmpty();
    }

    @Test
    void nullInputReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "keys", List.of(
                        mutableMap("currentKey", "old", "newKey", "new")
                )
        );

        NodeExecutionResult result = node.execute(ctx(null, params));

        assertThat(output(result)).isEmpty();
    }

    // ── Single key rename ──

    @Test
    void renameSingleKey() {
        List<Map<String, Object>> input = items(Map.of("oldName", "value1", "other", "value2"));
        Map<String, Object> params = mutableMap(
                "keys", List.of(
                        mutableMap("currentKey", "oldName", "newKey", "newName")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("newName", "value1");
        assertThat(firstJson(result)).doesNotContainKey("oldName");
        assertThat(firstJson(result)).containsEntry("other", "value2");
    }

    // ── Multiple key renames ──

    @Test
    void renameMultipleKeys() {
        List<Map<String, Object>> input = items(Map.of("firstName", "Alice", "lastName", "Smith", "age", 30));
        Map<String, Object> params = mutableMap(
                "keys", List.of(
                        mutableMap("currentKey", "firstName", "newKey", "first_name"),
                        mutableMap("currentKey", "lastName", "newKey", "last_name")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("first_name", "Alice");
        assertThat(firstJson(result)).containsEntry("last_name", "Smith");
        assertThat(firstJson(result)).containsEntry("age", 30);
        assertThat(firstJson(result)).doesNotContainKey("firstName");
        assertThat(firstJson(result)).doesNotContainKey("lastName");
    }

    // ── Key not found is ignored silently ──

    @Test
    void renameNonExistentKeyNoError() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice"));
        Map<String, Object> params = mutableMap(
                "keys", List.of(
                        mutableMap("currentKey", "missingKey", "newKey", "newKey")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("name", "Alice");
        assertThat(firstJson(result)).doesNotContainKey("missingKey");
        assertThat(firstJson(result)).doesNotContainKey("newKey");
    }

    // ── Keys parameter as Map with "values" key ──

    @Test
    void keysParameterAsMapWithValuesKey() {
        List<Map<String, Object>> input = items(Map.of("oldField", "data"));
        Map<String, Object> params = mutableMap(
                "keys", mutableMap(
                        "values", List.of(
                                mutableMap("currentKey", "oldField", "newKey", "newField")
                        )
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("newField", "data");
        assertThat(firstJson(result)).doesNotContainKey("oldField");
    }

    // ── Regex-based renaming ──

    @Test
    void regexBasedRenaming() {
        List<Map<String, Object>> input = items(Map.of("old_name", "Alice", "old_age", 30, "status", "active"));
        Map<String, Object> params = mutableMap(
                "additionalOptions", mutableMap(
                        "regexReplacement", List.of(
                                mutableMap("searchRegex", "^old_", "replaceRegex", "new_", "caseInsensitive", false, "maxDepth", -1)
                        )
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("new_name", "Alice");
        assertThat(firstJson(result)).containsEntry("new_age", 30);
        assertThat(firstJson(result)).containsEntry("status", "active");
        assertThat(firstJson(result)).doesNotContainKey("old_name");
        assertThat(firstJson(result)).doesNotContainKey("old_age");
    }

    // ── Regex with capture groups ──

    @Test
    void regexRenamingWithCaptureGroups() {
        List<Map<String, Object>> input = items(Map.of("user_name", "Alice", "user_email", "alice@test.com"));
        Map<String, Object> params = mutableMap(
                "additionalOptions", mutableMap(
                        "regexReplacement", List.of(
                                mutableMap("searchRegex", "^user_(.*)", "replaceRegex", "$1", "caseInsensitive", false, "maxDepth", -1)
                        )
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("name", "Alice");
        assertThat(firstJson(result)).containsEntry("email", "alice@test.com");
    }

    // ── Case insensitive regex ──

    @Test
    void regexRenamingCaseInsensitive() {
        List<Map<String, Object>> input = items(Map.of("OLD_name", "Alice", "old_age", 30));
        Map<String, Object> params = mutableMap(
                "additionalOptions", mutableMap(
                        "regexReplacement", List.of(
                                mutableMap("searchRegex", "^old_", "replaceRegex", "new_", "caseInsensitive", true, "maxDepth", -1)
                        )
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("new_name", "Alice");
        assertThat(firstJson(result)).containsEntry("new_age", 30);
    }

    // ── maxDepth 0 only renames top-level keys ──

    @Test
    void regexMaxDepthZeroTopLevelOnly() {
        Map<String, Object> nested = mutableMap("old_inner", "nestedVal");
        List<Map<String, Object>> input = items(mutableMap("old_outer", "topVal", "nested", nested));
        Map<String, Object> params = mutableMap(
                "additionalOptions", mutableMap(
                        "regexReplacement", List.of(
                                mutableMap("searchRegex", "^old_", "replaceRegex", "new_", "caseInsensitive", false, "maxDepth", 0)
                        )
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("new_outer", "topVal");
        // Nested map is not renamed because maxDepth=0
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedResult = (Map<String, Object>) firstJson(result).get("nested");
        assertThat(nestedResult).containsKey("old_inner");
    }

    // ── Nested renaming with regex (unlimited depth) ──

    @Test
    void regexNestedRenamingUnlimitedDepth() {
        Map<String, Object> deepNested = mutableMap("old_deep", "deepVal");
        Map<String, Object> nested = mutableMap("old_inner", "nestedVal", "child", deepNested);
        List<Map<String, Object>> input = items(mutableMap("old_outer", "topVal", "nested", nested));
        Map<String, Object> params = mutableMap(
                "additionalOptions", mutableMap(
                        "regexReplacement", List.of(
                                mutableMap("searchRegex", "^old_", "replaceRegex", "new_", "caseInsensitive", false, "maxDepth", -1)
                        )
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("new_outer", "topVal");
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedResult = (Map<String, Object>) firstJson(result).get("nested");
        assertThat(nestedResult).containsEntry("new_inner", "nestedVal");
        @SuppressWarnings("unchecked")
        Map<String, Object> deepResult = (Map<String, Object>) nestedResult.get("child");
        assertThat(deepResult).containsEntry("new_deep", "deepVal");
    }

    // ── Multiple items all get renamed ──

    @Test
    void renameMultipleItems() {
        List<Map<String, Object>> input = items(
                Map.of("old", "alice"),
                Map.of("old", "bob"),
                Map.of("old", "charlie")
        );
        Map<String, Object> params = mutableMap(
                "keys", List.of(
                        mutableMap("currentKey", "old", "newKey", "new")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(3);
        assertThat(jsonAt(result, 0)).containsEntry("new", "alice");
        assertThat(jsonAt(result, 1)).containsEntry("new", "bob");
        assertThat(jsonAt(result, 2)).containsEntry("new", "charlie");
        assertThat(jsonAt(result, 0)).doesNotContainKey("old");
        assertThat(jsonAt(result, 1)).doesNotContainKey("old");
        assertThat(jsonAt(result, 2)).doesNotContainKey("old");
    }

    // ── Both direct and regex renaming combined ──

    @Test
    void directAndRegexRenamingCombined() {
        List<Map<String, Object>> input = items(Map.of("x_val", "A", "y_val", "B", "special", "C"));
        Map<String, Object> params = mutableMap(
                "keys", List.of(
                        mutableMap("currentKey", "special", "newKey", "renamed_special")
                ),
                "additionalOptions", mutableMap(
                        "regexReplacement", List.of(
                                mutableMap("searchRegex", "_val$", "replaceRegex", "_value", "caseInsensitive", false, "maxDepth", -1)
                        )
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("x_value", "A");
        assertThat(firstJson(result)).containsEntry("y_value", "B");
        assertThat(firstJson(result)).containsEntry("renamed_special", "C");
    }

    // ── No rename rules preserves data unchanged ──

    @Test
    void noRenameRulesPreservesData() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice", "age", 30));
        Map<String, Object> params = mutableMap();

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("name", "Alice");
        assertThat(firstJson(result)).containsEntry("age", 30);
    }

    // ── Regex replacement via Map with "values" key ──

    @Test
    void regexReplacementAsMapWithValuesKey() {
        List<Map<String, Object>> input = items(Map.of("prefix_name", "Alice"));
        Map<String, Object> params = mutableMap(
                "additionalOptions", mutableMap(
                        "regexReplacement", mutableMap(
                                "values", List.of(
                                        mutableMap("searchRegex", "^prefix_", "replaceRegex", "", "caseInsensitive", false, "maxDepth", -1)
                                )
                        )
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("name", "Alice");
        assertThat(firstJson(result)).doesNotContainKey("prefix_name");
    }

    // ── Blank currentKey or newKey is ignored ──

    @Test
    void blankKeysAreIgnored() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice"));
        Map<String, Object> params = mutableMap(
                "keys", List.of(
                        mutableMap("currentKey", "", "newKey", "newName"),
                        mutableMap("currentKey", "name", "newKey", "")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        // Neither rule should apply since one has blank currentKey and the other has blank newKey
        assertThat(firstJson(result)).containsEntry("name", "Alice");
    }

    // ── Result continues execution ──

    @Test
    void resultContinuesExecution() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap();

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.isContinueExecution()).isTrue();
        assertThat(result.getError()).isNull();
    }
}
