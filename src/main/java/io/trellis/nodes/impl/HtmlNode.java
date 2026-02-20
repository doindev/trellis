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
 * HTML Node - processes HTML content.
 * Operations:
 * - Generate HTML template from data
 * - Extract content from HTML using CSS-like selectors
 * - Convert items to HTML table
 */
@Slf4j
@Node(
	type = "html",
	displayName = "HTML",
	description = "Generate HTML templates, extract content from HTML, or convert data to HTML tables.",
	category = "Data Transformation",
	icon = "file-code"
)
public class HtmlNode extends AbstractNode {

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
				.name("operation")
				.displayName("Operation")
				.type(ParameterType.OPTIONS)
				.defaultValue("generateHtmlTemplate")
				.options(List.of(
					ParameterOption.builder().name("Generate HTML Template").value("generateHtmlTemplate")
						.description("Generate HTML from a template using item data").build(),
					ParameterOption.builder().name("Extract HTML Content").value("extractHtmlContent")
						.description("Extract content from HTML using CSS-like tag selectors").build(),
					ParameterOption.builder().name("Convert to HTML Table").value("convertToTable")
						.description("Convert input items to an HTML table").build()
				))
				.build(),

			// Generate template params
			NodeParameter.builder()
				.name("html")
				.displayName("HTML Template")
				.description("HTML template. Use {{ fieldName }} placeholders to inject data.")
				.type(ParameterType.STRING)
				.typeOptions(Map.of("rows", 10))
				.displayOptions(Map.of("show", Map.of("operation", List.of("generateHtmlTemplate"))))
				.build(),

			// Extract params
			NodeParameter.builder()
				.name("sourceField")
				.displayName("Source Field")
				.description("The field containing HTML to extract from.")
				.type(ParameterType.STRING)
				.defaultValue("html")
				.displayOptions(Map.of("show", Map.of("operation", List.of("extractHtmlContent"))))
				.build(),

			NodeParameter.builder()
				.name("extractionValues")
				.displayName("Extraction Values")
				.description("Define what to extract from the HTML.")
				.type(ParameterType.FIXED_COLLECTION)
				.displayOptions(Map.of("show", Map.of("operation", List.of("extractHtmlContent"))))
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("key")
						.displayName("Key")
						.description("The output key name for this extraction.")
						.type(ParameterType.STRING)
						.required(true)
						.placeHolder("e.g. title")
						.build(),
					NodeParameter.builder()
						.name("cssSelector")
						.displayName("CSS Selector (Tag Name)")
						.description("HTML tag to search for (e.g. 'h1', 'a', 'p', 'div.class').")
						.type(ParameterType.STRING)
						.required(true)
						.placeHolder("e.g. h1")
						.build(),
					NodeParameter.builder()
						.name("returnValue")
						.displayName("Return Value")
						.description("What to return from the matched element.")
						.type(ParameterType.OPTIONS)
						.defaultValue("text")
						.options(List.of(
							ParameterOption.builder().name("Text Content").value("text").build(),
							ParameterOption.builder().name("Inner HTML").value("html").build(),
							ParameterOption.builder().name("Attribute Value").value("attribute").build()
						))
						.build(),
					NodeParameter.builder()
						.name("attribute")
						.displayName("Attribute")
						.description("The attribute name to extract (e.g. 'href', 'src').")
						.type(ParameterType.STRING)
						.displayOptions(Map.of("show", Map.of("returnValue", List.of("attribute"))))
						.build()
				))
				.build(),

			// Convert to table params
			NodeParameter.builder()
				.name("destinationKey")
				.displayName("Output Field")
				.description("The field name for the output.")
				.type(ParameterType.STRING)
				.defaultValue("html")
				.build(),

			NodeParameter.builder()
				.name("options")
				.displayName("Options")
				.type(ParameterType.COLLECTION)
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("tableStyle")
						.displayName("Table Style")
						.description("CSS style for the table element.")
						.type(ParameterType.STRING)
						.defaultValue("border-collapse: collapse; width: 100%;")
						.build(),
					NodeParameter.builder()
						.name("headerStyle")
						.displayName("Header Style")
						.description("CSS style for th elements.")
						.type(ParameterType.STRING)
						.defaultValue("border: 1px solid #ddd; padding: 8px; text-align: left; background-color: #f2f2f2;")
						.build(),
					NodeParameter.builder()
						.name("cellStyle")
						.displayName("Cell Style")
						.description("CSS style for td elements.")
						.type(ParameterType.STRING)
						.defaultValue("border: 1px solid #ddd; padding: 8px;")
						.build()
				))
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.empty();
		}

		String operation = context.getParameter("operation", "generateHtmlTemplate");
		String destinationKey = context.getParameter("destinationKey", "html");

		try {
			switch (operation) {
				case "generateHtmlTemplate":
					return generateTemplate(context, inputData, destinationKey);
				case "extractHtmlContent":
					return extractContent(context, inputData);
				case "convertToTable":
					return convertToTable(context, inputData, destinationKey);
				default:
					return NodeExecutionResult.error("Unknown operation: " + operation);
			}
		} catch (Exception e) {
			return handleError(context, "HTML node error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult generateTemplate(NodeExecutionContext context,
			List<Map<String, Object>> inputData, String destinationKey) {
		String template = context.getParameter("html", "");
		if (template == null || template.isBlank()) {
			return NodeExecutionResult.error("HTML template is required");
		}

		List<Map<String, Object>> result = new ArrayList<>();
		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = unwrapJson(item);
			String rendered = renderTemplate(template, json);
			Map<String, Object> outputJson = new LinkedHashMap<>(json);
			outputJson.put(destinationKey, rendered);
			result.add(wrapInJson(outputJson));
		}

		return NodeExecutionResult.success(result);
	}

	private String renderTemplate(String template, Map<String, Object> data) {
		String result = template;
		// Replace {{ fieldName }} placeholders
		Pattern pattern = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*\\}\\}");
		Matcher matcher = pattern.matcher(result);
		StringBuilder sb = new StringBuilder();
		while (matcher.find()) {
			String key = matcher.group(1);
			Object value = getValueByPath(data, key);
			matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? String.valueOf(value) : ""));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	@SuppressWarnings("unchecked")
	private Object getValueByPath(Map<String, Object> data, String path) {
		String[] parts = path.split("\\.");
		Object current = data;
		for (String part : parts) {
			if (current instanceof Map) {
				current = ((Map<String, Object>) current).get(part);
			} else {
				return null;
			}
		}
		return current;
	}

	private NodeExecutionResult extractContent(NodeExecutionContext context,
			List<Map<String, Object>> inputData) {
		String sourceField = context.getParameter("sourceField", "html");
		Object extractionParam = context.getParameter("extractionValues", null);
		List<ExtractionRule> rules = parseExtractionRules(extractionParam);

		if (rules.isEmpty()) {
			return NodeExecutionResult.error("No extraction rules specified");
		}

		List<Map<String, Object>> result = new ArrayList<>();
		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = unwrapJson(item);
			String html = String.valueOf(json.getOrDefault(sourceField, ""));

			Map<String, Object> outputJson = new LinkedHashMap<>(json);
			for (ExtractionRule rule : rules) {
				Object extracted = extractByTag(html, rule);
				outputJson.put(rule.key, extracted);
			}
			result.add(wrapInJson(outputJson));
		}

		return NodeExecutionResult.success(result);
	}

	/**
	 * Simple HTML tag extraction without external libraries.
	 * Supports basic tag names like "h1", "a", "p", "div".
	 * For tag.class format, matches tags with that class.
	 */
	private Object extractByTag(String html, ExtractionRule rule) {
		String selector = rule.cssSelector.trim();
		String tagName;
		String className = null;

		// Support tag.class notation
		if (selector.contains(".")) {
			String[] parts = selector.split("\\.", 2);
			tagName = parts[0];
			className = parts[1];
		} else {
			tagName = selector;
		}

		List<String> matches = new ArrayList<>();

		// Build regex for matching tag (with optional attributes)
		String tagPattern;
		if (className != null) {
			tagPattern = "<" + Pattern.quote(tagName) + "\\s+[^>]*class\\s*=\\s*\"[^\"]*\\b"
				+ Pattern.quote(className) + "\\b[^\"]*\"[^>]*>(.*?)</"
				+ Pattern.quote(tagName) + ">";
		} else {
			tagPattern = "<" + Pattern.quote(tagName) + "(?:\\s[^>]*)?>([\\s\\S]*?)</"
				+ Pattern.quote(tagName) + ">";
		}

		Pattern pattern = Pattern.compile(tagPattern, Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(html);

		while (matcher.find()) {
			String fullMatch = matcher.group(0);
			String innerContent = matcher.group(1);

			switch (rule.returnValue) {
				case "text":
					matches.add(stripHtml(innerContent).trim());
					break;
				case "html":
					matches.add(innerContent.trim());
					break;
				case "attribute":
					if (rule.attribute != null) {
						String attrValue = extractAttribute(fullMatch, rule.attribute);
						if (attrValue != null) matches.add(attrValue);
					}
					break;
				default:
					matches.add(stripHtml(innerContent).trim());
			}
		}

		// Also handle self-closing tags for attribute extraction
		if ("attribute".equals(rule.returnValue) && matches.isEmpty()) {
			String selfPattern = "<" + Pattern.quote(tagName) + "\\s[^>]*/>";
			Pattern selfClosing = Pattern.compile(selfPattern, Pattern.CASE_INSENSITIVE);
			Matcher selfMatcher = selfClosing.matcher(html);
			while (selfMatcher.find()) {
				String attrValue = extractAttribute(selfMatcher.group(0), rule.attribute);
				if (attrValue != null) matches.add(attrValue);
			}
		}

		return matches.size() == 1 ? matches.get(0) : matches;
	}

	private String extractAttribute(String tag, String attrName) {
		Pattern attrPattern = Pattern.compile(
			Pattern.quote(attrName) + "\\s*=\\s*\"([^\"]*)\"",
			Pattern.CASE_INSENSITIVE);
		Matcher m = attrPattern.matcher(tag);
		return m.find() ? m.group(1) : null;
	}

	private String stripHtml(String html) {
		return html.replaceAll("<[^>]*>", "").replaceAll("&nbsp;", " ")
			.replaceAll("&amp;", "&").replaceAll("&lt;", "<")
			.replaceAll("&gt;", ">").replaceAll("&quot;", "\"");
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult convertToTable(NodeExecutionContext context,
			List<Map<String, Object>> inputData, String destinationKey) {
		String tableStyle = "border-collapse: collapse; width: 100%;";
		String headerStyle = "border: 1px solid #ddd; padding: 8px; text-align: left; background-color: #f2f2f2;";
		String cellStyle = "border: 1px solid #ddd; padding: 8px;";

		Object optionsObj = context.getParameter("options", null);
		if (optionsObj instanceof Map) {
			Map<String, Object> opts = (Map<String, Object>) optionsObj;
			if (opts.get("tableStyle") != null) tableStyle = String.valueOf(opts.get("tableStyle"));
			if (opts.get("headerStyle") != null) headerStyle = String.valueOf(opts.get("headerStyle"));
			if (opts.get("cellStyle") != null) cellStyle = String.valueOf(opts.get("cellStyle"));
		}

		// Collect all column names
		List<String> columns = new ArrayList<>();
		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = unwrapJson(item);
			for (String key : json.keySet()) {
				if (!columns.contains(key)) columns.add(key);
			}
		}

		// Build HTML table
		StringBuilder sb = new StringBuilder();
		sb.append("<table style=\"").append(escapeHtml(tableStyle)).append("\">\n");

		// Header row
		sb.append("<thead><tr>");
		for (String col : columns) {
			sb.append("<th style=\"").append(escapeHtml(headerStyle)).append("\">")
				.append(escapeHtml(col)).append("</th>");
		}
		sb.append("</tr></thead>\n");

		// Data rows
		sb.append("<tbody>");
		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = unwrapJson(item);
			sb.append("<tr>");
			for (String col : columns) {
				Object value = json.get(col);
				String display = value != null ? String.valueOf(value) : "";
				sb.append("<td style=\"").append(escapeHtml(cellStyle)).append("\">")
					.append(escapeHtml(display)).append("</td>");
			}
			sb.append("</tr>\n");
		}
		sb.append("</tbody></table>");

		Map<String, Object> outputJson = new LinkedHashMap<>();
		outputJson.put(destinationKey, sb.toString());

		return NodeExecutionResult.success(List.of(wrapInJson(outputJson)));
	}

	private String escapeHtml(String text) {
		if (text == null) return "";
		return text.replace("&", "&amp;").replace("<", "&lt;")
			.replace(">", "&gt;").replace("\"", "&quot;");
	}

	@SuppressWarnings("unchecked")
	private List<ExtractionRule> parseExtractionRules(Object param) {
		List<ExtractionRule> rules = new ArrayList<>();
		if (param == null) return rules;

		List<?> list;
		if (param instanceof Map) {
			Object values = ((Map<String, Object>) param).get("values");
			list = values instanceof List ? (List<?>) values : List.of(param);
		} else if (param instanceof List) {
			list = (List<?>) param;
		} else {
			return rules;
		}

		for (Object entry : list) {
			if (entry instanceof Map) {
				Map<String, Object> map = (Map<String, Object>) entry;
				String key = (String) map.get("key");
				String cssSelector = (String) map.get("cssSelector");
				String returnValue = (String) map.getOrDefault("returnValue", "text");
				String attribute = (String) map.get("attribute");
				if (key != null && cssSelector != null) {
					rules.add(new ExtractionRule(key, cssSelector, returnValue, attribute));
				}
			}
		}
		return rules;
	}

	private record ExtractionRule(String key, String cssSelector, String returnValue, String attribute) {}
}
