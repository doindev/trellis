package io.trellis.nodes.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * Switch Node - routes items to one of four outputs based on rules or expressions.
 * In rules mode, each rule maps a condition to an output index (0-3).
 * In expression mode, the expression result determines the output index.
 * Items that don't match any rule go to the fallback output.
 */
@Slf4j
@Node(
	type = "switch",
	displayName = "Switch",
	description = "Route items to different outputs based on rules or expressions. Supports up to 4 output branches.",
	category = "Flow",
	icon = "route"
)
public class SwitchNode extends AbstractNode {

	private static final int NUM_OUTPUTS = 4;

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
			NodeOutput.builder().name("output0").displayName("Output 0").build(),
			NodeOutput.builder().name("output1").displayName("Output 1").build(),
			NodeOutput.builder().name("output2").displayName("Output 2").build(),
			NodeOutput.builder().name("output3").displayName("Output 3").build()
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
						.description("Route based on matching rules")
						.build(),
					ParameterOption.builder()
						.name("Expression")
						.value("expression")
						.description("Route based on an expression that returns the output index (0-3)")
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("rules")
				.displayName("Routing Rules")
				.description("Define rules to route items to different outputs.")
				.type(ParameterType.FIXED_COLLECTION)
				.displayOptions(Map.of("show", Map.of("mode", List.of("rules"))))
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("output")
						.displayName("Output")
						.description("The output index to route to (0-3).")
						.type(ParameterType.NUMBER)
						.defaultValue(0)
						.minValue(0)
						.maxValue(3)
						.required(true)
						.build(),
					NodeParameter.builder()
						.name("value1")
						.displayName("Value 1")
						.description("The first value to compare. Supports dot notation for field references.")
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
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("expression")
				.displayName("Expression")
				.description("An expression that returns the output index (0-3). Evaluated as a field reference on the item.")
				.type(ParameterType.STRING)
				.placeHolder("json.outputIndex")
				.displayOptions(Map.of("show", Map.of("mode", List.of("expression"))))
				.build(),

			NodeParameter.builder()
				.name("fallbackOutput")
				.displayName("Fallback Output")
				.description("The output to use when no rules match (0-3).")
				.type(ParameterType.NUMBER)
				.defaultValue(3)
				.minValue(0)
				.maxValue(3)
				.build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String mode = context.getParameter("mode", "rules");
		int fallbackOutput = toInt(context.getParameter("fallbackOutput", 3), 3);
		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData == null || inputData.isEmpty()) {
			List<List<Map<String, Object>>> emptyOutputs = new ArrayList<>();
			for (int i = 0; i < NUM_OUTPUTS; i++) {
				emptyOutputs.add(new ArrayList<>());
			}
			return NodeExecutionResult.successMultiOutput(emptyOutputs);
		}

		// Initialize output buckets
		List<List<Map<String, Object>>> outputs = new ArrayList<>();
		for (int i = 0; i < NUM_OUTPUTS; i++) {
			outputs.add(new ArrayList<>());
		}

		if ("rules".equals(mode)) {
			Object rulesObj = context.getParameter("rules", null);
			List<Map<String, Object>> rulesList = new ArrayList<>();
			if (rulesObj instanceof List) {
				for (Object r : (List<?>) rulesObj) {
					if (r instanceof Map) {
						rulesList.add((Map<String, Object>) r);
					}
				}
			}

			for (Map<String, Object> item : inputData) {
				int targetOutput = evaluateRules(item, rulesList, fallbackOutput);
				targetOutput = Math.max(0, Math.min(NUM_OUTPUTS - 1, targetOutput));
				outputs.get(targetOutput).add(item);
			}
		} else {
			// Expression mode
			String expression = context.getParameter("expression", "");

			for (Map<String, Object> item : inputData) {
				int targetOutput = fallbackOutput;
				if (expression != null && !expression.isEmpty()) {
					Object value = getNestedValue(item, expression);
					if (value != null) {
						targetOutput = toInt(value, fallbackOutput);
					}
				}
				targetOutput = Math.max(0, Math.min(NUM_OUTPUTS - 1, targetOutput));
				outputs.get(targetOutput).add(item);
			}
		}

		log.debug("Switch node: {} items -> outputs[{}]",
			inputData.size(),
			outputs.stream().map(l -> String.valueOf(l.size())).reduce((a, b) -> a + ", " + b).orElse(""));

		return NodeExecutionResult.successMultiOutput(outputs);
	}

	private int evaluateRules(Map<String, Object> item, List<Map<String, Object>> rules, int fallbackOutput) {
		for (Map<String, Object> rule : rules) {
			int output = toInt(rule.get("output"), 0);
			String value1Ref = toString(rule.get("value1"));
			String operation = toString(rule.get("operation"));
			String value2Ref = toString(rule.get("value2"));

			Object resolvedValue1 = getNestedValue(item, value1Ref);
			String val1 = resolvedValue1 != null ? String.valueOf(resolvedValue1) : value1Ref;

			Object resolvedValue2 = getNestedValue(item, value2Ref);
			String val2 = resolvedValue2 != null ? String.valueOf(resolvedValue2) : value2Ref;

			if (evaluateOperation(val1, operation, val2, resolvedValue1)) {
				return output;
			}
		}
		return fallbackOutput;
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
