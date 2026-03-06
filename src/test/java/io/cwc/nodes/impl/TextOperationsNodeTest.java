package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class TextOperationsNodeTest {

    private TextOperationsNode node;

    @BeforeEach
    void setUp() {
        node = new TextOperationsNode();
    }

    // ── Empty / null input ──

    @Test
    void emptyInputReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "operation", "trim",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).isEmpty();
    }

    @Test
    void nullInputReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "operation", "trim",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(null, params));

        assertThat(output(result)).isEmpty();
    }

    // ── Default operation is trim ──

    @Test
    void defaultOperationIsTrim() {
        List<Map<String, Object>> input = items(Map.of("text", "  hello  "));
        Map<String, Object> params = mutableMap(
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("hello");
    }

    // ── trim ──

    @Test
    void trimWhitespace() {
        List<Map<String, Object>> input = items(Map.of("text", "  hello world  "));
        Map<String, Object> params = mutableMap(
                "operation", "trim",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("hello world");
    }

    // ── toUpperCase ──

    @Test
    void toUpperCase() {
        List<Map<String, Object>> input = items(Map.of("text", "hello world"));
        Map<String, Object> params = mutableMap(
                "operation", "toUpperCase",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("HELLO WORLD");
    }

    // ── toLowerCase ──

    @Test
    void toLowerCase() {
        List<Map<String, Object>> input = items(Map.of("text", "HELLO WORLD"));
        Map<String, Object> params = mutableMap(
                "operation", "toLowerCase",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("hello world");
    }

    // ── titleCase ──

    @Test
    void titleCase() {
        List<Map<String, Object>> input = items(Map.of("text", "hello beautiful world"));
        Map<String, Object> params = mutableMap(
                "operation", "titleCase",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("Hello Beautiful World");
    }

    @Test
    void titleCaseWithHyphens() {
        List<Map<String, Object>> input = items(Map.of("text", "hello-world test"));
        Map<String, Object> params = mutableMap(
                "operation", "titleCase",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("Hello-World Test");
    }

    // ── camelCase ──

    @Test
    void camelCaseFromSpaces() {
        List<Map<String, Object>> input = items(Map.of("text", "hello beautiful world"));
        Map<String, Object> params = mutableMap(
                "operation", "camelCase",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("helloBeautifulWorld");
    }

    @Test
    void camelCaseFromHyphenated() {
        List<Map<String, Object>> input = items(Map.of("text", "my-component-name"));
        Map<String, Object> params = mutableMap(
                "operation", "camelCase",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("myComponentName");
    }

    @Test
    void camelCaseFromUnderscored() {
        List<Map<String, Object>> input = items(Map.of("text", "my_variable_name"));
        Map<String, Object> params = mutableMap(
                "operation", "camelCase",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("myVariableName");
    }

    // ── snakeCase ──

    @Test
    void snakeCaseFromCamelCase() {
        List<Map<String, Object>> input = items(Map.of("text", "helloWorld"));
        Map<String, Object> params = mutableMap(
                "operation", "snakeCase",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("hello_world");
    }

    @Test
    void snakeCaseFromSpaces() {
        List<Map<String, Object>> input = items(Map.of("text", "Hello World Test"));
        Map<String, Object> params = mutableMap(
                "operation", "snakeCase",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat((String) firstJson(result).get("text")).isEqualTo("hello_world_test");
    }

    @Test
    void snakeCaseFromHyphenated() {
        List<Map<String, Object>> input = items(Map.of("text", "my-component-name"));
        Map<String, Object> params = mutableMap(
                "operation", "snakeCase",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat((String) firstJson(result).get("text")).isEqualTo("my_component_name");
    }

    // ── slugify ──

    @Test
    void slugify() {
        List<Map<String, Object>> input = items(Map.of("text", "Hello World! This is a Test."));
        Map<String, Object> params = mutableMap(
                "operation", "slugify",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("hello-world-this-is-a-test");
    }

    @Test
    void slugifySpecialChars() {
        List<Map<String, Object>> input = items(Map.of("text", "  Hello, World!  How are you?  "));
        Map<String, Object> params = mutableMap(
                "operation", "slugify",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String slug = (String) firstJson(result).get("text");
        assertThat(slug).doesNotContain(" ");
        assertThat(slug).doesNotContain(",");
        assertThat(slug).doesNotContain("!");
        assertThat(slug).doesNotContain("?");
        assertThat(slug).matches("[a-z0-9-]+");
    }

    // ── replace ──

    @Test
    void replaceAll() {
        List<Map<String, Object>> input = items(Map.of("text", "foo bar foo baz foo"));
        Map<String, Object> params = mutableMap(
                "operation", "replace",
                "inputField", "text",
                "outputField", "",
                "searchValue", "foo",
                "replaceValue", "qux",
                "replaceAll", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("qux bar qux baz qux");
    }

    @Test
    void replaceFirst() {
        List<Map<String, Object>> input = items(Map.of("text", "foo bar foo baz"));
        Map<String, Object> params = mutableMap(
                "operation", "replace",
                "inputField", "text",
                "outputField", "",
                "searchValue", "foo",
                "replaceValue", "qux",
                "replaceAll", false
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("qux bar foo baz");
    }

    // ── regexReplace ──

    @Test
    void regexReplace() {
        List<Map<String, Object>> input = items(Map.of("text", "abc123def456"));
        Map<String, Object> params = mutableMap(
                "operation", "regexReplace",
                "inputField", "text",
                "outputField", "",
                "searchValue", "\\d+",
                "replaceValue", "#"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("abc#def#");
    }

    // ── split ──

    @Test
    void splitByComma() {
        List<Map<String, Object>> input = items(Map.of("text", "a,b,c"));
        Map<String, Object> params = mutableMap(
                "operation", "split",
                "inputField", "text",
                "outputField", "",
                "separator", ","
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        Object val = firstJson(result).get("text");
        assertThat(val).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> list = (List<String>) val;
        assertThat(list).containsExactly("a", "b", "c");
    }

    @Test
    void splitByCustomSeparator() {
        List<Map<String, Object>> input = items(Map.of("text", "a|b|c"));
        Map<String, Object> params = mutableMap(
                "operation", "split",
                "inputField", "text",
                "outputField", "",
                "separator", "|"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        List<String> list = (List<String>) firstJson(result).get("text");
        assertThat(list).containsExactly("a", "b", "c");
    }

    // ── join ──

    @Test
    void joinArray() {
        List<Map<String, Object>> input = items(
                mutableMap("text", List.of("a", "b", "c"))
        );
        Map<String, Object> params = mutableMap(
                "operation", "join",
                "inputField", "text",
                "outputField", "",
                "separator", "-"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("a-b-c");
    }

    @Test
    void joinArrayWithComma() {
        List<Map<String, Object>> input = items(
                mutableMap("text", List.of("x", "y", "z"))
        );
        Map<String, Object> params = mutableMap(
                "operation", "join",
                "inputField", "text",
                "outputField", "",
                "separator", ", "
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("x, y, z");
    }

    // ── truncate ──

    @Test
    void truncateWithSuffix() {
        List<Map<String, Object>> input = items(Map.of("text", "Hello beautiful world, this is a long string."));
        Map<String, Object> params = mutableMap(
                "operation", "truncate",
                "inputField", "text",
                "outputField", "",
                "maxLength", 20,
                "suffix", "..."
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String val = (String) firstJson(result).get("text");
        assertThat(val).hasSize(20);
        assertThat(val).endsWith("...");
    }

    @Test
    void truncateShortStringUnchanged() {
        List<Map<String, Object>> input = items(Map.of("text", "Hello"));
        Map<String, Object> params = mutableMap(
                "operation", "truncate",
                "inputField", "text",
                "outputField", "",
                "maxLength", 100,
                "suffix", "..."
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("Hello");
    }

    // ── pad ──

    @Test
    void padStart() {
        List<Map<String, Object>> input = items(Map.of("text", "42"));
        Map<String, Object> params = mutableMap(
                "operation", "pad",
                "inputField", "text",
                "outputField", "",
                "targetLength", 6,
                "padChar", "0",
                "padDirection", "start"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("000042");
    }

    @Test
    void padEnd() {
        List<Map<String, Object>> input = items(Map.of("text", "Hi"));
        Map<String, Object> params = mutableMap(
                "operation", "pad",
                "inputField", "text",
                "outputField", "",
                "targetLength", 5,
                "padChar", ".",
                "padDirection", "end"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("Hi...");
    }

    // ── extract ──

    @Test
    void extractWithCaptureGroup() {
        List<Map<String, Object>> input = items(Map.of("text", "Order #12345 placed"));
        Map<String, Object> params = mutableMap(
                "operation", "extract",
                "inputField", "text",
                "outputField", "",
                "regexPattern", "#(\\d+)"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("12345");
    }

    @Test
    void extractWithoutCaptureGroup() {
        List<Map<String, Object>> input = items(Map.of("text", "Hello World 123"));
        Map<String, Object> params = mutableMap(
                "operation", "extract",
                "inputField", "text",
                "outputField", "",
                "regexPattern", "\\d+"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("123");
    }

    @Test
    void extractNoMatchReturnsNull() {
        List<Map<String, Object>> input = items(Map.of("text", "Hello World"));
        Map<String, Object> params = mutableMap(
                "operation", "extract",
                "inputField", "text",
                "outputField", "",
                "regexPattern", "\\d+"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isNull();
    }

    // ── template ──

    @Test
    void templateWithPlaceholders() {
        List<Map<String, Object>> input = items(
                mutableMap("name", "Alice", "orderId", "12345", "text", "ignored")
        );
        Map<String, Object> params = mutableMap(
                "operation", "template",
                "inputField", "text",
                "outputField", "message",
                "template", "Hello {{name}}, your order #{{orderId}} is ready."
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("message")).isEqualTo("Hello Alice, your order #12345 is ready.");
    }

    @Test
    void templateWithMissingFieldReplacesWithEmpty() {
        List<Map<String, Object>> input = items(
                mutableMap("name", "Bob", "text", "ignored")
        );
        Map<String, Object> params = mutableMap(
                "operation", "template",
                "inputField", "text",
                "outputField", "result",
                "template", "Hello {{name}}, ref: {{missingField}}"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("result")).isEqualTo("Hello Bob, ref: ");
    }

    // ── base64Encode ──

    @Test
    void base64Encode() {
        List<Map<String, Object>> input = items(Map.of("text", "Hello World"));
        Map<String, Object> params = mutableMap(
                "operation", "base64Encode",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("SGVsbG8gV29ybGQ=");
    }

    // ── base64Decode ──

    @Test
    void base64Decode() {
        List<Map<String, Object>> input = items(Map.of("text", "SGVsbG8gV29ybGQ="));
        Map<String, Object> params = mutableMap(
                "operation", "base64Decode",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("Hello World");
    }

    // ── base64 roundtrip ──

    @Test
    void base64Roundtrip() {
        String original = "Test data with special chars: <>&\"'";
        List<Map<String, Object>> input1 = items(Map.of("text", original));
        Map<String, Object> encParams = mutableMap(
                "operation", "base64Encode",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult encResult = node.execute(ctx(input1, encParams));
        String encoded = (String) firstJson(encResult).get("text");

        List<Map<String, Object>> input2 = items(Map.of("text", encoded));
        Map<String, Object> decParams = mutableMap(
                "operation", "base64Decode",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult decResult = node.execute(ctx(input2, decParams));
        assertThat(firstJson(decResult).get("text")).isEqualTo(original);
    }

    // ── urlEncode ──

    @Test
    void urlEncode() {
        List<Map<String, Object>> input = items(Map.of("text", "hello world&foo=bar"));
        Map<String, Object> params = mutableMap(
                "operation", "urlEncode",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String encoded = (String) firstJson(result).get("text");
        assertThat(encoded).contains("hello+world");
        assertThat(encoded).contains("%26");
    }

    // ── urlDecode ──

    @Test
    void urlDecode() {
        List<Map<String, Object>> input = items(Map.of("text", "hello+world%26foo%3Dbar"));
        Map<String, Object> params = mutableMap(
                "operation", "urlDecode",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("hello world&foo=bar");
    }

    // ── URL encode/decode roundtrip ──

    @Test
    void urlEncodeDecodeRoundtrip() {
        String original = "query=hello world&sort=name&filter=active";
        List<Map<String, Object>> input1 = items(Map.of("text", original));
        Map<String, Object> encParams = mutableMap(
                "operation", "urlEncode",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult encResult = node.execute(ctx(input1, encParams));
        String encoded = (String) firstJson(encResult).get("text");

        List<Map<String, Object>> input2 = items(Map.of("text", encoded));
        Map<String, Object> decParams = mutableMap(
                "operation", "urlDecode",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult decResult = node.execute(ctx(input2, decParams));
        assertThat(firstJson(decResult).get("text")).isEqualTo(original);
    }

    // ── length ──

    @Test
    void length() {
        List<Map<String, Object>> input = items(Map.of("text", "Hello"));
        Map<String, Object> params = mutableMap(
                "operation", "length",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo(5);
    }

    @Test
    void lengthEmptyString() {
        List<Map<String, Object>> input = items(Map.of("text", ""));
        Map<String, Object> params = mutableMap(
                "operation", "length",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo(0);
    }

    // ── Custom output field ──

    @Test
    void customOutputField() {
        List<Map<String, Object>> input = items(Map.of("text", "hello"));
        Map<String, Object> params = mutableMap(
                "operation", "toUpperCase",
                "inputField", "text",
                "outputField", "result"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("result", "HELLO");
        // Original field is also preserved
        assertThat(firstJson(result)).containsEntry("text", "hello");
    }

    // ── Empty output field defaults to input field ──

    @Test
    void emptyOutputFieldDefaultsToInputField() {
        List<Map<String, Object>> input = items(Map.of("text", "hello"));
        Map<String, Object> params = mutableMap(
                "operation", "toUpperCase",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("text")).isEqualTo("HELLO");
    }

    // ── Multiple items ──

    @Test
    void multipleItems() {
        List<Map<String, Object>> input = items(
                Map.of("text", "hello"),
                Map.of("text", "world")
        );
        Map<String, Object> params = mutableMap(
                "operation", "toUpperCase",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(2);
        assertThat(jsonAt(result, 0).get("text")).isEqualTo("HELLO");
        assertThat(jsonAt(result, 1).get("text")).isEqualTo("WORLD");
    }

    // ── Result continues execution ──

    @Test
    void resultContinuesExecution() {
        List<Map<String, Object>> input = items(Map.of("text", "hello"));
        Map<String, Object> params = mutableMap(
                "operation", "trim",
                "inputField", "text",
                "outputField", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.isContinueExecution()).isTrue();
        assertThat(result.getError()).isNull();
    }
}
