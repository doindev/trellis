package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class SchemaValidatorNodeTest {

    private SchemaValidatorNode node;

    @BeforeEach
    void setUp() {
        node = new SchemaValidatorNode();
    }

    // ── Empty / null input ──

    @Test
    void emptyInputReturnsTwoEmptyOutputs() {
        Map<String, Object> params = mutableMap(
                "mode", "fieldChecks",
                "checks", List.of()
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).isEmpty();
    }

    @Test
    void nullInputReturnsTwoEmptyOutputs() {
        Map<String, Object> params = mutableMap(
                "mode", "fieldChecks",
                "checks", List.of()
        );

        NodeExecutionResult result = node.execute(ctx(null, params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).isEmpty();
    }

    // ── Field checks mode ──

    @Nested
    class FieldChecksMode {

        // ── Valid items pass to output 0 ──

        @Test
        void validItemsPassToOutput0() {
            List<Map<String, Object>> input = items(
                    Map.of("name", "Alice", "age", 30),
                    Map.of("name", "Bob", "age", 25)
            );
            Map<String, Object> params = mutableMap(
                    "mode", "fieldChecks",
                    "checks", List.of(
                            mutableMap("fieldName", "name", "checkType", "required", "checkValue", "")
                    ),
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(2);
            assertThat(output(result, 1)).isEmpty();
        }

        // ── Invalid items pass to output 1 with _validationErrors ──

        @Test
        void invalidItemsPassToOutput1() {
            Map<String, Object> item1Json = new java.util.HashMap<>();
            item1Json.put("name", null);
            item1Json.put("age", 30);
            Map<String, Object> wrapped1 = new java.util.HashMap<>();
            wrapped1.put("json", item1Json);

            List<Map<String, Object>> input = List.of(
                    wrapped1,
                    item(Map.of("name", "Bob", "age", 25))
            );
            Map<String, Object> params = mutableMap(
                    "mode", "fieldChecks",
                    "checks", List.of(
                            mutableMap("fieldName", "name", "checkType", "required", "checkValue", "")
                    ),
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("name", "Bob");
            assertThat(output(result, 1)).hasSize(1);
        }

        // ── Required field validation ──

        @Test
        void requiredFieldValidation() {
            Map<String, Object> item1Json = new java.util.HashMap<>();
            item1Json.put("email", null);
            Map<String, Object> wrapped1 = new java.util.HashMap<>();
            wrapped1.put("json", item1Json);

            List<Map<String, Object>> input = List.of(
                    wrapped1,
                    item(Map.of("email", "alice@example.com"))
            );
            Map<String, Object> params = mutableMap(
                    "mode", "fieldChecks",
                    "checks", List.of(
                            mutableMap("fieldName", "email", "checkType", "required", "checkValue", "")
                    ),
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(output(result, 1)).hasSize(1);
            assertThat(jsonAt(result, 1, 0)).containsKey("_validationErrors");
        }

        // ── Type checking: string ──

        @Test
        void typeCheckForString() {
            List<Map<String, Object>> input = items(
                    Map.of("name", "Alice"),
                    Map.of("name", 42)
            );
            Map<String, Object> params = mutableMap(
                    "mode", "fieldChecks",
                    "checks", List.of(
                            mutableMap("fieldName", "name", "checkType", "type", "checkValue", "string")
                    ),
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("name", "Alice");
            assertThat(output(result, 1)).hasSize(1);
        }

        // ── Type checking: number ──

        @Test
        void typeCheckForNumber() {
            List<Map<String, Object>> input = items(
                    Map.of("val", 42),
                    Map.of("val", "not a number")
            );
            Map<String, Object> params = mutableMap(
                    "mode", "fieldChecks",
                    "checks", List.of(
                            mutableMap("fieldName", "val", "checkType", "type", "checkValue", "number")
                    ),
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("val", 42);
            assertThat(output(result, 1)).hasSize(1);
        }

        // ── Type checking: boolean ──

        @Test
        void typeCheckForBoolean() {
            List<Map<String, Object>> input = items(
                    Map.of("flag", true),
                    Map.of("flag", "yes")
            );
            Map<String, Object> params = mutableMap(
                    "mode", "fieldChecks",
                    "checks", List.of(
                            mutableMap("fieldName", "flag", "checkType", "type", "checkValue", "boolean")
                    ),
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("flag", true);
            assertThat(output(result, 1)).hasSize(1);
        }

        // ── Type checking: array ──

        @Test
        void typeCheckForArray() {
            List<Map<String, Object>> input = items(
                    mutableMap("tags", List.of("java", "spring")),
                    Map.of("tags", "not-an-array")
            );
            Map<String, Object> params = mutableMap(
                    "mode", "fieldChecks",
                    "checks", List.of(
                            mutableMap("fieldName", "tags", "checkType", "type", "checkValue", "array")
                    ),
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(output(result, 1)).hasSize(1);
        }

        // ── Type checking: object ──

        @Test
        void typeCheckForObject() {
            List<Map<String, Object>> input = items(
                    mutableMap("meta", Map.of("key", "val")),
                    Map.of("meta", "not-an-object")
            );
            Map<String, Object> params = mutableMap(
                    "mode", "fieldChecks",
                    "checks", List.of(
                            mutableMap("fieldName", "meta", "checkType", "type", "checkValue", "object")
                    ),
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(output(result, 1)).hasSize(1);
        }

        // ── Not empty validation ──

        @Test
        void notEmptyValidation() {
            List<Map<String, Object>> input = items(
                    Map.of("name", "Alice"),
                    Map.of("name", ""),
                    Map.of("name", "   ")
            );
            Map<String, Object> params = mutableMap(
                    "mode", "fieldChecks",
                    "checks", List.of(
                            mutableMap("fieldName", "name", "checkType", "notEmpty", "checkValue", "")
                    ),
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("name", "Alice");
            assertThat(output(result, 1)).hasSize(2);
        }

        // ── Min length ──

        @Test
        void minLengthValidation() {
            List<Map<String, Object>> input = items(
                    Map.of("password", "abc"),
                    Map.of("password", "abcdefgh")
            );
            Map<String, Object> params = mutableMap(
                    "mode", "fieldChecks",
                    "checks", List.of(
                            mutableMap("fieldName", "password", "checkType", "minLength", "checkValue", "5")
                    ),
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("password", "abcdefgh");
            assertThat(output(result, 1)).hasSize(1);
        }

        // ── Max length ──

        @Test
        void maxLengthValidation() {
            List<Map<String, Object>> input = items(
                    Map.of("code", "ABC"),
                    Map.of("code", "ABCDEFGHIJ")
            );
            Map<String, Object> params = mutableMap(
                    "mode", "fieldChecks",
                    "checks", List.of(
                            mutableMap("fieldName", "code", "checkType", "maxLength", "checkValue", "5")
                    ),
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("code", "ABC");
            assertThat(output(result, 1)).hasSize(1);
        }

        // ── Pattern validation ──

        @Test
        void patternValidation() {
            List<Map<String, Object>> input = items(
                    Map.of("email", "test@example.com"),
                    Map.of("email", "not-an-email")
            );
            Map<String, Object> params = mutableMap(
                    "mode", "fieldChecks",
                    "checks", List.of(
                            mutableMap("fieldName", "email", "checkType", "pattern", "checkValue", "^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$")
                    ),
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("email", "test@example.com");
            assertThat(output(result, 1)).hasSize(1);
        }

        // ── Min value ──

        @Test
        void minValueValidation() {
            List<Map<String, Object>> input = items(
                    Map.of("age", 15),
                    Map.of("age", 25)
            );
            Map<String, Object> params = mutableMap(
                    "mode", "fieldChecks",
                    "checks", List.of(
                            mutableMap("fieldName", "age", "checkType", "minValue", "checkValue", "18")
                    ),
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("age", 25);
            assertThat(output(result, 1)).hasSize(1);
        }

        // ── Max value ──

        @Test
        void maxValueValidation() {
            List<Map<String, Object>> input = items(
                    Map.of("score", 85),
                    Map.of("score", 105)
            );
            Map<String, Object> params = mutableMap(
                    "mode", "fieldChecks",
                    "checks", List.of(
                            mutableMap("fieldName", "score", "checkType", "maxValue", "checkValue", "100")
                    ),
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("score", 85);
            assertThat(output(result, 1)).hasSize(1);
        }

        // ── In list validation ──

        @Test
        void inListValidation() {
            List<Map<String, Object>> input = items(
                    Map.of("status", "active"),
                    Map.of("status", "deleted"),
                    Map.of("status", "pending")
            );
            Map<String, Object> params = mutableMap(
                    "mode", "fieldChecks",
                    "checks", List.of(
                            mutableMap("fieldName", "status", "checkType", "inList", "checkValue", "active, pending, suspended")
                    ),
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(2);
            assertThat(output(result, 1)).hasSize(1);
            assertThat(jsonAt(result, 1, 0)).containsEntry("status", "deleted");
        }

        // ── Multiple checks on different fields ──

        @Test
        void multipleCheckRules() {
            List<Map<String, Object>> input = items(
                    Map.of("name", "Alice", "age", 25),
                    Map.of("name", "", "age", 25),
                    Map.of("name", "Charlie", "age", 10)
            );
            Map<String, Object> params = mutableMap(
                    "mode", "fieldChecks",
                    "checks", List.of(
                            mutableMap("fieldName", "name", "checkType", "notEmpty", "checkValue", ""),
                            mutableMap("fieldName", "age", "checkType", "minValue", "checkValue", "18")
                    ),
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            // Only Alice (name not empty, age >= 18) passes both checks
            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("name", "Alice");
            assertThat(output(result, 1)).hasSize(2);
        }

        // ── includeErrors=true adds _validationErrors to invalid items ──

        @Test
        void includeErrorsAddsValidationErrorsToInvalidItems() {
            Map<String, Object> item1Json = new java.util.HashMap<>();
            item1Json.put("name", null);
            Map<String, Object> wrapped1 = new java.util.HashMap<>();
            wrapped1.put("json", item1Json);

            List<Map<String, Object>> input = List.of(wrapped1);
            Map<String, Object> params = mutableMap(
                    "mode", "fieldChecks",
                    "checks", List.of(
                            mutableMap("fieldName", "name", "checkType", "required", "checkValue", "")
                    ),
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            Map<String, Object> invalidJson = jsonAt(result, 1, 0);
            assertThat(invalidJson).containsKey("_validationErrors");
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) invalidJson.get("_validationErrors");
            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0)).contains("name");
        }

        // ── includeErrors=false does not add _validationErrors ──

        @Test
        void includeErrorsFalseDoesNotAddValidationErrors() {
            Map<String, Object> item1Json = new java.util.HashMap<>();
            item1Json.put("name", null);
            Map<String, Object> wrapped1 = new java.util.HashMap<>();
            wrapped1.put("json", item1Json);

            List<Map<String, Object>> input = List.of(wrapped1);
            Map<String, Object> params = mutableMap(
                    "mode", "fieldChecks",
                    "checks", List.of(
                            mutableMap("fieldName", "name", "checkType", "required", "checkValue", "")
                    ),
                    "includeErrors", false
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            Map<String, Object> invalidJson = jsonAt(result, 1, 0);
            assertThat(invalidJson).doesNotContainKey("_validationErrors");
        }

        // ── No checks means all items are valid ──

        @Test
        void noChecksAllItemsAreValid() {
            List<Map<String, Object>> input = items(
                    Map.of("x", 1),
                    Map.of("y", 2)
            );
            Map<String, Object> params = mutableMap(
                    "mode", "fieldChecks"
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(2);
            assertThat(output(result, 1)).isEmpty();
        }

        // ── Multiple errors on same item ──

        @Test
        void multipleErrorsOnSameItem() {
            List<Map<String, Object>> input = items(
                    Map.of("name", "", "age", 10)
            );
            Map<String, Object> params = mutableMap(
                    "mode", "fieldChecks",
                    "checks", List.of(
                            mutableMap("fieldName", "name", "checkType", "notEmpty", "checkValue", ""),
                            mutableMap("fieldName", "age", "checkType", "minValue", "checkValue", "18")
                    ),
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).isEmpty();
            assertThat(output(result, 1)).hasSize(1);
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) jsonAt(result, 1, 0).get("_validationErrors");
            assertThat(errors).hasSize(2);
        }
    }

    // ── JSON Schema mode ──

    @Nested
    class JsonSchemaMode {

        @Test
        void validItemsPassJsonSchemaValidation() {
            List<Map<String, Object>> input = items(
                    Map.of("name", "Alice", "email", "alice@example.com")
            );
            String schema = "{\"type\": \"object\", \"required\": [\"name\", \"email\"]}";
            Map<String, Object> params = mutableMap(
                    "mode", "jsonSchema",
                    "schema", schema,
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(output(result, 1)).isEmpty();
        }

        @Test
        void missingRequiredFieldFailsJsonSchemaValidation() {
            List<Map<String, Object>> input = items(
                    Map.of("name", "Alice")
            );
            String schema = "{\"type\": \"object\", \"required\": [\"name\", \"email\"]}";
            Map<String, Object> params = mutableMap(
                    "mode", "jsonSchema",
                    "schema", schema,
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).isEmpty();
            assertThat(output(result, 1)).hasSize(1);
            assertThat(jsonAt(result, 1, 0)).containsKey("_validationErrors");
        }

        @Test
        void propertyTypeCheckInJsonSchema() {
            List<Map<String, Object>> input = items(
                    Map.of("name", "Alice", "age", 30),
                    Map.of("name", "Bob", "age", "not a number")
            );
            String schema = "{\"type\": \"object\", \"properties\": {\"age\": {\"type\": \"number\"}}}";
            Map<String, Object> params = mutableMap(
                    "mode", "jsonSchema",
                    "schema", schema,
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("name", "Alice");
            assertThat(output(result, 1)).hasSize(1);
        }

        @Test
        void invalidSchemaJsonReturnsAllInvalid() {
            List<Map<String, Object>> input = items(
                    Map.of("x", 1)
            );
            Map<String, Object> params = mutableMap(
                    "mode", "jsonSchema",
                    "schema", "not valid json {",
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).isEmpty();
            assertThat(output(result, 1)).hasSize(1);
        }

        @Test
        void emptyInputWithJsonSchemaReturnsTwoEmptyOutputs() {
            String schema = "{\"type\": \"object\", \"required\": [\"name\"]}";
            Map<String, Object> params = mutableMap(
                    "mode", "jsonSchema",
                    "schema", schema,
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(List.of(), params));

            assertThat(output(result, 0)).isEmpty();
            assertThat(output(result, 1)).isEmpty();
        }

        @Test
        void schemaAsMapObject() {
            List<Map<String, Object>> input = items(
                    Map.of("name", "Alice", "email", "alice@test.com")
            );
            Map<String, Object> schemaMap = mutableMap(
                    "type", "object",
                    "required", List.of("name", "email")
            );
            Map<String, Object> params = mutableMap(
                    "mode", "jsonSchema",
                    "schema", schemaMap,
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(output(result, 1)).isEmpty();
        }

        @Test
        void jsonSchemaStringPropertyTypeValidation() {
            List<Map<String, Object>> input = items(
                    Map.of("name", "Alice"),
                    Map.of("name", 123)
            );
            String schema = "{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\"}}}";
            Map<String, Object> params = mutableMap(
                    "mode", "jsonSchema",
                    "schema", schema,
                    "includeErrors", true
            );

            NodeExecutionResult result = node.execute(ctx(input, params));

            assertThat(output(result, 0)).hasSize(1);
            assertThat(jsonAt(result, 0, 0)).containsEntry("name", "Alice");
            assertThat(output(result, 1)).hasSize(1);
        }
    }

    // ── Result verification ──

    @Test
    void resultContinuesExecution() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "mode", "fieldChecks",
                "checks", List.of()
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.isContinueExecution()).isTrue();
        assertThat(result.getError()).isNull();
    }
}
