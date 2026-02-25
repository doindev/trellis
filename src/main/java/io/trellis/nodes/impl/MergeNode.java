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
 * Merge Node - combines data from multiple input branches into a single output.
 * Supports three modes:
 * - Append: concatenates items from all inputs (supports 2-10 inputs)
 * - Combine by SQL-style key matching: joins items from two inputs by a shared key
 * - Choose Branch: selects all items from one input branch
 */
@Slf4j
@Node(
	type = "merge",
	displayName = "Merge",
	description = "Combine data from multiple input branches. Supports append, key-based merge, and branch selection.",
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
				.description("How to combine the data from the inputs.")
				.type(ParameterType.OPTIONS)
				.defaultValue("append")
				.required(true)
				.options(List.of(
					ParameterOption.builder()
						.name("Append")
						.value("append")
						.description("Append items from all inputs in order")
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
				.name("numberInputs")
				.displayName("Number of Inputs")
				.description("The number of data inputs to merge. The node waits for all connected inputs to be executed.")
				.type(ParameterType.OPTIONS)
				.defaultValue(2)
				.options(List.of(
					ParameterOption.builder().name("2").value("2").build(),
					ParameterOption.builder().name("3").value("3").build(),
					ParameterOption.builder().name("4").value("4").build(),
					ParameterOption.builder().name("5").value("5").build(),
					ParameterOption.builder().name("6").value("6").build(),
					ParameterOption.builder().name("7").value("7").build(),
					ParameterOption.builder().name("8").value("8").build(),
					ParameterOption.builder().name("9").value("9").build(),
					ParameterOption.builder().name("10").value("10").build()
				))
				.displayOptions(Map.of("show", Map.of("mode", List.of("append"))))
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

		if ("append".equals(mode)) {
			int numberInputs = Math.max(2, Math.min(10, toInt(context.getParameter("numberInputs", 2), 2)));
			return executeAppendN(inputData, numberInputs);
		}

		// For combineBySql and chooseBranch, use 2-input logic
		List<Map<String, Object>> input1 = new ArrayList<>();
		List<Map<String, Object>> input2 = new ArrayList<>();
		splitTwoInputs(inputData, input1, input2);

		List<Map<String, Object>> result;

		switch (mode) {
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

	/**
	 * Split input data into two buckets by _inputIndex (0 and 1).
	 */
	private void splitTwoInputs(List<Map<String, Object>> inputData,
			List<Map<String, Object>> input1, List<Map<String, Object>> input2) {
		for (Map<String, Object> item : inputData) {
			int idx = getInputIndex(item);
			if (idx == 1) {
				input2.add(item);
			} else {
				input1.add(item);
			}
		}
	}

	/**
	 * Append mode with N inputs: split items into N buckets by _inputIndex,
	 * then concatenate all buckets in order.
	 */
	private NodeExecutionResult executeAppendN(List<Map<String, Object>> inputData, int numberInputs) {
		List<List<Map<String, Object>>> buckets = new ArrayList<>();
		for (int i = 0; i < numberInputs; i++) {
			buckets.add(new ArrayList<>());
		}

		for (Map<String, Object> item : inputData) {
			int idx = getInputIndex(item);
			if (idx >= 0 && idx < numberInputs) {
				buckets.get(idx).add(item);
			} else {
				buckets.get(0).add(item);
			}
		}

		List<Map<String, Object>> result = new ArrayList<>();
		for (List<Map<String, Object>> bucket : buckets) {
			result.addAll(bucket);
		}

		log.debug("Merge node: append {} inputs, {} total items -> {} output items",
			numberInputs, inputData.size(), result.size());
		return NodeExecutionResult.success(result);
	}

	/**
	 * Read the _inputIndex from an item (checking both json wrapper and root).
	 */
	private int getInputIndex(Map<String, Object> item) {
		Map<String, Object> json = unwrapJson(item);
		Object inputIndex = json.get("_inputIndex");
		if (inputIndex == null) {
			inputIndex = item.get("_inputIndex");
		}
		return toInt(inputIndex, 0);
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
