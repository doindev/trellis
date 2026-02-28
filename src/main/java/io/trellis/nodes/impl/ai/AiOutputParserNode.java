package io.trellis.nodes.impl.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
 * AI Output Parser Node - extracts structured data from LLM text output.
 * Parses JSON blocks, key-value pairs, sections, lists, or regex matches.
 */
@Slf4j
@Node(
	type = "aiOutputParser",
	displayName = "AI Output Parser",
	description = "Extract structured data from AI/LLM text output: JSON blocks, key-value pairs, sections, lists, or regex.",
	category = "AI / Output Parsers",
	icon = "file-json"
)
public class AiOutputParserNode extends AbstractNode {

	private static final ObjectMapper MAPPER = new ObjectMapper();

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
				.description("How to parse the AI output.")
				.type(ParameterType.OPTIONS)
				.defaultValue("extractJson")
				.required(true)
				.options(List.of(
					ParameterOption.builder().name("Extract JSON").value("extractJson")
						.description("Find and parse JSON objects/arrays in text").build(),
					ParameterOption.builder().name("Extract Key-Value Pairs").value("extractKeyValue")
						.description("Parse 'key: value' lines").build(),
					ParameterOption.builder().name("Extract Sections").value("extractSections")
						.description("Split text by headings into sections").build(),
					ParameterOption.builder().name("Extract List").value("extractList")
						.description("Parse numbered or bulleted lists").build(),
					ParameterOption.builder().name("Regex").value("regex")
						.description("Apply a custom regex pattern").build()
				))
				.build(),

			NodeParameter.builder()
				.name("inputField")
				.displayName("Input Field")
				.description("The field containing the AI text output.")
				.type(ParameterType.STRING)
				.defaultValue("output")
				.required(true)
				.build(),

			NodeParameter.builder()
				.name("outputField")
				.displayName("Output Field")
				.description("The field to store the parsed result in.")
				.type(ParameterType.STRING)
				.defaultValue("parsed")
				.build(),

			// Regex params
			NodeParameter.builder()
				.name("regexPattern")
				.displayName("Regex Pattern")
				.description("The regex pattern to apply. Use named groups like (?<name>...) for structured output.")
				.type(ParameterType.STRING)
				.displayOptions(Map.of("show", Map.of("operation", List.of("regex"))))
				.required(true)
				.build(),

			NodeParameter.builder()
				.name("allMatches")
				.displayName("All Matches")
				.description("Return all matches (not just the first).")
				.type(ParameterType.BOOLEAN)
				.defaultValue(true)
				.displayOptions(Map.of("show", Map.of("operation", List.of("regex"))))
				.build(),

			// Section params
			NodeParameter.builder()
				.name("sectionPattern")
				.displayName("Section Heading Pattern")
				.description("Regex pattern to identify section headings. Default matches markdown headings (## Heading).")
				.type(ParameterType.STRING)
				.defaultValue("^#{1,6}\\s+(.+)$")
				.displayOptions(Map.of("show", Map.of("operation", List.of("extractSections"))))
				.build(),

			// Key-value params
			NodeParameter.builder()
				.name("kvSeparator")
				.displayName("Separator")
				.description("The separator between keys and values.")
				.type(ParameterType.STRING)
				.defaultValue(":")
				.displayOptions(Map.of("show", Map.of("operation", List.of("extractKeyValue"))))
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.empty();
		}

		String operation = context.getParameter("operation", "extractJson");
		String inputField = context.getParameter("inputField", "output");
		String outputField = context.getParameter("outputField", "parsed");

		List<Map<String, Object>> result = new ArrayList<>();

		for (Map<String, Object> item : inputData) {
			try {
				Map<String, Object> json = new LinkedHashMap<>(unwrapJson(item));
				Object rawText = getNestedValue(item, inputField);
				String text = rawText != null ? String.valueOf(rawText) : "";

				Object parsed = switch (operation) {
					case "extractJson" -> extractJson(text);
					case "extractKeyValue" -> extractKeyValue(text, context);
					case "extractSections" -> extractSections(text, context);
					case "extractList" -> extractList(text);
					case "regex" -> extractRegex(text, context);
					default -> text;
				};

				setNestedValue(json, outputField, parsed);
				result.add(wrapInJson(json));
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					Map<String, Object> errorJson = new LinkedHashMap<>(unwrapJson(item));
					errorJson.put("error", e.getMessage());
					result.add(wrapInJson(errorJson));
				} else {
					return handleError(context, "AI output parsing failed: " + e.getMessage(), e);
				}
			}
		}

		return NodeExecutionResult.success(result);
	}

	private Object extractJson(String text) {
		// Try to find JSON object or array in the text
		// Look for ```json ... ``` blocks first
		Pattern codeBlock = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
		Matcher cbMatcher = codeBlock.matcher(text);
		if (cbMatcher.find()) {
			String jsonStr = cbMatcher.group(1).trim();
			try {
				return MAPPER.readValue(jsonStr, new TypeReference<Object>() {});
			} catch (Exception ignored) {}
		}

		// Try to find JSON object { ... }
		int braceStart = text.indexOf('{');
		int braceEnd = text.lastIndexOf('}');
		if (braceStart >= 0 && braceEnd > braceStart) {
			String jsonStr = text.substring(braceStart, braceEnd + 1);
			try {
				return MAPPER.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
			} catch (Exception ignored) {}
		}

		// Try to find JSON array [ ... ]
		int bracketStart = text.indexOf('[');
		int bracketEnd = text.lastIndexOf(']');
		if (bracketStart >= 0 && bracketEnd > bracketStart) {
			String jsonStr = text.substring(bracketStart, bracketEnd + 1);
			try {
				return MAPPER.readValue(jsonStr, new TypeReference<List<Object>>() {});
			} catch (Exception ignored) {}
		}

		return null;
	}

	private Map<String, String> extractKeyValue(String text, NodeExecutionContext context) {
		String separator = context.getParameter("kvSeparator", ":");
		Map<String, String> kvPairs = new LinkedHashMap<>();

		for (String line : text.split("\\r?\\n")) {
			line = line.trim();
			if (line.isEmpty()) continue;

			int sepIdx = line.indexOf(separator);
			if (sepIdx > 0) {
				String key = line.substring(0, sepIdx).trim();
				String value = line.substring(sepIdx + separator.length()).trim();
				// Clean common formatting
				key = key.replaceAll("^[\\-\\*•]+\\s*", ""); // remove list markers
				key = key.replaceAll("\\*+", ""); // remove bold/italic markers
				if (!key.isEmpty()) {
					kvPairs.put(key, value);
				}
			}
		}

		return kvPairs;
	}

	private List<Map<String, String>> extractSections(String text, NodeExecutionContext context) {
		String patternStr = context.getParameter("sectionPattern", "^#{1,6}\\s+(.+)$");
		Pattern headingPattern = Pattern.compile(patternStr, Pattern.MULTILINE);

		List<Map<String, String>> sections = new ArrayList<>();
		Matcher matcher = headingPattern.matcher(text);

		int lastEnd = 0;
		String lastHeading = null;

		while (matcher.find()) {
			if (lastHeading != null) {
				String content = text.substring(lastEnd, matcher.start()).trim();
				Map<String, String> section = new LinkedHashMap<>();
				section.put("heading", lastHeading);
				section.put("content", content);
				sections.add(section);
			}
			lastHeading = matcher.group(1).trim();
			lastEnd = matcher.end();
		}

		// Add the last section
		if (lastHeading != null) {
			String content = text.substring(lastEnd).trim();
			Map<String, String> section = new LinkedHashMap<>();
			section.put("heading", lastHeading);
			section.put("content", content);
			sections.add(section);
		}

		return sections;
	}

	private List<String> extractList(String text) {
		List<String> items = new ArrayList<>();
		Pattern listPattern = Pattern.compile("^\\s*(?:\\d+[.)]|[-*•+])\\s+(.+)$", Pattern.MULTILINE);
		Matcher matcher = listPattern.matcher(text);

		while (matcher.find()) {
			items.add(matcher.group(1).trim());
		}

		return items;
	}

	private Object extractRegex(String text, NodeExecutionContext context) {
		String patternStr = context.getParameter("regexPattern", "");
		boolean allMatches = toBoolean(context.getParameter("allMatches", true), true);

		Pattern pattern = Pattern.compile(patternStr, Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(text);

		List<Object> matches = new ArrayList<>();

		while (matcher.find()) {
			if (matcher.groupCount() > 0) {
				if (matcher.groupCount() == 1) {
					matches.add(matcher.group(1));
				} else {
					Map<String, String> groups = new LinkedHashMap<>();
					for (int i = 1; i <= matcher.groupCount(); i++) {
						groups.put("group" + i, matcher.group(i));
					}
					matches.add(groups);
				}
			} else {
				matches.add(matcher.group());
			}

			if (!allMatches) break;
		}

		if (!allMatches) {
			return matches.isEmpty() ? null : matches.get(0);
		}
		return matches;
	}
}
