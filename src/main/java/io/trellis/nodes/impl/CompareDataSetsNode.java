package io.trellis.nodes.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * Compare Datasets Node - compares two input datasets to find matches, differences,
 * and unique items. Produces four outputs:
 * <ol>
 *   <li>In A only - items from Input A with no match in Input B</li>
 *   <li>In A and B (same) - matched items where all compared fields are identical</li>
 *   <li>In A and B (different) - matched items where compared fields differ</li>
 *   <li>In B only - items from Input B with no match in Input A</li>
 * </ol>
 */
@Slf4j
@Node(
	type = "compareDatasets",
	displayName = "Compare Datasets",
	description = "Compare two input datasets to find matching, different, and unique items.",
	category = "Data Transformation",
	icon = "git-compare"
)
public class CompareDataSetsNode extends AbstractNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of(
			NodeInput.builder().name("inputA").displayName("Input A").build(),
			NodeInput.builder().name("inputB").displayName("Input B").build()
		);
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
			NodeOutput.builder().name("inAOnly").displayName("In A Only").build(),
			NodeOutput.builder().name("same").displayName("Same").build(),
			NodeOutput.builder().name("different").displayName("Different").build(),
			NodeOutput.builder().name("inBOnly").displayName("In B Only").build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("mergeByFields")
				.displayName("Fields to Match")
				.description("Define which fields to use for matching items between the two inputs.")
				.type(ParameterType.FIXED_COLLECTION)
				.required(true)
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("field1")
						.displayName("Input A Field")
						.description("Field name from Input A to match on.")
						.type(ParameterType.STRING)
						.required(true)
						.placeHolder("e.g. id")
						.build(),
					NodeParameter.builder()
						.name("field2")
						.displayName("Input B Field")
						.description("Field name from Input B to match on.")
						.type(ParameterType.STRING)
						.required(true)
						.placeHolder("e.g. id")
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("resolve")
				.displayName("When There Are Differences")
				.description("How to handle items that match but have different values in other fields.")
				.type(ParameterType.OPTIONS)
				.defaultValue("preferInput1")
				.options(List.of(
					ParameterOption.builder().name("Use Input A Version").value("preferInput1")
						.description("Output the Input A version of differing items").build(),
					ParameterOption.builder().name("Use Input B Version").value("preferInput2")
						.description("Output the Input B version of differing items").build(),
					ParameterOption.builder().name("Use a Mix of Versions").value("mix")
						.description("Select which fields to prefer from each input").build(),
					ParameterOption.builder().name("Include Both Versions").value("includeBoth")
						.description("Include fields from both inputs, suffixed with _A and _B for conflicts").build()
				))
				.build(),

			NodeParameter.builder()
				.name("preferWhenMix")
				.displayName("Prefer")
				.description("Which input to prefer for most fields.")
				.type(ParameterType.OPTIONS)
				.defaultValue("input1")
				.options(List.of(
					ParameterOption.builder().name("Input A").value("input1").build(),
					ParameterOption.builder().name("Input B").value("input2").build()
				))
				.displayOptions(Map.of("show", Map.of("resolve", List.of("mix"))))
				.build(),

			NodeParameter.builder()
				.name("exceptWhenMix")
				.displayName("For These Fields, Prefer the Other Input")
				.description("Comma-separated list of fields to take from the non-preferred input.")
				.type(ParameterType.STRING)
				.defaultValue("")
				.displayOptions(Map.of("show", Map.of("resolve", List.of("mix"))))
				.build(),

			NodeParameter.builder()
				.name("options")
				.displayName("Options")
				.type(ParameterType.COLLECTION)
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("fuzzyCompare")
						.displayName("Fuzzy Compare")
						.description("Tolerate type differences when comparing (e.g., number 3 equals string \"3\").")
						.type(ParameterType.BOOLEAN)
						.defaultValue(false)
						.build(),
					NodeParameter.builder()
						.name("fieldsToSkipComparing")
						.displayName("Fields to Skip Comparing")
						.description("Comma-separated list of fields to exclude from the same/different comparison.")
						.type(ParameterType.STRING)
						.defaultValue("")
						.build(),
					NodeParameter.builder()
						.name("disableDotNotation")
						.displayName("Disable Dot Notation")
						.description("When true, treats field names as literal strings (no nested access).")
						.type(ParameterType.BOOLEAN)
						.defaultValue(false)
						.build(),
					NodeParameter.builder()
						.name("multipleMatches")
						.displayName("Multiple Matches")
						.description("How to handle when multiple items match.")
						.type(ParameterType.OPTIONS)
						.defaultValue("all")
						.options(List.of(
							ParameterOption.builder().name("Include All Matches").value("all")
								.description("Include every matching item combination").build(),
							ParameterOption.builder().name("Include First Match Only").value("first")
								.description("Only include the first match found").build()
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
			return NodeExecutionResult.successMultiOutput(
				List.of(List.of(), List.of(), List.of(), List.of()));
		}

		// Split input data into two branches by _inputIndex
		List<Map<String, Object>> inputA = new ArrayList<>();
		List<Map<String, Object>> inputB = new ArrayList<>();
		splitInputs(inputData, inputA, inputB);

		// Parse match field pairs
		List<FieldPair> matchFields = parseMatchFields(context.getParameter("mergeByFields", null));
		if (matchFields.isEmpty()) {
			return NodeExecutionResult.error("At least one field pair must be specified for matching");
		}

		String resolve = context.getParameter("resolve", "preferInput1");

		// Parse options
		boolean fuzzyCompare = false;
		Set<String> skipFields = new LinkedHashSet<>();
		boolean disableDotNotation = false;
		String multipleMatches = "all";
		Object optionsObj = context.getParameter("options", null);
		if (optionsObj instanceof Map) {
			Map<String, Object> opts = (Map<String, Object>) optionsObj;
			fuzzyCompare = toBoolean(opts.get("fuzzyCompare"), false);
			disableDotNotation = toBoolean(opts.get("disableDotNotation"), false);
			Object mm = opts.get("multipleMatches");
			if (mm != null) multipleMatches = String.valueOf(mm);
			Object skipObj = opts.get("fieldsToSkipComparing");
			if (skipObj != null && !String.valueOf(skipObj).isBlank()) {
				for (String f : String.valueOf(skipObj).split(",")) {
					skipFields.add(f.trim());
				}
			}
		}

		// Mix mode parameters
		String preferWhenMix = context.getParameter("preferWhenMix", "input1");
		Set<String> exceptFields = new LinkedHashSet<>();
		String exceptStr = context.getParameter("exceptWhenMix", "");
		if (exceptStr != null && !exceptStr.isBlank()) {
			for (String f : exceptStr.split(",")) {
				exceptFields.add(f.trim());
			}
		}

		// Build match keys for all items
		// For each item in A, find matching items in B
		List<Map<String, Object>> inAOnly = new ArrayList<>();
		List<Map<String, Object>> same = new ArrayList<>();
		List<Map<String, Object>> different = new ArrayList<>();
		List<Map<String, Object>> inBOnly = new ArrayList<>();

		// Track which B items were matched
		boolean[] bMatched = new boolean[inputB.size()];

		for (Map<String, Object> itemA : inputA) {
			Map<String, Object> jsonA = unwrapJson(itemA);
			// Remove _inputIndex tag for comparison
			jsonA = cleanItem(jsonA);

			boolean aMatched = false;

			for (int bi = 0; bi < inputB.size(); bi++) {
				Map<String, Object> itemB = inputB.get(bi);
				Map<String, Object> jsonB = unwrapJson(itemB);
				jsonB = cleanItem(jsonB);

				// Check if match fields are equal
				if (!fieldsMatch(jsonA, jsonB, matchFields, fuzzyCompare, disableDotNotation)) {
					continue;
				}

				// We have a match
				aMatched = true;
				bMatched[bi] = true;

				// Determine if items are the same or different (comparing non-match, non-skip fields)
				Set<String> matchFieldNamesA = new LinkedHashSet<>();
				Set<String> matchFieldNamesB = new LinkedHashSet<>();
				for (FieldPair fp : matchFields) {
					matchFieldNamesA.add(fp.field1);
					matchFieldNamesB.add(fp.field2);
				}

				boolean hasDifferences = itemsHaveDifferences(
					jsonA, jsonB, matchFieldNamesA, matchFieldNamesB, skipFields,
					fuzzyCompare, disableDotNotation);

				if (hasDifferences) {
					Map<String, Object> resolved = resolveItem(
						jsonA, jsonB, resolve, preferWhenMix, exceptFields,
						matchFieldNamesA, matchFieldNamesB);
					different.add(wrapInJson(resolved));
				} else {
					// Items are the same - output the A version
					same.add(wrapInJson(new LinkedHashMap<>(jsonA)));
				}

				if ("first".equals(multipleMatches)) {
					break;
				}
			}

			if (!aMatched) {
				inAOnly.add(wrapInJson(new LinkedHashMap<>(jsonA)));
			}
		}

		// Collect unmatched B items
		for (int bi = 0; bi < inputB.size(); bi++) {
			if (!bMatched[bi]) {
				Map<String, Object> jsonB = unwrapJson(inputB.get(bi));
				jsonB = cleanItem(jsonB);
				inBOnly.add(wrapInJson(new LinkedHashMap<>(jsonB)));
			}
		}

		log.debug("CompareDatasets: inputA={}, inputB={}, inAOnly={}, same={}, different={}, inBOnly={}",
			inputA.size(), inputB.size(), inAOnly.size(), same.size(), different.size(), inBOnly.size());

		return NodeExecutionResult.successMultiOutput(
			List.of(inAOnly, same, different, inBOnly));
	}

	/**
	 * Split combined input data into Input A and Input B using _inputIndex tags.
	 */
	private void splitInputs(List<Map<String, Object>> inputData,
			List<Map<String, Object>> inputA, List<Map<String, Object>> inputB) {
		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = unwrapJson(item);
			Object inputIndex = json.get("_inputIndex");
			if (inputIndex == null) {
				inputIndex = item.get("_inputIndex");
			}

			if (inputIndex != null && toInt(inputIndex, 0) == 1) {
				inputB.add(item);
			} else {
				inputA.add(item);
			}
		}
	}

	/**
	 * Remove _inputIndex tag from item data.
	 */
	private Map<String, Object> cleanItem(Map<String, Object> json) {
		Map<String, Object> clean = new LinkedHashMap<>(json);
		clean.remove("_inputIndex");
		return clean;
	}

	/**
	 * Check if two items match on all specified field pairs.
	 */
	private boolean fieldsMatch(Map<String, Object> jsonA, Map<String, Object> jsonB,
			List<FieldPair> matchFields, boolean fuzzyCompare, boolean disableDotNotation) {
		for (FieldPair fp : matchFields) {
			Object valA = disableDotNotation ? jsonA.get(fp.field1) : getNestedValue(jsonA, fp.field1);
			Object valB = disableDotNotation ? jsonB.get(fp.field2) : getNestedValue(jsonB, fp.field2);

			if (!valuesEqual(valA, valB, fuzzyCompare)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Check if two items have differences in non-match, non-skip fields.
	 */
	private boolean itemsHaveDifferences(Map<String, Object> jsonA, Map<String, Object> jsonB,
			Set<String> matchFieldsA, Set<String> matchFieldsB, Set<String> skipFields,
			boolean fuzzyCompare, boolean disableDotNotation) {
		// Collect all field names from both items (excluding match fields and skip fields)
		Set<String> allFields = new LinkedHashSet<>();
		for (String key : jsonA.keySet()) {
			if (!matchFieldsA.contains(key) && !skipFields.contains(key)) {
				allFields.add(key);
			}
		}
		for (String key : jsonB.keySet()) {
			if (!matchFieldsB.contains(key) && !skipFields.contains(key)) {
				allFields.add(key);
			}
		}

		for (String field : allFields) {
			Object valA = disableDotNotation ? jsonA.get(field) : getNestedValue(jsonA, field);
			Object valB = disableDotNotation ? jsonB.get(field) : getNestedValue(jsonB, field);

			if (!valuesEqual(valA, valB, fuzzyCompare)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Compare two values, optionally with fuzzy comparison.
	 */
	private boolean valuesEqual(Object a, Object b, boolean fuzzy) {
		if (a == null && b == null) return true;
		if (a == null || b == null) return false;

		if (fuzzy) {
			// Normalize to strings for comparison
			String strA = String.valueOf(a).trim();
			String strB = String.valueOf(b).trim();
			return strA.equals(strB);
		}

		return a.equals(b);
	}

	/**
	 * Resolve a pair of differing items based on the resolve strategy.
	 */
	private Map<String, Object> resolveItem(Map<String, Object> jsonA, Map<String, Object> jsonB,
			String resolve, String preferWhenMix, Set<String> exceptFields,
			Set<String> matchFieldsA, Set<String> matchFieldsB) {
		switch (resolve) {
			case "preferInput1":
				return new LinkedHashMap<>(jsonA);

			case "preferInput2":
				return new LinkedHashMap<>(jsonB);

			case "mix": {
				Map<String, Object> result = new LinkedHashMap<>();
				Set<String> allKeys = new LinkedHashSet<>();
				allKeys.addAll(jsonA.keySet());
				allKeys.addAll(jsonB.keySet());

				boolean preferA = "input1".equals(preferWhenMix);
				for (String key : allKeys) {
					boolean useException = exceptFields.contains(key);
					boolean useA = preferA ? !useException : useException;
					if (useA && jsonA.containsKey(key)) {
						result.put(key, jsonA.get(key));
					} else if (jsonB.containsKey(key)) {
						result.put(key, jsonB.get(key));
					} else if (jsonA.containsKey(key)) {
						result.put(key, jsonA.get(key));
					}
				}
				return result;
			}

			case "includeBoth": {
				Map<String, Object> result = new LinkedHashMap<>();
				Set<String> allKeys = new LinkedHashSet<>();
				allKeys.addAll(jsonA.keySet());
				allKeys.addAll(jsonB.keySet());

				for (String key : allKeys) {
					Object valA = jsonA.get(key);
					Object valB = jsonB.get(key);

					// Match fields go in unsuffixed
					if (matchFieldsA.contains(key) || matchFieldsB.contains(key)) {
						result.put(key, valA != null ? valA : valB);
						continue;
					}

					boolean bothHave = jsonA.containsKey(key) && jsonB.containsKey(key);
					if (bothHave && !valuesEqual(valA, valB, false)) {
						// Both have the field with different values - suffix
						result.put(key + "_A", valA);
						result.put(key + "_B", valB);
					} else {
						// Only one has it, or values are the same
						result.put(key, valA != null ? valA : valB);
					}
				}
				return result;
			}

			default:
				return new LinkedHashMap<>(jsonA);
		}
	}

	/**
	 * Parse match field pairs from the FIXED_COLLECTION parameter.
	 */
	@SuppressWarnings("unchecked")
	private List<FieldPair> parseMatchFields(Object param) {
		List<FieldPair> pairs = new ArrayList<>();
		if (param == null) return pairs;

		List<?> list;
		if (param instanceof Map) {
			Object values = ((Map<String, Object>) param).get("values");
			list = values instanceof List ? (List<?>) values : List.of(param);
		} else if (param instanceof List) {
			list = (List<?>) param;
		} else {
			return pairs;
		}

		for (Object entry : list) {
			if (entry instanceof Map) {
				Map<String, Object> map = (Map<String, Object>) entry;
				String field1 = map.get("field1") != null ? String.valueOf(map.get("field1")).trim() : null;
				String field2 = map.get("field2") != null ? String.valueOf(map.get("field2")).trim() : null;
				if (field1 != null && !field1.isEmpty() && field2 != null && !field2.isEmpty()) {
					pairs.add(new FieldPair(field1, field2));
				}
			}
		}
		return pairs;
	}

	private record FieldPair(String field1, String field2) {}
}
