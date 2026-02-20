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
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Rename Keys Node - renames field names in data items.
 * Supports direct key-to-key renaming and regex-based renaming.
 */
@Slf4j
@Node(
	type = "renameKeys",
	displayName = "Rename Keys",
	description = "Rename field names (keys) in the input items.",
	category = "Data Transformation",
	icon = "replace"
)
public class RenameKeysNode extends AbstractNode {

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
				.name("keys")
				.displayName("Keys")
				.description("The key renaming rules to apply.")
				.type(ParameterType.FIXED_COLLECTION)
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("currentKey")
						.displayName("Current Key Name")
						.description("The current name of the key to rename.")
						.type(ParameterType.STRING)
						.required(true)
						.placeHolder("e.g. oldName")
						.build(),
					NodeParameter.builder()
						.name("newKey")
						.displayName("New Key Name")
						.description("The new name for the key.")
						.type(ParameterType.STRING)
						.required(true)
						.placeHolder("e.g. newName")
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("additionalOptions")
				.displayName("Additional Options")
				.type(ParameterType.COLLECTION)
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("regexReplacement")
						.displayName("Regex Replacement")
						.description("Use a regular expression to match and rename keys.")
						.type(ParameterType.FIXED_COLLECTION)
						.nestedParameters(List.of(
							NodeParameter.builder()
								.name("searchRegex")
								.displayName("Regular Expression")
								.description("The regex pattern to match key names against.")
								.type(ParameterType.STRING)
								.placeHolder("e.g. ^old_(.*)")
								.build(),
							NodeParameter.builder()
								.name("replaceRegex")
								.displayName("Replace With")
								.description("The replacement string (supports $1, $2, etc. for capture groups).")
								.type(ParameterType.STRING)
								.placeHolder("e.g. new_$1")
								.build(),
							NodeParameter.builder()
								.name("caseInsensitive")
								.displayName("Case Insensitive")
								.type(ParameterType.BOOLEAN)
								.defaultValue(false)
								.build(),
							NodeParameter.builder()
								.name("maxDepth")
								.displayName("Max Depth")
								.description("Maximum depth for nested renaming. -1 for unlimited, 0 for top-level only.")
								.type(ParameterType.NUMBER)
								.defaultValue(-1)
								.build()
						))
						.build()
				))
				.build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.empty();
		}

		// Parse direct rename rules
		Object keysParam = context.getParameter("keys", null);
		List<RenameRule> directRenames = parseRenameRules(keysParam);

		// Parse regex rules from additionalOptions
		List<RegexRule> regexRules = new ArrayList<>();
		Object additionalOpts = context.getParameter("additionalOptions", null);
		if (additionalOpts instanceof Map) {
			Object regexParam = ((Map<String, Object>) additionalOpts).get("regexReplacement");
			regexRules = parseRegexRules(regexParam);
		}

		List<Map<String, Object>> result = new ArrayList<>();
		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = unwrapJson(item);
			Map<String, Object> renamed = new LinkedHashMap<>(json);

			// Apply direct renames
			for (RenameRule rule : directRenames) {
				if (renamed.containsKey(rule.currentKey)) {
					Object value = renamed.remove(rule.currentKey);
					renamed.put(rule.newKey, value);
				}
			}

			// Apply regex renames
			for (RegexRule rule : regexRules) {
				renamed = applyRegexRename(renamed, rule, 0);
			}

			result.add(wrapInJson(renamed));
		}

		log.debug("RenameKeys: processed {} items ({} direct rules, {} regex rules)",
				inputData.size(), directRenames.size(), regexRules.size());
		return NodeExecutionResult.success(result);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> applyRegexRename(Map<String, Object> map, RegexRule rule, int depth) {
		Map<String, Object> result = new LinkedHashMap<>();

		for (Map.Entry<String, Object> entry : map.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();

			// Apply regex to key
			Matcher matcher = rule.pattern.matcher(key);
			String newKey = matcher.replaceAll(rule.replacement);

			// Recurse into nested maps if within depth
			if (value instanceof Map && (rule.maxDepth < 0 || depth < rule.maxDepth)) {
				value = applyRegexRename((Map<String, Object>) value, rule, depth + 1);
			}

			result.put(newKey, value);
		}

		return result;
	}

	@SuppressWarnings("unchecked")
	private List<RenameRule> parseRenameRules(Object keysParam) {
		List<RenameRule> rules = new ArrayList<>();
		if (keysParam == null) return rules;

		List<?> keyList;
		if (keysParam instanceof Map) {
			Object values = ((Map<String, Object>) keysParam).get("values");
			if (values instanceof List) {
				keyList = (List<?>) values;
			} else {
				keyList = List.of(keysParam);
			}
		} else if (keysParam instanceof List) {
			keyList = (List<?>) keysParam;
		} else {
			return rules;
		}

		for (Object entry : keyList) {
			if (entry instanceof Map) {
				Map<String, Object> map = (Map<String, Object>) entry;
				String currentKey = (String) map.get("currentKey");
				String newKey = (String) map.get("newKey");
				if (currentKey != null && !currentKey.isBlank() && newKey != null && !newKey.isBlank()) {
					rules.add(new RenameRule(currentKey.trim(), newKey.trim()));
				}
			}
		}
		return rules;
	}

	@SuppressWarnings("unchecked")
	private List<RegexRule> parseRegexRules(Object regexParam) {
		List<RegexRule> rules = new ArrayList<>();
		if (regexParam == null) return rules;

		List<?> regexList;
		if (regexParam instanceof Map) {
			Object values = ((Map<String, Object>) regexParam).get("values");
			if (values instanceof List) {
				regexList = (List<?>) values;
			} else {
				regexList = List.of(regexParam);
			}
		} else if (regexParam instanceof List) {
			regexList = (List<?>) regexParam;
		} else {
			return rules;
		}

		for (Object entry : regexList) {
			if (entry instanceof Map) {
				Map<String, Object> map = (Map<String, Object>) entry;
				String search = (String) map.get("searchRegex");
				String replace = (String) map.get("replaceRegex");
				boolean caseInsensitive = toBoolean(map.get("caseInsensitive"), false);
				int maxDepth = toInt(map.get("maxDepth"), -1);

				if (search != null && !search.isBlank()) {
					int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
					Pattern pattern = Pattern.compile(search, flags);
					rules.add(new RegexRule(pattern, replace != null ? replace : "", maxDepth));
				}
			}
		}
		return rules;
	}

	private record RenameRule(String currentKey, String newKey) {}
	private record RegexRule(Pattern pattern, String replacement, int maxDepth) {}
}
