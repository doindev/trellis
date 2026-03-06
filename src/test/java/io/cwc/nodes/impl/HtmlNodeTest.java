package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class HtmlNodeTest {

    private HtmlNode node;

    @BeforeEach
    void setUp() {
        node = new HtmlNode();
    }

    // ── Generate HTML Template: simple placeholder ──

    @Test
    void generateTemplateSimplePlaceholder() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice", "age", 30));
        Map<String, Object> params = mutableMap(
                "operation", "generateHtmlTemplate",
                "html", "<p>Hello {{ name }}, you are {{ age }} years old.</p>",
                "destinationKey", "html"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        String html = (String) firstJson(result).get("html");
        assertThat(html).isEqualTo("<p>Hello Alice, you are 30 years old.</p>");
        // Original fields preserved
        assertThat(firstJson(result)).containsEntry("name", "Alice");
    }

    @Test
    void generateTemplateMissingPlaceholderReplacedWithEmpty() {
        List<Map<String, Object>> input = items(Map.of("name", "Bob"));
        Map<String, Object> params = mutableMap(
                "operation", "generateHtmlTemplate",
                "html", "<div>{{ name }} - {{ missing }}</div>",
                "destinationKey", "html"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String html = (String) firstJson(result).get("html");
        assertThat(html).isEqualTo("<div>Bob - </div>");
    }

    @Test
    void generateTemplateMultipleItems() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice"),
                Map.of("name", "Bob")
        );
        Map<String, Object> params = mutableMap(
                "operation", "generateHtmlTemplate",
                "html", "<span>{{ name }}</span>",
                "destinationKey", "html"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(2);
        assertThat(jsonAt(result, 0).get("html")).isEqualTo("<span>Alice</span>");
        assertThat(jsonAt(result, 1).get("html")).isEqualTo("<span>Bob</span>");
    }

    // ── Extract HTML Content: by tag name ──

    @Test
    void extractContentByTag() {
        String htmlContent = "<html><body><h1>Title</h1><p>Paragraph text</p></body></html>";
        List<Map<String, Object>> input = items(Map.of("html", htmlContent));
        Map<String, Object> params = mutableMap(
                "operation", "extractHtmlContent",
                "sourceField", "html",
                "extractionValues", List.of(
                        mutableMap("key", "title", "cssSelector", "h1", "returnValue", "text"),
                        mutableMap("key", "paragraph", "cssSelector", "p", "returnValue", "text")
                ),
                "destinationKey", "html"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result).get("title")).isEqualTo("Title");
        assertThat(firstJson(result).get("paragraph")).isEqualTo("Paragraph text");
    }

    @Test
    void extractContentInnerHtml() {
        String htmlContent = "<div><p>Hello <strong>world</strong></p></div>";
        List<Map<String, Object>> input = items(Map.of("html", htmlContent));
        Map<String, Object> params = mutableMap(
                "operation", "extractHtmlContent",
                "sourceField", "html",
                "extractionValues", List.of(
                        mutableMap("key", "inner", "cssSelector", "p", "returnValue", "html")
                ),
                "destinationKey", "html"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String inner = (String) firstJson(result).get("inner");
        assertThat(inner).contains("<strong>world</strong>");
    }

    @Test
    void extractContentAttribute() {
        String htmlContent = "<div><a href=\"https://example.com\">Link</a></div>";
        List<Map<String, Object>> input = items(Map.of("html", htmlContent));
        Map<String, Object> params = mutableMap(
                "operation", "extractHtmlContent",
                "sourceField", "html",
                "extractionValues", List.of(
                        mutableMap("key", "link", "cssSelector", "a", "returnValue", "attribute", "attribute", "href")
                ),
                "destinationKey", "html"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("link")).isEqualTo("https://example.com");
    }

    @Test
    void extractMultipleMatchesReturnsListForText() {
        String htmlContent = "<ul><li>One</li><li>Two</li><li>Three</li></ul>";
        List<Map<String, Object>> input = items(Map.of("html", htmlContent));
        Map<String, Object> params = mutableMap(
                "operation", "extractHtmlContent",
                "sourceField", "html",
                "extractionValues", List.of(
                        mutableMap("key", "items", "cssSelector", "li", "returnValue", "text")
                ),
                "destinationKey", "html"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        Object items = firstJson(result).get("items");
        assertThat(items).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> list = (List<String>) items;
        assertThat(list).containsExactly("One", "Two", "Three");
    }

    // ── Convert to HTML Table ──

    @Test
    void convertToTable() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice", "age", 30),
                Map.of("name", "Bob", "age", 25)
        );
        Map<String, Object> params = mutableMap(
                "operation", "convertToTable",
                "destinationKey", "html"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        String table = (String) firstJson(result).get("html");
        assertThat(table).contains("<table");
        assertThat(table).contains("<thead>");
        assertThat(table).contains("<tbody>");
        assertThat(table).contains("Alice");
        assertThat(table).contains("Bob");
        assertThat(table).contains("name");
        assertThat(table).contains("age");
    }

    @Test
    void convertToTableContainsHeaderAndDataRows() {
        List<Map<String, Object>> input = items(
                Map.of("col1", "A", "col2", "B")
        );
        Map<String, Object> params = mutableMap(
                "operation", "convertToTable",
                "destinationKey", "tableHtml"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String table = (String) firstJson(result).get("tableHtml");
        assertThat(table).contains("<th");
        assertThat(table).contains("<td");
        assertThat(table).contains("col1");
        assertThat(table).contains("col2");
        assertThat(table).contains("A");
        assertThat(table).contains("B");
    }

    // ── Empty input ──

    @Test
    void emptyInputReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "operation", "generateHtmlTemplate",
                "html", "<p>{{ name }}</p>",
                "destinationKey", "html"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).isEmpty();
    }

    // ── Error: no extraction rules ──

    @Test
    void extractWithNoRulesReturnsError() {
        List<Map<String, Object>> input = items(Map.of("html", "<p>test</p>"));
        Map<String, Object> params = mutableMap(
                "operation", "extractHtmlContent",
                "sourceField", "html",
                "destinationKey", "html"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getError()).isNotNull();
        assertThat(result.getError().getMessage()).contains("No extraction rules");
    }

    // ── Error: empty template ──

    @Test
    void generateWithEmptyTemplateReturnsError() {
        List<Map<String, Object>> input = items(Map.of("name", "test"));
        Map<String, Object> params = mutableMap(
                "operation", "generateHtmlTemplate",
                "html", "",
                "destinationKey", "html"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getError()).isNotNull();
        assertThat(result.getError().getMessage()).contains("template is required");
    }

    // ── Custom destination key ──

    @Test
    void customDestinationKey() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice"));
        Map<String, Object> params = mutableMap(
                "operation", "generateHtmlTemplate",
                "html", "<p>{{ name }}</p>",
                "destinationKey", "renderedHtml"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsKey("renderedHtml");
        assertThat(firstJson(result).get("renderedHtml")).isEqualTo("<p>Alice</p>");
    }
}
