package io.trellis.nodes.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * Merge Node - combines data from two input branches into a single output.
 * Supports three modes:
 * - Append: concatenates items from both inputs
 * - Combine by SQL-style key matching: joins items from both inputs by a shared key
 * - Choose Branch: selects all items from one input branch
 */
@Slf4j
@Node(
	type = "merge",
	displayName = "Merge",
	description = "Combine data from two input branches. Supports append, key-based merge, and branch selection.",
	category = "Flow",
	icon = "merge"
)
public class MergeNode extends AbstractNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of(
			NodeInput.builder().name("input1").displayName("Input 1").build(),
			NodeInput.builder().name("input2").displayName("Input 2").build()
		);
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
				.description("How to combine the data from the two inputs.")
				.type(ParameterType.OPTIONS)
				.defaultValue("append")
				.required(true)
				.options(List.of(
					ParameterOption.builder()
						.name("Append")
						.value("append")
						.description("Append all items from Input 2 after Input 1")
						.build(),
					ParameterOption.builder()
						.name("Combine by Matching Key")
						.value("combineBySql")
						.description("Merge items from both inputs by a matching key field")
						.build(),
					ParameterOption.builder()
						.name("Choose Branch")
						.value("chooseBranch")
						.description("Select all items from one input and discard the other")
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("chooseBranchValue")
				.displayName("Output Branch")
				.description("Which input branch's data to output.")
				.type(ParameterType.OPTIONS)
				.defaultValue("input1")
				.options(List.of(
					ParameterOption.builder().name("Input 1").value("input1").build(),
					ParameterOption.builder().name("Input 2").value("input2").build()
				))
				.displayOptions(Map.of("show", Map.of("mode", List.of("chooseBranch"))))
				.build(),

			NodeParameter.builder()
				.name("mergeKey")
				.displayName("Merge Key")
				.description("The field name to use for matching items between the two inputs (supports dot notation, e.g., 'json.id').")
				.type(ParameterType.STRING)
				.placeHolder("id")
				.displayOptions(Map.of("show", Map.of("mode", List.of("combineBySql"))))
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String mode = context.getParameter("mode", "append");
		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData == null) {
			inputData = List.of();
		}

		// Split input data into two branches
		// Convention: inputData contains items tagged with _inputIndex or we split by position
		// For now, we split based on _inputIndex metadata or treat first half as input1, second as input2
		List<Map<String, Object>> input1 = new ArrayList<>();
		List<Map<String, Object>> input2 = new ArrayList<>();

		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = unwrapJson(item);
			Object inputIndex = json.get("_inputIndex");
			if (inputIndex == null) {
				inputIndex = item.get("_inputIndex");
			}

			if (inputIndex != null && toInt(inputIndex, 0) == 1) {
				input2.add(item);
			} else {
				input1.add(item);
			}
		}

		// If no _inputIndex tags, split evenly as a fallback
		if (input2.isEmpty() && input1.size() > 1 && inputData.stream().noneMatch(i -> {
			Map<String, Object> j = unwrapJson(i);
			return j.containsKey("_inputIndex") || i.containsKey("_inputIndex");
		})) {
			// Keep all in input1 - single branch scenario
			log.debug("Merge node: all {} items treated as input 1 (no _inputIndex tags)", inputData.size());
		}

		List<Map<String, Object>> result;

		switch (mode) {
			case "append":
				result = mergeAppend(input1, input2);
				break;
			case "combineBySql":
				String mergeKey = context.getParameter("mergeKey", "id");
				result = mergeBySqlKey(input1, input2, mergeKey);
				break;
			case "chooseBranch":
				String branch = context.getParameter("chooseBranchValue", "input1");
				result = "input2".equals(branch) ? new ArrayList<>(input2) : new ArrayList<>(input1);
				break;
			default:
				result = mergeAppend(input1, input2);
				break;
		}

		log.debug("Merge node: input1={}, input2={}, output={} items (mode={})",
			input1.size(), input2.size(), result.size(), mode);

		return NodeExecutionResult.success(result);
	}

	private List<Map<String, Object>> mergeAppend(List<Map<String, Object>> input1,
			List<Map<String, Object>> input2) {
		List<Map<String, Object>> result = new ArrayList<>(input1);
		result.addAll(input2);
		return result;
	}

	private List<Map<String, Object>> mergeBySqlKey(List<Map<String, Object>> input1,
			List<Map<String, Object>> input2, String mergeKey) {
		List<Map<String, Object>> result = new ArrayList<>();

		// Build a lookup map from input2 by merge key
		Map<String, Map<String, Object>> input2Lookup = new HashMap<>();
		for (Map<String, Object> item : input2) {
			Object keyValue = getNestedValue(item, mergeKey);
			if (keyValue != null) {
				input2Lookup.put(String.valueOf(keyValue), item);
			}
		}

		// For each item in input1, find a matching item in input2 and merge
		for (Map<String, Object> item1 : input1) {
			Object keyValue = getNestedValue(item1, mergeKey);
			Map<String, Object> merged = new HashMap<>(unwrapJson(deepClone(item1)));

			if (keyValue != null) {
				Map<String, Object> item2 = input2Lookup.get(String.valueOf(keyValue));
				if (item2 != null) {
					Map<String, Object> json2 = unwrapJson(item2);
					// Merge fields from item2 into item1 (item2 values override on conflict)
					merged.putAll(json2);
				}
			}

			result.add(wrapInJson(merged));
		}

		// Add any items from input2 that weren't matched
		for (Map.Entry<String, Map<String, Object>> entry : input2Lookup.entrySet()) {
			boolean matched = false;
			for (Map<String, Object> item1 : input1) {
				Object keyValue = getNestedValue(item1, mergeKey);
				if (keyValue != null && entry.getKey().equals(String.valueOf(keyValue))) {
					matched = true;
					break;
				}
			}
			if (!matched) {
				result.add(deepClone(entry.getValue()));
			}
		}

		return result;
	}
}
