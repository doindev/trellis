package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class MarkdownNodeTest {

    private MarkdownNode node;

    @BeforeEach
    void setUp() {
        node = new MarkdownNode();
    }

    // ── Markdown to HTML: headings ──

    @Test
    void markdownToHtmlH1() {
        List<Map<String, Object>> input = items(Map.of("data", "# Hello"));
        Map<String, Object> params = mutableMap(
                "mode", "markdownToHtml",
                "sourceField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String html = (String) firstJson(result).get("data");
        assertThat(html).contains("<h1>Hello</h1>");
    }

    @Test
    void markdownToHtmlH2() {
        List<Map<String, Object>> input = items(Map.of("data", "## Sub Title"));
        Map<String, Object> params = mutableMap(
                "mode", "markdownToHtml",
                "sourceField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String html = (String) firstJson(result).get("data");
        assertThat(html).contains("<h2>Sub Title</h2>");
    }

    @Test
    void markdownToHtmlH3() {
        List<Map<String, Object>> input = items(Map.of("data", "### Level 3"));
        Map<String, Object> params = mutableMap(
                "mode", "markdownToHtml",
                "sourceField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String html = (String) firstJson(result).get("data");
        assertThat(html).contains("<h3>Level 3</h3>");
    }

    // ── Markdown to HTML: bold ──

    @Test
    void markdownToHtmlBold() {
        List<Map<String, Object>> input = items(Map.of("data", "This is **bold** text"));
        Map<String, Object> params = mutableMap(
                "mode", "markdownToHtml",
                "sourceField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String html = (String) firstJson(result).get("data");
        assertThat(html).contains("<strong>bold</strong>");
    }

    // ── Markdown to HTML: italic ──

    @Test
    void markdownToHtmlItalic() {
        List<Map<String, Object>> input = items(Map.of("data", "This is *italic* text"));
        Map<String, Object> params = mutableMap(
                "mode", "markdownToHtml",
                "sourceField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String html = (String) firstJson(result).get("data");
        assertThat(html).contains("<em>italic</em>");
    }

    // ── Markdown to HTML: code blocks ──

    @Test
    void markdownToHtmlCodeBlock() {
        String md = "```java\nint x = 42;\n```";
        List<Map<String, Object>> input = items(Map.of("data", md));
        Map<String, Object> params = mutableMap(
                "mode", "markdownToHtml",
                "sourceField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String html = (String) firstJson(result).get("data");
        assertThat(html).contains("<pre><code");
        assertThat(html).contains("language-java");
        assertThat(html).contains("int x = 42;");
    }

    @Test
    void markdownToHtmlInlineCode() {
        List<Map<String, Object>> input = items(Map.of("data", "Use `println()` to print"));
        Map<String, Object> params = mutableMap(
                "mode", "markdownToHtml",
                "sourceField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String html = (String) firstJson(result).get("data");
        assertThat(html).contains("<code>println()</code>");
    }

    // ── Markdown to HTML: links ──

    @Test
    void markdownToHtmlLinks() {
        List<Map<String, Object>> input = items(Map.of("data", "Visit [Google](https://google.com)"));
        Map<String, Object> params = mutableMap(
                "mode", "markdownToHtml",
                "sourceField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String html = (String) firstJson(result).get("data");
        assertThat(html).contains("<a href=\"https://google.com\">Google</a>");
    }

    // ── Markdown to HTML: unordered lists ──

    @Test
    void markdownToHtmlUnorderedList() {
        String md = "- Item 1\n- Item 2\n- Item 3";
        List<Map<String, Object>> input = items(Map.of("data", md));
        Map<String, Object> params = mutableMap(
                "mode", "markdownToHtml",
                "sourceField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String html = (String) firstJson(result).get("data");
        assertThat(html).contains("<ul>");
        assertThat(html).contains("<li>Item 1</li>");
        assertThat(html).contains("<li>Item 2</li>");
        assertThat(html).contains("<li>Item 3</li>");
        assertThat(html).contains("</ul>");
    }

    // ── Markdown to HTML: ordered lists ──

    @Test
    void markdownToHtmlOrderedList() {
        String md = "1. First\n2. Second\n3. Third";
        List<Map<String, Object>> input = items(Map.of("data", md));
        Map<String, Object> params = mutableMap(
                "mode", "markdownToHtml",
                "sourceField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String html = (String) firstJson(result).get("data");
        assertThat(html).contains("<ol>");
        assertThat(html).contains("<li>First</li>");
        assertThat(html).contains("<li>Second</li>");
        assertThat(html).contains("<li>Third</li>");
        assertThat(html).contains("</ol>");
    }

    // ── Markdown to HTML: blockquotes ──

    @Test
    void markdownToHtmlBlockquote() {
        List<Map<String, Object>> input = items(Map.of("data", "> This is a quote"));
        Map<String, Object> params = mutableMap(
                "mode", "markdownToHtml",
                "sourceField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String html = (String) firstJson(result).get("data");
        assertThat(html).contains("<blockquote>");
        assertThat(html).contains("This is a quote");
    }

    // ── Markdown to HTML: horizontal rule ──

    @Test
    void markdownToHtmlHorizontalRule() {
        List<Map<String, Object>> input = items(Map.of("data", "---"));
        Map<String, Object> params = mutableMap(
                "mode", "markdownToHtml",
                "sourceField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String html = (String) firstJson(result).get("data");
        assertThat(html).contains("<hr>");
    }

    // ── HTML to Markdown ──

    @Test
    void htmlToMarkdownHeadings() {
        List<Map<String, Object>> input = items(Map.of("data", "<h1>Title</h1>"));
        Map<String, Object> params = mutableMap(
                "mode", "htmlToMarkdown",
                "sourceField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String md = (String) firstJson(result).get("data");
        assertThat(md).contains("# Title");
    }

    @Test
    void htmlToMarkdownBold() {
        List<Map<String, Object>> input = items(Map.of("data", "<p>This is <strong>bold</strong> text</p>"));
        Map<String, Object> params = mutableMap(
                "mode", "htmlToMarkdown",
                "sourceField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String md = (String) firstJson(result).get("data");
        assertThat(md).contains("**bold**");
    }

    @Test
    void htmlToMarkdownItalic() {
        List<Map<String, Object>> input = items(Map.of("data", "<p>This is <em>italic</em> text</p>"));
        Map<String, Object> params = mutableMap(
                "mode", "htmlToMarkdown",
                "sourceField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String md = (String) firstJson(result).get("data");
        assertThat(md).contains("*italic*");
    }

    @Test
    void htmlToMarkdownLinks() {
        List<Map<String, Object>> input = items(Map.of("data", "<a href=\"https://example.com\">Link</a>"));
        Map<String, Object> params = mutableMap(
                "mode", "htmlToMarkdown",
                "sourceField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String md = (String) firstJson(result).get("data");
        assertThat(md).contains("[Link](https://example.com)");
    }

    @Test
    void htmlToMarkdownList() {
        List<Map<String, Object>> input = items(Map.of("data", "<ul><li>A</li><li>B</li></ul>"));
        Map<String, Object> params = mutableMap(
                "mode", "htmlToMarkdown",
                "sourceField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String md = (String) firstJson(result).get("data");
        assertThat(md).contains("- A");
        assertThat(md).contains("- B");
    }

    // ── Custom source/destination fields ──

    @Test
    void customSourceAndDestinationFields() {
        List<Map<String, Object>> input = items(Map.of("mdContent", "# Hello"));
        Map<String, Object> params = mutableMap(
                "mode", "markdownToHtml",
                "sourceField", "mdContent",
                "destinationKey", "htmlOutput"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsKey("htmlOutput");
        assertThat(firstJson(result)).containsKey("mdContent");
        String html = (String) firstJson(result).get("htmlOutput");
        assertThat(html).contains("<h1>Hello</h1>");
    }

    // ── Empty input ──

    @Test
    void emptyInputReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "mode", "markdownToHtml",
                "sourceField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).isEmpty();
    }

    // ── Multiple items ──

    @Test
    void multipleItems() {
        List<Map<String, Object>> input = items(
                Map.of("data", "# Title 1"),
                Map.of("data", "# Title 2")
        );
        Map<String, Object> params = mutableMap(
                "mode", "markdownToHtml",
                "sourceField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(2);
        assertThat((String) jsonAt(result, 0).get("data")).contains("<h1>Title 1</h1>");
        assertThat((String) jsonAt(result, 1).get("data")).contains("<h1>Title 2</h1>");
    }

    // ── Empty markdown produces empty string ──

    @Test
    void emptyMarkdownProducesEmptyString() {
        List<Map<String, Object>> input = items(Map.of("data", ""));
        Map<String, Object> params = mutableMap(
                "mode", "markdownToHtml",
                "sourceField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String html = (String) firstJson(result).get("data");
        assertThat(html).isEmpty();
    }

    // ── Strikethrough ──

    @Test
    void markdownToHtmlStrikethrough() {
        List<Map<String, Object>> input = items(Map.of("data", "This is ~~deleted~~ text"));
        Map<String, Object> params = mutableMap(
                "mode", "markdownToHtml",
                "sourceField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String html = (String) firstJson(result).get("data");
        assertThat(html).contains("<del>deleted</del>");
    }

    // ── Image ──

    @Test
    void markdownToHtmlImage() {
        List<Map<String, Object>> input = items(Map.of("data", "![alt text](https://example.com/img.png)"));
        Map<String, Object> params = mutableMap(
                "mode", "markdownToHtml",
                "sourceField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String html = (String) firstJson(result).get("data");
        assertThat(html).contains("<img src=\"https://example.com/img.png\" alt=\"alt text\">");
    }
}
