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
 * If Node - routes items to one of two outputs based on conditional logic.
 * Items that match all/any conditions go to output 0 (true branch),
 * items that don't match go to output 1 (false branch).
 */
@Slf4j
@Node(
	type = "if",
	displayName = "If",
	description = "Route items to different outputs based on conditions. Items matching the conditions go to the 'true' output, others go to 'false'.",
	category = "Flow",
	icon = "split"
)
public class IfNode extends AbstractNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
			NodeOutput.builder().name("true").displayName("True").build(),
			NodeOutput.builder().name("false").displayName("False").build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("conditions")
				.displayName("Conditions")
				.description("The conditions to evaluate for each item.")
				.type(ParameterType.FIXED_COLLECTION)
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("value1")
						.displayName("Value 1")
						.description("The first value to compare. Supports dot notation (e.g., 'json.user.name').")
						.type(ParameterType.STRING)
						.required(true)
						.placeHolder("value1")
						.build(),
					NodeParameter.builder()
						.name("operation")
						.displayName("Operation")
						.description("The comparison operation to perform.")
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
							ParameterOption.builder().name("Is Empty").value("isEmpty").build(),
							ParameterOption.builder().name("Is Not Empty").value("isNotEmpty").build(),
							ParameterOption.builder().name("Regex").value("regex").build(),
							ParameterOption.builder().name("Is True").value("isTrue").build(),
							ParameterOption.builder().name("Is False").value("isFalse").build()
						))
						.build(),
					NodeParameter.builder()
						.name("value2")
						.displayName("Value 2")
						.description("The second value to compare against (not needed for isEmpty, isNotEmpty, isTrue, isFalse).")
						.type(ParameterType.STRING)
						.placeHolder("value2")
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("combineOperation")
				.displayName("Combine Conditions")
				.description("How to combine multiple conditions.")
				.type(ParameterType.OPTIONS)
				.defaultValue("and")
				.options(List.of(
					ParameterOption.builder()
						.name("AND")
						.value("and")
						.description("All conditions must be true")
						.build(),
					ParameterOption.builder()
						.name("OR")
						.value("or")
						.description("At least one condition must be true")
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
			return NodeExecutionResult.successMultiOutput(List.of(List.of(), List.of()));
		}

		String combineOperation = context.getParameter("combineOperation", "and");
		Object conditionsObj = context.getParameter("conditions", null);
		List<Map<String, Object>> conditionsList = new ArrayList<>();

		if (conditionsObj instanceof List) {
			for (Object c : (List<?>) conditionsObj) {
				if (c instanceof Map) {
					conditionsList.add((Map<String, Object>) c);
				}
			}
		}

		List<Map<String, Object>> trueItems = new ArrayList<>();
		List<Map<String, Object>> falseItems = new ArrayList<>();

		for (Map<String, Object> item : inputData) {
			boolean result = evaluateConditions(item, conditionsList, combineOperation);
			if (result) {
				trueItems.add(item);
			} else {
				falseItems.add(item);
			}
		}

		log.debug("If node: {} items -> true={}, false={}", inputData.size(), trueItems.size(), falseItems.size());
		return NodeExecutionResult.successMultiOutput(List.of(trueItems, falseItems));
	}

	private boolean evaluateConditions(Map<String, Object> item, List<Map<String, Object>> conditions,
			String combineOperation) {
		if (conditions == null || conditions.isEmpty()) {
			return true; // No conditions means everything passes
		}

		boolean isAnd = "and".equals(combineOperation);

		for (Map<String, Object> condition : conditions) {
			boolean conditionResult = evaluateCondition(item, condition);
			if (isAnd && !conditionResult) {
				return false; // AND: any false means false
			}
			if (!isAnd && conditionResult) {
				return true; // OR: any true means true
			}
		}

		return isAnd; // AND: all true means true; OR: all false means false
	}

	private boolean evaluateCondition(Map<String, Object> item, Map<String, Object> condition) {
		String value1Ref = toString(condition.get("value1"));
		String operation = toString(condition.get("operation"));
		String value2Ref = toString(condition.get("value2"));

		// Resolve value1: try as a field reference first
		Object resolvedValue1 = getNestedValue(item, value1Ref);
		String val1 = resolvedValue1 != null ? String.valueOf(resolvedValue1) : value1Ref;

		// Resolve value2: try as a field reference first
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
			case "isEmpty":
				return rawVal1 == null || val1.isEmpty();
			case "isNotEmpty":
				return rawVal1 != null && !val1.isEmpty();
			case "regex":
				try {
					return Pattern.compile(val2).matcher(val1).find();
				} catch (Exception e) {
					log.warn("Invalid regex pattern: {}", val2);
					return false;
				}
			case "isTrue":
				return toBoolean(rawVal1 != null ? rawVal1 : val1, false);
			case "isFalse":
				return !toBoolean(rawVal1 != null ? rawVal1 : val1, true);
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
