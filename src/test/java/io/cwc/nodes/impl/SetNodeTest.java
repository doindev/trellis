package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class SetNodeTest {

    private SetNode node;

    @BeforeEach
    void setUp() {
        node = new SetNode();
    }

    // -- Manual mode: string field --

    @Test
    void manualModeSetStringField() {
        List<Map<String, Object>> input = items(Map.of("existing", "data"));
        Map<String, Object> params = mutableMap(
                "mode", "manual",
                "includeOtherFields", true,
                "fields", List.of(
                        mutableMap("name", "greeting", "value", "hello", "type", "string")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("greeting", "hello");
        assertThat(firstJson(result)).containsEntry("existing", "data");
    }

    // -- Manual mode: number field (integer) --

    @Test
    void manualModeSetIntegerField() {
        List<Map<String, Object>> input = items(Map.of("name", "test"));
        Map<String, Object> params = mutableMap(
                "mode", "manual",
                "includeOtherFields", true,
                "fields", List.of(
                        mutableMap("name", "count", "value", "42", "type", "number")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("count", 42L);
    }

    // -- Manual mode: number field (decimal) --

    @Test
    void manualModeSetDecimalField() {
        List<Map<String, Object>> input = items(Map.of("name", "test"));
        Map<String, Object> params = mutableMap(
                "mode", "manual",
                "includeOtherFields", true,
                "fields", List.of(
                        mutableMap("name", "price", "value", "19.99", "type", "number")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("price", 19.99);
    }

    // -- Manual mode: boolean field --

    @Test
    void manualModeSetBooleanFieldTrue() {
        List<Map<String, Object>> input = items(Map.of("name", "test"));
        Map<String, Object> params = mutableMap(
                "mode", "manual",
                "includeOtherFields", true,
                "fields", List.of(
                        mutableMap("name", "active", "value", "true", "type", "boolean")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("active", true);
    }

    @Test
    void manualModeSetBooleanFieldFalse() {
        List<Map<String, Object>> input = items(Map.of("name", "test"));
        Map<String, Object> params = mutableMap(
                "mode", "manual",
                "includeOtherFields", true,
                "fields", List.of(
                        mutableMap("name", "active", "value", "false", "type", "boolean")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("active", false);
    }

    // -- Manual mode: JSON field --

    @Test
    void manualModeSetJsonField() {
        List<Map<String, Object>> input = items(Map.of("name", "test"));
        Map<String, Object> params = mutableMap(
                "mode", "manual",
                "includeOtherFields", true,
                "fields", List.of(
                        mutableMap("name", "metadata", "value", "{\"key\":\"val\",\"num\":123}", "type", "json")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        Object metadata = firstJson(result).get("metadata");
        assertThat(metadata).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> metaMap = (Map<String, Object>) metadata;
        assertThat(metaMap).containsEntry("key", "val");
        assertThat(metaMap).containsEntry("num", 123);
    }

    // -- Manual mode: nested field with dot notation --

    @Test
    void manualModeSetNestedFieldWithDotNotation() {
        List<Map<String, Object>> input = items(Map.of("name", "test"));
        Map<String, Object> params = mutableMap(
                "mode", "manual",
                "includeOtherFields", true,
                "fields", List.of(
                        mutableMap("name", "user.email", "value", "alice@example.com", "type", "string")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        Object user = firstJson(result).get("user");
        assertThat(user).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> userMap = (Map<String, Object>) user;
        assertThat(userMap).containsEntry("email", "alice@example.com");
    }

    // -- Manual mode: includeOtherFields=true --

    @Test
    void manualModeIncludeOtherFieldsTrue() {
        List<Map<String, Object>> input = items(Map.of("existing1", "keep1", "existing2", "keep2"));
        Map<String, Object> params = mutableMap(
                "mode", "manual",
                "includeOtherFields", true,
                "fields", List.of(
                        mutableMap("name", "newField", "value", "newValue", "type", "string")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("existing1", "keep1");
        assertThat(firstJson(result)).containsEntry("existing2", "keep2");
        assertThat(firstJson(result)).containsEntry("newField", "newValue");
    }

    // -- Manual mode: includeOtherFields=false --

    @Test
    void manualModeIncludeOtherFieldsFalse() {
        List<Map<String, Object>> input = items(Map.of("existing1", "drop1", "existing2", "drop2"));
        Map<String, Object> params = mutableMap(
                "mode", "manual",
                "includeOtherFields", false,
                "fields", List.of(
                        mutableMap("name", "onlyField", "value", "onlyValue", "type", "string")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("onlyField", "onlyValue");
        assertThat(firstJson(result)).doesNotContainKey("existing1");
        assertThat(firstJson(result)).doesNotContainKey("existing2");
    }

    // -- Raw JSON mode: replace with object --

    @Test
    void rawJsonModeReplaceWithObject() {
        List<Map<String, Object>> input = items(Map.of("old", "data"));
        Map<String, Object> params = mutableMap(
                "mode", "raw",
                "rawJson", "{\"brand\":\"Acme\",\"year\":2024}"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("brand", "Acme");
        assertThat(firstJson(result)).containsEntry("year", 2024);
        assertThat(firstJson(result)).doesNotContainKey("old");
    }

    // -- Raw JSON mode: replace with array --

    @Test
    void rawJsonModeReplaceWithArray() {
        List<Map<String, Object>> input = items(Map.of("old", "data"));
        Map<String, Object> params = mutableMap(
                "mode", "raw",
                "rawJson", "[{\"id\":1},{\"id\":2},{\"id\":3}]"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        // Array JSON produces one item per array element, breaking after first input item
        assertThat(output(result)).hasSize(3);
        assertThat(jsonAt(result, 0)).containsEntry("id", 1);
        assertThat(jsonAt(result, 1)).containsEntry("id", 2);
        assertThat(jsonAt(result, 2)).containsEntry("id", 3);
    }

    // -- Empty input --

    @Test
    void emptyInputReturnsEmpty() {
        Map<String, Object> params = mutableMap("mode", "manual");
        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).isEmpty();
    }

    @Test
    void nullInputReturnsEmpty() {
        Map<String, Object> params = mutableMap("mode", "manual");
        NodeExecutionResult result = node.execute(ctx(null, params));

        assertThat(output(result)).isEmpty();
    }

    // -- No fields in manual mode --

    @Test
    void manualModeNoFieldsIncludeOtherFieldsTrue() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice"));
        Map<String, Object> params = mutableMap(
                "mode", "manual",
                "includeOtherFields", true
                // no "fields" parameter
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        // With includeOtherFields=true and no fields to set, original data is preserved
        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("name", "Alice");
    }

    @Test
    void manualModeNoFieldsIncludeOtherFieldsFalse() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice"));
        Map<String, Object> params = mutableMap(
                "mode", "manual",
                "includeOtherFields", false
                // no "fields" parameter
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        // With includeOtherFields=false and no fields, result is an empty json object
        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).isEmpty();
    }

    // -- Invalid JSON in raw mode --

    @Test
    void rawJsonModeInvalidJsonFallsBackToOriginal() {
        List<Map<String, Object>> input = items(Map.of("original", "data"));
        Map<String, Object> params = mutableMap(
                "mode", "raw",
                "rawJson", "this is not valid json"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        // On invalid JSON, the node falls back to the original item
        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("original", "data");
    }

    // -- Multiple items in manual mode --

    @Test
    void manualModeMultipleItems() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice"),
                Map.of("name", "Bob")
        );
        Map<String, Object> params = mutableMap(
                "mode", "manual",
                "includeOtherFields", true,
                "fields", List.of(
                        mutableMap("name", "role", "value", "admin", "type", "string")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(2);
        assertThat(jsonAt(result, 0)).containsEntry("name", "Alice").containsEntry("role", "admin");
        assertThat(jsonAt(result, 1)).containsEntry("name", "Bob").containsEntry("role", "admin");
    }

    // -- Multiple fields set at once --

    @Test
    void manualModeMultipleFieldsSetAtOnce() {
        List<Map<String, Object>> input = items(Map.of("id", 1));
        Map<String, Object> params = mutableMap(
                "mode", "manual",
                "includeOtherFields", true,
                "fields", List.of(
                        mutableMap("name", "status", "value", "active", "type", "string"),
                        mutableMap("name", "count", "value", "5", "type", "number"),
                        mutableMap("name", "verified", "value", "true", "type", "boolean")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("id", 1);
        assertThat(firstJson(result)).containsEntry("status", "active");
        assertThat(firstJson(result)).containsEntry("count", 5L);
        assertThat(firstJson(result)).containsEntry("verified", true);
    }
}
