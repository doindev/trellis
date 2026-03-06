package io.cwc.nodes.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Switch Node - routes items to different outputs based on rules or expressions.
 * Each routing rule maps 1:1 to an output (rule index = output index).
 * Users can dynamically add/remove rules to create more or fewer outputs.
 * In expression mode, the expression result determines the output index.
 */
@Slf4j
@Node(
	type = "switch",
	displayName = "Switch",
	description = "Route items to different outputs based on rules or expressions. Add routing rules to create output branches.",
	category = "Flow",
	icon = "signpost"
)
public class SwitchNode extends AbstractNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		// Default single output — actual outputs are dynamic based on parameters.
		// The frontend computes per-instance outputs from the node's rules/numberOutputs parameter.
		return List.of(
			NodeOutput.builder().name("output0").displayName("Output 0").build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("mode")
				.displayName("Mode")
				.description("How to determine which output to route items to.")
				.type(ParameterType.OPTIONS)
				.defaultValue("rules")
				.required(true)
				.options(List.of(
					ParameterOption.builder()
						.name("Rules")
						.value("rules")
						.description("Build a matching rule for each output")
						.build(),
					ParameterOption.builder()
						.name("Expression")
						.value("expression")
						.description("Write an expression that returns the output index")
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("rules")
				.displayName("Routing Rules")
				.description("Each rule creates an output. Items matching a rule are routed to that output.")
				.type(ParameterType.FIXED_COLLECTION)
				.displayOptions(Map.of("show", Map.of("mode", List.of("rules"))))
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("value1")
						.displayName("Value 1")
						.description("The first value to compare. Use dot notation for field references (e.g. json.status).")
						.type(ParameterType.STRING)
						.required(true)
						.build(),
					NodeParameter.builder()
						.name("operation")
						.displayName("Operation")
						.description("The comparison operation.")
						.type(ParameterType.OPTIONS)
						.defaultValue("equals")
						.options(List.of(
							ParameterOption.builder().name("Equals").value("equals").build(),
							ParameterOption.builder().name("Not Equals").value("notEquals").build(),
							ParameterOption.builder().name("Contains").value("contains").build(),
							ParameterOption.builder().name("Starts With").value("startsWith").build(),
							ParameterOption.builder().name("Ends With").value("endsWith").build(),
							ParameterOption.builder().name("Greater Than").value("greaterThan").build(),
							ParameterOption.builder().name("Less Than").value("lessThan").build(),
							ParameterOption.builder().name("Regex").value("regex").build(),
							ParameterOption.builder().name("Is Empty").value("isEmpty").build(),
							ParameterOption.builder().name("Is Not Empty").value("isNotEmpty").build()
						))
						.build(),
					NodeParameter.builder()
						.name("value2")
						.displayName("Value 2")
						.description("The second value to compare against.")
						.type(ParameterType.STRING)
						.build(),
					NodeParameter.builder()
						.name("outputLabel")
						.displayName("Output Label")
						.description("Optional custom label for this output on the canvas.")
						.type(ParameterType.STRING)
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("fallbackOutput")
				.displayName("Fallback Output")
				.description("What to do with items that don't match any rule.")
				.type(ParameterType.OPTIONS)
				.defaultValue("none")
				.displayOptions(Map.of("show", Map.of("mode", List.of("rules"))))
				.options(List.of(
					ParameterOption.builder()
						.name("None (drop)")
						.value("none")
						.description("Unmatched items are silently dropped")
						.build(),
					ParameterOption.builder()
						.name("Extra Output")
						.value("extra")
						.description("Unmatched items are sent to an extra 'Fallback' output")
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("allMatchingOutputs")
				.displayName("Send to All Matching Outputs")
				.description("When enabled, items are sent to every matching rule's output instead of only the first match.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.displayOptions(Map.of("show", Map.of("mode", List.of("rules"))))
				.build(),

			NodeParameter.builder()
				.name("numberOutputs")
				.displayName("Number of Outputs")
				.description("How many output branches to create.")
				.type(ParameterType.NUMBER)
				.defaultValue(4)
				.minValue(2)
				.maxValue(10)
				.displayOptions(Map.of("show", Map.of("mode", List.of("expression"))))
				.build(),

			NodeParameter.builder()
				.name("expression")
				.displayName("Expression")
				.description("A field reference that returns the output index (0-based). Evaluated on each item.")
				.type(ParameterType.STRING)
				.placeHolder("json.outputIndex")
				.displayOptions(Map.of("show", Map.of("mode", List.of("expression"))))
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String mode = context.getParameter("mode", "rules");
		List<Map<String, Object>> inputData = context.getInputData();

		if ("rules".equals(mode)) {
			return executeRulesMode(context, inputData);
		} else {
			return executeExpressionMode(context, inputData);
		}
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeRulesMode(NodeExecutionContext context,
			List<Map<String, Object>> inputData) {
		Object rulesObj = context.getParameter("rules", null);
		List<Map<String, Object>> rulesList = new ArrayList<>();
		if (rulesObj instanceof List) {
			for (Object r : (List<?>) rulesObj) {
				if (r instanceof Map) {
					rulesList.add((Map<String, Object>) r);
				}
			}
		}

		String fallbackOutput = context.getParameter("fallbackOutput", "none");
		boolean hasFallbackExtra = "extra".equals(fallbackOutput);
		boolean allMatching = Boolean.TRUE.equals(context.getParameter("allMatchingOutputs", false));

		int numOutputs = Math.max(1, rulesList.size());
		if (hasFallbackExtra) numOutputs++;

		// Initialize output buckets
		List<List<Map<String, Object>>> outputs = new ArrayList<>();
		for (int i = 0; i < numOutputs; i++) {
			outputs.add(new ArrayList<>());
		}

		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.successMultiOutput(outputs);
		}

		for (Map<String, Object> item : inputData) {
			boolean matched = false;
			for (int ruleIdx = 0; ruleIdx < rulesList.size(); ruleIdx++) {
				if (evaluateRule(item, rulesList.get(ruleIdx))) {
					outputs.get(ruleIdx).add(item);
					matched = true;
					if (!allMatching) break;
				}
			}
			if (!matched) {
				if (hasFallbackExtra) {
					outputs.get(outputs.size() - 1).add(item);
				}
				// else "none": item is dropped
			}
		}

		log.debug("Switch node (rules): {} items -> outputs[{}]",
			inputData.size(),
			outputs.stream().map(l -> String.valueOf(l.size())).reduce((a, b) -> a + ", " + b).orElse(""));

		return NodeExecutionResult.successMultiOutput(outputs);
	}

	private NodeExecutionResult executeExpressionMode(NodeExecutionContext context,
			List<Map<String, Object>> inputData) {
		int numOutputs = toInt(context.getParameter("numberOutputs", 4), 4);
		numOutputs = Math.max(2, Math.min(10, numOutputs));
		String expression = context.getParameter("expression", "");

		// Initialize output buckets
		List<List<Map<String, Object>>> outputs = new ArrayList<>();
		for (int i = 0; i < numOutputs; i++) {
			outputs.add(new ArrayList<>());
		}

		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.successMultiOutput(outputs);
		}

		for (Map<String, Object> item : inputData) {
			int targetOutput = 0;
			if (expression != null && !expression.isEmpty()) {
				Object value = getNestedValue(item, expression);
				if (value != null) {
					targetOutput = toInt(value, 0);
				}
			}
			targetOutput = Math.max(0, Math.min(numOutputs - 1, targetOutput));
			outputs.get(targetOutput).add(item);
		}

		log.debug("Switch node (expression): {} items -> outputs[{}]",
			inputData.size(),
			outputs.stream().map(l -> String.valueOf(l.size())).reduce((a, b) -> a + ", " + b).orElse(""));

		return NodeExecutionResult.successMultiOutput(outputs);
	}

	private boolean evaluateRule(Map<String, Object> item, Map<String, Object> rule) {
		String value1Ref = toString(rule.get("value1"));
		String operation = toString(rule.get("operation"));
		String value2Ref = toString(rule.get("value2"));

		Object resolvedValue1 = getNestedValue(item, value1Ref);
		String val1 = resolvedValue1 != null ? String.valueOf(resolvedValue1) : value1Ref;

		Object resolvedValue2 = getNestedValue(item, value2Ref);
		String val2 = resolvedValue2 != null ? String.valueOf(resolvedValue2) : value2Ref;

		return evaluateOperation(val1, operation, val2, resolvedValue1);
	}

	private boolean evaluateOperation(String val1, String operation, String val2, Object rawVal1) {
		if (operation == null || operation.isEmpty()) {
			operation = "equals";
		}

		switch (operation) {
			case "equals":
				return val1.equals(val2);
			case "notEquals":
				return !val1.equals(val2);
			case "contains":
				return val1.contains(val2);
			case "startsWith":
				return val1.startsWith(val2);
			case "endsWith":
				return val1.endsWith(val2);
			case "greaterThan":
				return compareNumbers(val1, val2) > 0;
			case "lessThan":
				return compareNumbers(val1, val2) < 0;
			case "regex":
				try {
					return Pattern.compile(val2).matcher(val1).find();
				} catch (Exception e) {
					log.warn("Invalid regex pattern: {}", val2);
					return false;
				}
			case "isEmpty":
				return rawVal1 == null || val1.isEmpty();
			case "isNotEmpty":
				return rawVal1 != null && !val1.isEmpty();
			default:
				log.warn("Unknown operation: {}", operation);
				return false;
		}
	}

	private int compareNumbers(String val1, String val2) {
		try {
			double d1 = Double.parseDouble(val1);
			double d2 = Double.parseDouble(val2);
			return Double.compare(d1, d2);
		} catch (NumberFormatException e) {
			return val1.compareTo(val2);
		}
	}
}
