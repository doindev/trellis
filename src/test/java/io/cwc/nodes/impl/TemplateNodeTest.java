package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class TemplateNodeTest {

    private TemplateNode node;

    @BeforeEach
    void setUp() {
        node = new TemplateNode();
    }

    // ── Mustache: simple variable substitution ──

    @Test
    void mustacheSimpleVariable() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice"));
        Map<String, Object> params = mutableMap(
                "template", "Hello {{name}}!",
                "outputField", "output",
                "syntax", "mustache",
                "preserveNewlines", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result).get("output")).isEqualTo("Hello Alice!");
    }

    @Test
    void mustacheMultipleVariables() {
        List<Map<String, Object>> input = items(Map.of("firstName", "Alice", "lastName", "Smith"));
        Map<String, Object> params = mutableMap(
                "template", "{{firstName}} {{lastName}}",
                "outputField", "output",
                "syntax", "mustache",
                "preserveNewlines", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("output")).isEqualTo("Alice Smith");
    }

    @Test
    void mustacheMissingVariableReplacedWithEmpty() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice"));
        Map<String, Object> params = mutableMap(
                "template", "Hello {{name}}, age: {{age}}",
                "outputField", "output",
                "syntax", "mustache",
                "preserveNewlines", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("output")).isEqualTo("Hello Alice, age: ");
    }

    // ── Mustache: conditionals {{#if field}}...{{/if}} ──

    @Test
    void mustacheIfTruthy() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice", "premium", true));
        Map<String, Object> params = mutableMap(
                "template", "Hello {{name}}{{#if premium}} (Premium Member){{/if}}",
                "outputField", "output",
                "syntax", "mustache",
                "preserveNewlines", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("output")).isEqualTo("Hello Alice (Premium Member)");
    }

    @Test
    void mustacheIfFalsy() {
        List<Map<String, Object>> input = items(Map.of("name", "Bob", "premium", false));
        Map<String, Object> params = mutableMap(
                "template", "Hello {{name}}{{#if premium}} (Premium Member){{/if}}",
                "outputField", "output",
                "syntax", "mustache",
                "preserveNewlines", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("output")).isEqualTo("Hello Bob");
    }

    @Test
    void mustacheIfMissingField() {
        List<Map<String, Object>> input = items(Map.of("name", "Charlie"));
        Map<String, Object> params = mutableMap(
                "template", "Hello {{name}}{{#if vip}} VIP{{/if}}",
                "outputField", "output",
                "syntax", "mustache",
                "preserveNewlines", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("output")).isEqualTo("Hello Charlie");
    }

    @Test
    void mustacheIfNonEmptyString() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice", "role", "admin"));
        Map<String, Object> params = mutableMap(
                "template", "{{#if role}}Role: {{role}}{{/if}}",
                "outputField", "output",
                "syntax", "mustache",
                "preserveNewlines", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("output")).isEqualTo("Role: admin");
    }

    // ── Mustache: loops {{#each array}}...{{/each}} ──

    @Test
    void mustacheEachWithSimpleArray() {
        List<Map<String, Object>> input = items(
                mutableMap("items", List.of("apple", "banana", "cherry"))
        );
        Map<String, Object> params = mutableMap(
                "template", "Fruits: {{#each items}}{{this}}, {{/each}}",
                "outputField", "output",
                "syntax", "mustache",
                "preserveNewlines", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String out = (String) firstJson(result).get("output");
        assertThat(out).contains("apple");
        assertThat(out).contains("banana");
        assertThat(out).contains("cherry");
    }

    @Test
    void mustacheEachWithObjectArray() {
        List<Map<String, Object>> input = items(
                mutableMap("users", List.of(
                        Map.of("name", "Alice"),
                        Map.of("name", "Bob")
                ))
        );
        Map<String, Object> params = mutableMap(
                "template", "{{#each users}}{{this.name}} {{/each}}",
                "outputField", "output",
                "syntax", "mustache",
                "preserveNewlines", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String out = (String) firstJson(result).get("output");
        assertThat(out).contains("Alice");
        assertThat(out).contains("Bob");
    }

    @Test
    void mustacheEachWithEmptyArray() {
        List<Map<String, Object>> input = items(
                mutableMap("items", List.of())
        );
        Map<String, Object> params = mutableMap(
                "template", "Items: {{#each items}}{{this}}{{/each}}Done",
                "outputField", "output",
                "syntax", "mustache",
                "preserveNewlines", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("output")).isEqualTo("Items: Done");
    }

    // ── Mustache: default values {{field|default}} ──

    @Test
    void mustacheDefaultValueWhenFieldMissing() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice"));
        Map<String, Object> params = mutableMap(
                "template", "Hello {{name}}, role: {{role|guest}}",
                "outputField", "output",
                "syntax", "mustache",
                "preserveNewlines", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("output")).isEqualTo("Hello Alice, role: guest");
    }

    @Test
    void mustacheDefaultValueNotUsedWhenFieldPresent() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice", "role", "admin"));
        Map<String, Object> params = mutableMap(
                "template", "Hello {{name}}, role: {{role|guest}}",
                "outputField", "output",
                "syntax", "mustache",
                "preserveNewlines", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("output")).isEqualTo("Hello Alice, role: admin");
    }

    // ── Expression syntax ──

    @Test
    void expressionSyntaxJsonPrefix() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice"));
        Map<String, Object> params = mutableMap(
                "template", "Hello {{ $json.name }}!",
                "outputField", "output",
                "syntax", "expression",
                "preserveNewlines", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("output")).isEqualTo("Hello Alice!");
    }

    @Test
    void expressionSyntaxWithoutPrefix() {
        List<Map<String, Object>> input = items(Map.of("city", "NYC"));
        Map<String, Object> params = mutableMap(
                "template", "City: {{ city }}",
                "outputField", "output",
                "syntax", "expression",
                "preserveNewlines", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("output")).isEqualTo("City: NYC");
    }

    // ── Custom output field ──

    @Test
    void customOutputField() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice"));
        Map<String, Object> params = mutableMap(
                "template", "Hello {{name}}",
                "outputField", "greeting",
                "syntax", "mustache",
                "preserveNewlines", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsKey("greeting");
        assertThat(firstJson(result).get("greeting")).isEqualTo("Hello Alice");
    }

    // ── Preserve newlines ──

    @Test
    void preserveNewlinesTrue() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice"));
        Map<String, Object> params = mutableMap(
                "template", "Line 1\nLine 2",
                "outputField", "output",
                "syntax", "mustache",
                "preserveNewlines", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("output")).isEqualTo("Line 1\nLine 2");
    }

    @Test
    void preserveNewlinesFalse() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice"));
        Map<String, Object> params = mutableMap(
                "template", "Line 1\nLine 2",
                "outputField", "output",
                "syntax", "mustache",
                "preserveNewlines", false
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String out = (String) firstJson(result).get("output");
        assertThat(out).doesNotContain("\n");
        assertThat(out).contains("Line 1 Line 2");
    }

    // ── Empty input ──

    @Test
    void emptyInputReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "template", "Hello {{name}}",
                "outputField", "output",
                "syntax", "mustache",
                "preserveNewlines", true
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).isEmpty();
    }

    // ── Multiple input items ──

    @Test
    void multipleInputItems() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice"),
                Map.of("name", "Bob")
        );
        Map<String, Object> params = mutableMap(
                "template", "Hello {{name}}",
                "outputField", "output",
                "syntax", "mustache",
                "preserveNewlines", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(2);
        assertThat(jsonAt(result, 0).get("output")).isEqualTo("Hello Alice");
        assertThat(jsonAt(result, 1).get("output")).isEqualTo("Hello Bob");
    }

    // ── Original fields preserved ──

    @Test
    void originalFieldsPreserved() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice", "age", 30));
        Map<String, Object> params = mutableMap(
                "template", "Hi {{name}}",
                "outputField", "greeting",
                "syntax", "mustache",
                "preserveNewlines", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("name", "Alice");
        assertThat(firstJson(result)).containsEntry("age", 30);
        assertThat(firstJson(result)).containsKey("greeting");
    }
}
