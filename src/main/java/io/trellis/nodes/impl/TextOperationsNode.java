package io.trellis.nodes.impl;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
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
 * Text Operations Node - performs various string transformations on item fields.
 */
@Slf4j
@Node(
	type = "textOperations",
	displayName = "Text Operations",
	description = "Perform string transformations: case conversion, trim, replace, split, join, encode/decode, and more.",
	category = "Data Transformation",
	icon = "type"
)
public class TextOperationsNode extends AbstractNode {

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
				.description("The text operation to perform.")
				.type(ParameterType.OPTIONS)
				.defaultValue("trim")
				.required(true)
				.options(List.of(
					ParameterOption.builder().name("Trim").value("trim").description("Remove leading/trailing whitespace").build(),
					ParameterOption.builder().name("To Upper Case").value("toUpperCase").description("Convert to uppercase").build(),
					ParameterOption.builder().name("To Lower Case").value("toLowerCase").description("Convert to lowercase").build(),
					ParameterOption.builder().name("Title Case").value("titleCase").description("Capitalize first letter of each word").build(),
					ParameterOption.builder().name("Camel Case").value("camelCase").description("Convert to camelCase").build(),
					ParameterOption.builder().name("Snake Case").value("snakeCase").description("Convert to snake_case").build(),
					ParameterOption.builder().name("Slugify").value("slugify").description("Convert to URL-friendly slug").build(),
					ParameterOption.builder().name("Replace").value("replace").description("Replace occurrences of a string").build(),
					ParameterOption.builder().name("Regex Replace").value("regexReplace").description("Replace using a regex pattern").build(),
					ParameterOption.builder().name("Split").value("split").description("Split string into an array").build(),
					ParameterOption.builder().name("Join").value("join").description("Join an array into a string").build(),
					ParameterOption.builder().name("Truncate").value("truncate").description("Truncate to a max length").build(),
					ParameterOption.builder().name("Pad").value("pad").description("Pad string to a target length").build(),
					ParameterOption.builder().name("Extract (Regex)").value("extract").description("Extract matching text using regex").build(),
					ParameterOption.builder().name("Template").value("template").description("Apply a template string").build(),
					ParameterOption.builder().name("Base64 Encode").value("base64Encode").description("Encode to Base64").build(),
					ParameterOption.builder().name("Base64 Decode").value("base64Decode").description("Decode from Base64").build(),
					ParameterOption.builder().name("URL Encode").value("urlEncode").description("URL-encode the string").build(),
					ParameterOption.builder().name("URL Decode").value("urlDecode").description("URL-decode the string").build(),
					ParameterOption.builder().name("Length").value("length").description("Get string length").build()
				))
				.build(),

			NodeParameter.builder()
				.name("inputField")
				.displayName("Input Field")
				.description("The field to read the text value from.")
				.type(ParameterType.STRING)
				.defaultValue("text")
				.required(true)
				.placeHolder("fieldName")
				.build(),

			NodeParameter.builder()
				.name("outputField")
				.displayName("Output Field")
				.description("The field to store the result in. Leave empty to overwrite the input field.")
				.type(ParameterType.STRING)
				.defaultValue("")
				.placeHolder("result (default: same as input)")
				.build(),

			// Replace params
			NodeParameter.builder()
				.name("searchValue")
				.displayName("Search Value")
				.description("The string to search for.")
				.type(ParameterType.STRING)
				.displayOptions(Map.of("show", Map.of("operation", List.of("replace", "regexReplace"))))
				.required(true)
				.build(),

			NodeParameter.builder()
				.name("replaceValue")
				.displayName("Replace With")
				.description("The replacement string.")
				.type(ParameterType.STRING)
				.defaultValue("")
				.displayOptions(Map.of("show", Map.of("operation", List.of("replace", "regexReplace"))))
				.build(),

			NodeParameter.builder()
				.name("replaceAll")
				.displayName("Replace All")
				.description("Replace all occurrences (not just the first).")
				.type(ParameterType.BOOLEAN)
				.defaultValue(true)
				.displayOptions(Map.of("show", Map.of("operation", List.of("replace"))))
				.build(),

			// Split params
			NodeParameter.builder()
				.name("separator")
				.displayName("Separator")
				.description("The delimiter to split/join on.")
				.type(ParameterType.STRING)
				.defaultValue(",")
				.displayOptions(Map.of("show", Map.of("operation", List.of("split", "join"))))
				.build(),

			// Truncate params
			NodeParameter.builder()
				.name("maxLength")
				.displayName("Max Length")
				.description("The maximum length of the string.")
				.type(ParameterType.NUMBER)
				.defaultValue(100)
				.displayOptions(Map.of("show", Map.of("operation", List.of("truncate"))))
				.build(),

			NodeParameter.builder()
				.name("suffix")
				.displayName("Suffix")
				.description("Suffix to append when truncated (e.g. '...').")
				.type(ParameterType.STRING)
				.defaultValue("...")
				.displayOptions(Map.of("show", Map.of("operation", List.of("truncate"))))
				.build(),

			// Pad params
			NodeParameter.builder()
				.name("targetLength")
				.displayName("Target Length")
				.description("The target length to pad to.")
				.type(ParameterType.NUMBER)
				.defaultValue(10)
				.displayOptions(Map.of("show", Map.of("operation", List.of("pad"))))
				.build(),

			NodeParameter.builder()
				.name("padChar")
				.displayName("Pad Character")
				.description("The character to pad with.")
				.type(ParameterType.STRING)
				.defaultValue(" ")
				.displayOptions(Map.of("show", Map.of("operation", List.of("pad"))))
				.build(),

			NodeParameter.builder()
				.name("padDirection")
				.displayName("Pad Direction")
				.description("Which side to pad.")
				.type(ParameterType.OPTIONS)
				.defaultValue("start")
				.displayOptions(Map.of("show", Map.of("operation", List.of("pad"))))
				.options(List.of(
					ParameterOption.builder().name("Start").value("start").build(),
					ParameterOption.builder().name("End").value("end").build()
				))
				.build(),

			// Extract params
			NodeParameter.builder()
				.name("regexPattern")
				.displayName("Regex Pattern")
				.description("The regex pattern to extract. Use groups to capture specific parts.")
				.type(ParameterType.STRING)
				.displayOptions(Map.of("show", Map.of("operation", List.of("extract"))))
				.required(true)
				.build(),

			// Template params
			NodeParameter.builder()
				.name("template")
				.displayName("Template")
				.description("Template string with {{fieldName}} placeholders.")
				.type(ParameterType.STRING)
				.typeOptions(Map.of("rows", 4))
				.displayOptions(Map.of("show", Map.of("operation", List.of("template"))))
				.required(true)
				.placeHolder("Hello {{name}}, your order #{{orderId}} is ready.")
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.empty();
		}

		String operation = context.getParameter("operation", "trim");
		String inputField = context.getParameter("inputField", "text");
		String outputField = context.getParameter("outputField", "");
		if (outputField == null || outputField.isEmpty()) {
			outputField = inputField;
		}

		List<Map<String, Object>> result = new ArrayList<>();

		for (Map<String, Object> item : inputData) {
			try {
				Map<String, Object> json = new LinkedHashMap<>(unwrapJson(item));
				Object rawValue = getNestedValue(item, inputField);

				Object transformed = applyOperation(operation, rawValue, json, context);

				setNestedValue(json, outputField, transformed);
				result.add(wrapInJson(json));
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					Map<String, Object> errorJson = new LinkedHashMap<>(unwrapJson(item));
					errorJson.put("error", e.getMessage());
					result.add(wrapInJson(errorJson));
				} else {
					return handleError(context, "Text operation '" + operation + "' failed: " + e.getMessage(), e);
				}
			}
		}

		return NodeExecutionResult.success(result);
	}

	private Object applyOperation(String operation, Object rawValue, Map<String, Object> json,
			NodeExecutionContext context) {
		String text = rawValue != null ? String.valueOf(rawValue) : "";

		switch (operation) {
			case "trim":
				return text.trim();

			case "toUpperCase":
				return text.toUpperCase();

			case "toLowerCase":
				return text.toLowerCase();

			case "titleCase":
				return toTitleCase(text);

			case "camelCase":
				return toCamelCase(text);

			case "snakeCase":
				return toSnakeCase(text);

			case "slugify":
				return slugify(text);

			case "replace": {
				String search = context.getParameter("searchValue", "");
				String replace = context.getParameter("replaceValue", "");
				boolean all = toBoolean(context.getParameter("replaceAll", true), true);
				if (all) {
					return text.replace(search, replace);
				} else {
					int idx = text.indexOf(search);
					if (idx >= 0) {
						return text.substring(0, idx) + replace + text.substring(idx + search.length());
					}
					return text;
				}
			}

			case "regexReplace": {
				String pattern = context.getParameter("searchValue", "");
				String replace = context.getParameter("replaceValue", "");
				return text.replaceAll(pattern, replace);
			}

			case "split": {
				String separator = context.getParameter("separator", ",");
				return Arrays.asList(text.split(Pattern.quote(separator)));
			}

			case "join": {
				String separator = context.getParameter("separator", ",");
				if (rawValue instanceof List) {
					List<?> list = (List<?>) rawValue;
					StringBuilder sb = new StringBuilder();
					for (int i = 0; i < list.size(); i++) {
						if (i > 0) sb.append(separator);
						sb.append(list.get(i) != null ? String.valueOf(list.get(i)) : "");
					}
					return sb.toString();
				}
				return text;
			}

			case "truncate": {
				int maxLen = toInt(context.getParameter("maxLength", 100), 100);
				String suffix = context.getParameter("suffix", "...");
				if (text.length() <= maxLen) return text;
				int cutAt = Math.max(0, maxLen - suffix.length());
				return text.substring(0, cutAt) + suffix;
			}

			case "pad": {
				int targetLen = toInt(context.getParameter("targetLength", 10), 10);
				String padChar = context.getParameter("padChar", " ");
				String direction = context.getParameter("padDirection", "start");
				if (padChar.isEmpty()) padChar = " ";
				char ch = padChar.charAt(0);
				StringBuilder sb = new StringBuilder(text);
				while (sb.length() < targetLen) {
					if ("start".equals(direction)) {
						sb.insert(0, ch);
					} else {
						sb.append(ch);
					}
				}
				return sb.toString();
			}

			case "extract": {
				String pattern = context.getParameter("regexPattern", "");
				Matcher matcher = Pattern.compile(pattern).matcher(text);
				if (matcher.find()) {
					return matcher.groupCount() > 0 ? matcher.group(1) : matcher.group();
				}
				return null;
			}

			case "template": {
				String template = context.getParameter("template", "");
				Matcher matcher = Pattern.compile("\\{\\{(\\w+(?:\\.\\w+)*)\\}\\}").matcher(template);
				StringBuilder sb = new StringBuilder();
				while (matcher.find()) {
					String fieldName = matcher.group(1);
					Object val = json.get(fieldName);
					matcher.appendReplacement(sb, Matcher.quoteReplacement(val != null ? String.valueOf(val) : ""));
				}
				matcher.appendTail(sb);
				return sb.toString();
			}

			case "base64Encode":
				return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));

			case "base64Decode":
				return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);

			case "urlEncode":
				return URLEncoder.encode(text, StandardCharsets.UTF_8);

			case "urlDecode":
				return URLDecoder.decode(text, StandardCharsets.UTF_8);

			case "length":
				return text.length();

			default:
				return text;
		}
	}

	private String toTitleCase(String text) {
		if (text.isEmpty()) return text;
		StringBuilder sb = new StringBuilder();
		boolean capitalizeNext = true;
		for (char c : text.toCharArray()) {
			if (Character.isWhitespace(c) || c == '-' || c == '_') {
				capitalizeNext = true;
				sb.append(c);
			} else if (capitalizeNext) {
				sb.append(Character.toUpperCase(c));
				capitalizeNext = false;
			} else {
				sb.append(Character.toLowerCase(c));
			}
		}
		return sb.toString();
	}

	private String toCamelCase(String text) {
		String[] words = text.split("[\\s_\\-]+");
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < words.length; i++) {
			String word = words[i].toLowerCase();
			if (word.isEmpty()) continue;
			if (sb.isEmpty()) {
				sb.append(word);
			} else {
				sb.append(Character.toUpperCase(word.charAt(0)));
				sb.append(word.substring(1));
			}
		}
		return sb.toString();
	}

	private String toSnakeCase(String text) {
		// Insert underscore before uppercase letters, then lowercase everything
		String result = text.replaceAll("([a-z])([A-Z])", "$1_$2");
		result = result.replaceAll("[\\s\\-]+", "_");
		return result.toLowerCase();
	}

	private String slugify(String text) {
		return text.toLowerCase()
			.replaceAll("[^a-z0-9\\s-]", "")
			.replaceAll("[\\s]+", "-")
			.replaceAll("-+", "-")
			.replaceAll("^-|-$", "");
	}
}
