package io.trellis.nodes.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Markdown Node - converts between Markdown and HTML.
 */
@Slf4j
@Node(
	type = "markdown",
	displayName = "Markdown",
	description = "Convert between Markdown and HTML.",
	category = "Data Transformation",
	icon = "file-text"
)
public class MarkdownNode extends AbstractNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("mode")
				.displayName("Mode")
				.type(ParameterType.OPTIONS)
				.defaultValue("markdownToHtml")
				.options(List.of(
					ParameterOption.builder().name("Markdown to HTML").value("markdownToHtml")
						.description("Convert Markdown text to HTML").build(),
					ParameterOption.builder().name("HTML to Markdown").value("htmlToMarkdown")
						.description("Convert HTML to Markdown text").build()
				))
				.build(),

			NodeParameter.builder()
				.name("sourceField")
				.displayName("Source Field")
				.description("The field containing the content to convert.")
				.type(ParameterType.STRING)
				.defaultValue("data")
				.required(true)
				.build(),

			NodeParameter.builder()
				.name("destinationKey")
				.displayName("Output Field")
				.description("The field name for the converted output.")
				.type(ParameterType.STRING)
				.defaultValue("data")
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.empty();
		}

		String mode = context.getParameter("mode", "markdownToHtml");
		String sourceField = context.getParameter("sourceField", "data");
		String destinationKey = context.getParameter("destinationKey", "data");

		try {
			List<Map<String, Object>> result = new ArrayList<>();

			for (Map<String, Object> item : inputData) {
				Map<String, Object> json = unwrapJson(item);
				String source = String.valueOf(json.getOrDefault(sourceField, ""));

				String converted;
				if ("markdownToHtml".equals(mode)) {
					converted = markdownToHtml(source);
				} else {
					converted = htmlToMarkdown(source);
				}

				Map<String, Object> outputJson = new LinkedHashMap<>(json);
				outputJson.put(destinationKey, converted);
				result.add(wrapInJson(outputJson));
			}

			log.debug("Markdown: mode={}, {} items processed", mode, result.size());
			return NodeExecutionResult.success(result);
		} catch (Exception e) {
			return handleError(context, "Markdown node error: " + e.getMessage(), e);
		}
	}

	/**
	 * Convert Markdown to HTML. Handles: headings, bold, italic, strikethrough,
	 * code blocks, inline code, links, images, lists, blockquotes, horizontal rules, paragraphs.
	 */
	private String markdownToHtml(String markdown) {
		if (markdown == null || markdown.isEmpty()) return "";

		String[] lines = markdown.split("\n");
		StringBuilder html = new StringBuilder();
		boolean inCodeBlock = false;
		boolean inList = false;
		boolean inOrderedList = false;
		String codeBlockLang = "";

		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];

			// Fenced code blocks
			if (line.trim().startsWith("```")) {
				if (inCodeBlock) {
					html.append("</code></pre>\n");
					inCodeBlock = false;
				} else {
					codeBlockLang = line.trim().substring(3).trim();
					html.append("<pre><code");
					if (!codeBlockLang.isEmpty()) {
						html.append(" class=\"language-").append(escapeHtml(codeBlockLang)).append("\"");
					}
					html.append(">");
					inCodeBlock = true;
				}
				continue;
			}

			if (inCodeBlock) {
				html.append(escapeHtml(line)).append("\n");
				continue;
			}

			// Close lists if not a list item
			if (inList && !line.trim().startsWith("- ") && !line.trim().startsWith("* ")) {
				html.append("</ul>\n");
				inList = false;
			}
			if (inOrderedList && !line.trim().matches("^\\d+\\.\\s.*")) {
				html.append("</ol>\n");
				inOrderedList = false;
			}

			// Horizontal rule
			if (line.trim().matches("^---+$") || line.trim().matches("^\\*\\*\\*+$")) {
				html.append("<hr>\n");
				continue;
			}

			// Headings
			if (line.startsWith("######")) {
				html.append("<h6>").append(processInline(line.substring(6).trim())).append("</h6>\n");
				continue;
			}
			if (line.startsWith("#####")) {
				html.append("<h5>").append(processInline(line.substring(5).trim())).append("</h5>\n");
				continue;
			}
			if (line.startsWith("####")) {
				html.append("<h4>").append(processInline(line.substring(4).trim())).append("</h4>\n");
				continue;
			}
			if (line.startsWith("###")) {
				html.append("<h3>").append(processInline(line.substring(3).trim())).append("</h3>\n");
				continue;
			}
			if (line.startsWith("##")) {
				html.append("<h2>").append(processInline(line.substring(2).trim())).append("</h2>\n");
				continue;
			}
			if (line.startsWith("#")) {
				html.append("<h1>").append(processInline(line.substring(1).trim())).append("</h1>\n");
				continue;
			}

			// Blockquotes
			if (line.startsWith("> ")) {
				html.append("<blockquote>").append(processInline(line.substring(2))).append("</blockquote>\n");
				continue;
			}

			// Unordered lists
			if (line.trim().startsWith("- ") || line.trim().startsWith("* ")) {
				if (!inList) {
					html.append("<ul>\n");
					inList = true;
				}
				String listContent = line.trim().substring(2);
				html.append("<li>").append(processInline(listContent)).append("</li>\n");
				continue;
			}

			// Ordered lists
			Matcher olMatcher = Pattern.compile("^(\\d+)\\.\\s(.*)").matcher(line.trim());
			if (olMatcher.matches()) {
				if (!inOrderedList) {
					html.append("<ol>\n");
					inOrderedList = true;
				}
				html.append("<li>").append(processInline(olMatcher.group(2))).append("</li>\n");
				continue;
			}

			// Empty line
			if (line.trim().isEmpty()) {
				html.append("\n");
				continue;
			}

			// Paragraph
			html.append("<p>").append(processInline(line)).append("</p>\n");
		}

		// Close any open blocks
		if (inCodeBlock) html.append("</code></pre>\n");
		if (inList) html.append("</ul>\n");
		if (inOrderedList) html.append("</ol>\n");

		return html.toString().trim();
	}

	/**
	 * Process inline Markdown: bold, italic, strikethrough, code, links, images.
	 */
	private String processInline(String text) {
		if (text == null || text.isEmpty()) return "";

		// Inline code (must be first to avoid processing inside code)
		text = text.replaceAll("`([^`]+)`", "<code>$1</code>");

		// Images: ![alt](url)
		text = text.replaceAll("!\\[([^\\]]*)\\]\\(([^)]+)\\)", "<img src=\"$2\" alt=\"$1\">");

		// Links: [text](url)
		text = text.replaceAll("\\[([^\\]]*)\\]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>");

		// Bold + Italic: ***text*** or ___text___
		text = text.replaceAll("\\*\\*\\*([^*]+)\\*\\*\\*", "<strong><em>$1</em></strong>");
		text = text.replaceAll("___([^_]+)___", "<strong><em>$1</em></strong>");

		// Bold: **text** or __text__
		text = text.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
		text = text.replaceAll("__([^_]+)__", "<strong>$1</strong>");

		// Italic: *text* or _text_
		text = text.replaceAll("\\*([^*]+)\\*", "<em>$1</em>");
		text = text.replaceAll("(?<=\\s|^)_([^_]+)_(?=\\s|$)", "<em>$1</em>");

		// Strikethrough: ~~text~~
		text = text.replaceAll("~~([^~]+)~~", "<del>$1</del>");

		return text;
	}

	/**
	 * Convert HTML to Markdown. Handles common HTML elements.
	 */
	private String htmlToMarkdown(String html) {
		if (html == null || html.isEmpty()) return "";

		String md = html;

		// Remove doctype, html, head, body wrappers
		md = md.replaceAll("<!DOCTYPE[^>]*>", "");
		md = md.replaceAll("</?html[^>]*>", "");
		md = md.replaceAll("<head[\\s\\S]*?</head>", "");
		md = md.replaceAll("</?body[^>]*>", "");

		// Headings
		md = md.replaceAll("<h1[^>]*>(.*?)</h1>", "# $1\n\n");
		md = md.replaceAll("<h2[^>]*>(.*?)</h2>", "## $1\n\n");
		md = md.replaceAll("<h3[^>]*>(.*?)</h3>", "### $1\n\n");
		md = md.replaceAll("<h4[^>]*>(.*?)</h4>", "#### $1\n\n");
		md = md.replaceAll("<h5[^>]*>(.*?)</h5>", "##### $1\n\n");
		md = md.replaceAll("<h6[^>]*>(.*?)</h6>", "###### $1\n\n");

		// Bold
		md = md.replaceAll("<strong[^>]*>(.*?)</strong>", "**$1**");
		md = md.replaceAll("<b[^>]*>(.*?)</b>", "**$1**");

		// Italic
		md = md.replaceAll("<em[^>]*>(.*?)</em>", "*$1*");
		md = md.replaceAll("<i[^>]*>(.*?)</i>", "*$1*");

		// Strikethrough
		md = md.replaceAll("<del[^>]*>(.*?)</del>", "~~$1~~");
		md = md.replaceAll("<s[^>]*>(.*?)</s>", "~~$1~~");

		// Code blocks
		md = md.replaceAll("<pre[^>]*><code[^>]*>([\\s\\S]*?)</code></pre>", "```\n$1```\n\n");

		// Inline code
		md = md.replaceAll("<code[^>]*>(.*?)</code>", "`$1`");

		// Links
		md = md.replaceAll("<a[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>", "[$2]($1)");

		// Images
		md = md.replaceAll("<img[^>]*src=\"([^\"]*)\"[^>]*alt=\"([^\"]*)\"[^>]*/?>", "![$2]($1)");
		md = md.replaceAll("<img[^>]*src=\"([^\"]*)\"[^>]*/?>", "![]($1)");

		// Lists
		md = md.replaceAll("<li[^>]*>(.*?)</li>", "- $1\n");
		md = md.replaceAll("</?[ou]l[^>]*>", "\n");

		// Blockquotes
		md = md.replaceAll("<blockquote[^>]*>(.*?)</blockquote>", "> $1\n\n");

		// Paragraphs
		md = md.replaceAll("<p[^>]*>(.*?)</p>", "$1\n\n");

		// Line breaks
		md = md.replaceAll("<br\\s*/?>", "\n");

		// Horizontal rules
		md = md.replaceAll("<hr\\s*/?>", "---\n\n");

		// Strip remaining tags
		md = md.replaceAll("<[^>]+>", "");

		// Decode HTML entities
		md = md.replaceAll("&amp;", "&");
		md = md.replaceAll("&lt;", "<");
		md = md.replaceAll("&gt;", ">");
		md = md.replaceAll("&quot;", "\"");
		md = md.replaceAll("&nbsp;", " ");

		// Clean up whitespace
		md = md.replaceAll("\n{3,}", "\n\n");

		return md.trim();
	}

	private String escapeHtml(String text) {
		if (text == null) return "";
		return text.replace("&", "&amp;").replace("<", "&lt;")
			.replace(">", "&gt;").replace("\"", "&quot;");
	}
}
