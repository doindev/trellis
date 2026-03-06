package io.cwc.nodes.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
 * Filter Node - removes items that don't match the specified conditions.
 * Items that pass all/any conditions go to output 0 (Kept),
 * items that fail go to output 1 (Discarded).
 *
 * Supports comprehensive operators across data types:
 * string, number, boolean, dateTime, array, object.
 */
@Slf4j
@Node(
	type = "filter",
	displayName = "Filter",
	description = "Remove items matching a condition. Items that pass go to 'Kept', others go to 'Discarded'.",
	category = "Flow",
	icon = "list-filter"
)
public class FilterNode extends AbstractNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
			NodeOutput.builder().name("kept").displayName("kept").build(),
			NodeOutput.builder().name("discarded").displayName("discarded").build()
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
						.description("The value or field reference to test (left side). Supports dot notation.")
						.type(ParameterType.STRING)
						.required(true)
						.placeHolder("json.field")
						.build(),
					NodeParameter.builder()
						.name("dataType")
						.displayName("Data Type")
						.description("The data type of the value being compared.")
						.type(ParameterType.OPTIONS)
						.defaultValue("string")
						.options(List.of(
							ParameterOption.builder().name("String").value("string").build(),
							ParameterOption.builder().name("Number").value("number").build(),
							ParameterOption.builder().name("Boolean").value("boolean").build(),
							ParameterOption.builder().name("Date & Time").value("dateTime").build(),
							ParameterOption.builder().name("Array").value("array").build(),
							ParameterOption.builder().name("Object").value("object").build()
						))
						.build(),
					NodeParameter.builder()
						.name("operation")
						.displayName("Operation")
						.description("The comparison operation to perform.")
						.type(ParameterType.OPTIONS)
						.defaultValue("equals")
						.options(List.of(
							// Universal
							ParameterOption.builder().name("Exists").value("exists").description("Value is not null/undefined").build(),
							ParameterOption.builder().name("Does Not Exist").value("notExists").description("Value is null/undefined").build(),
							// String
							ParameterOption.builder().name("Equals").value("equals").build(),
							ParameterOption.builder().name("Not Equals").value("notEquals").build(),
							ParameterOption.builder().name("Contains").value("contains").build(),
							ParameterOption.builder().name("Does Not Contain").value("notContains").build(),
							ParameterOption.builder().name("Starts With").value("startsWith").build(),
							ParameterOption.builder().name("Does Not Start With").value("notStartsWith").build(),
							ParameterOption.builder().name("Ends With").value("endsWith").build(),
							ParameterOption.builder().name("Does Not End With").value("notEndsWith").build(),
							ParameterOption.builder().name("Matches Regex").value("regex").build(),
							ParameterOption.builder().name("Does Not Match Regex").value("notRegex").build(),
							// Number / DateTime
							ParameterOption.builder().name("Greater Than").value("gt").build(),
							ParameterOption.builder().name("Less Than").value("lt").build(),
							ParameterOption.builder().name("Greater Than or Equal").value("gte").build(),
							ParameterOption.builder().name("Less Than or Equal").value("lte").build(),
							// DateTime aliases
							ParameterOption.builder().name("After").value("after").build(),
							ParameterOption.builder().name("Before").value("before").build(),
							ParameterOption.builder().name("After or Equals").value("afterOrEquals").build(),
							ParameterOption.builder().name("Before or Equals").value("beforeOrEquals").build(),
							// Boolean
							ParameterOption.builder().name("Is True").value("isTrue").build(),
							ParameterOption.builder().name("Is False").value("isFalse").build(),
							// Common
							ParameterOption.builder().name("Is Empty").value("empty").build(),
							ParameterOption.builder().name("Is Not Empty").value("notEmpty").build(),
							// Array
							ParameterOption.builder().name("Array Contains").value("arrayContains").build(),
							ParameterOption.builder().name("Array Does Not Contain").value("arrayNotContains").build(),
							ParameterOption.builder().name("Array Length Equals").value("lengthEquals").build(),
							ParameterOption.builder().name("Array Length Greater Than").value("lengthGt").build(),
							ParameterOption.builder().name("Array Length Less Than").value("lengthLt").build()
						))
						.build(),
					NodeParameter.builder()
						.name("value2")
						.displayName("Value 2")
						.description("The second value to compare against (right side). Not needed for exists, empty, isTrue, isFalse operations.")
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
				.build(),

			NodeParameter.builder()
				.name("ignoreCase")
				.displayName("Ignore Case")
				.description("Whether to ignore letter case when evaluating string conditions.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(true)
				.isNodeSetting(true)
				.build(),

			NodeParameter.builder()
				.name("looseTypeValidation")
				.displayName("Convert Types Where Required")
				.description("Try to convert values to the expected type before comparing (e.g. string '123' to number 123).")
				.type(ParameterType.BOOLEAN)
				.defaultValue(true)
				.isNodeSetting(true)
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
		boolean ignoreCase = toBoolean(context.getParameter("ignoreCase", true), true);
		boolean looseType = toBoolean(context.getParameter("looseTypeValidation", true), true);

		Object conditionsObj = context.getParameter("conditions", null);
		List<Map<String, Object>> conditionsList = new ArrayList<>();

		if (conditionsObj instanceof List) {
			for (Object c : (List<?>) conditionsObj) {
				if (c instanceof Map) {
					conditionsList.add((Map<String, Object>) c);
				}
			}
		}

		List<Map<String, Object>> keptItems = new ArrayList<>();
		List<Map<String, Object>> discardedItems = new ArrayList<>();

		for (Map<String, Object> item : inputData) {
			try {
				boolean pass = evaluateConditions(item, conditionsList, combineOperation, ignoreCase, looseType);
				if (pass) {
					keptItems.add(item);
				} else {
					discardedItems.add(item);
				}
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					discardedItems.add(item);
				} else {
					return handleError(context, "Filter condition evaluation failed: " + e.getMessage(), e);
				}
			}
		}

		log.debug("Filter node: {} items -> kept={}, discarded={}", inputData.size(), keptItems.size(), discardedItems.size());
		return NodeExecutionResult.successMultiOutput(List.of(keptItems, discardedItems));
	}

	private boolean evaluateConditions(Map<String, Object> item, List<Map<String, Object>> conditions,
			String combineOperation, boolean ignoreCase, boolean looseType) {
		if (conditions == null || conditions.isEmpty()) {
			return true;
		}

		boolean isAnd = "and".equals(combineOperation);

		for (Map<String, Object> condition : conditions) {
			boolean result = evaluateCondition(item, condition, ignoreCase, looseType);
			if (isAnd && !result) return false;
			if (!isAnd && result) return true;
		}

		return isAnd;
	}

	private boolean evaluateCondition(Map<String, Object> item, Map<String, Object> condition,
			boolean ignoreCase, boolean looseType) {
		String value1Ref = toString(condition.get("value1"));
		String operation = toString(condition.get("operation"));
		String dataType = toString(condition.get("dataType"));
		String value2Ref = toString(condition.get("value2"));

		if (dataType == null || dataType.isEmpty()) {
			dataType = "string";
		}

		// Resolve value1 as field reference
		Object leftRaw = getNestedValue(item, value1Ref);

		// Resolve value2 as field reference or literal
		Object rightRaw = null;
		if (value2Ref != null && !value2Ref.isEmpty()) {
			Object resolved = getNestedValue(item, value2Ref);
			rightRaw = resolved != null ? resolved : value2Ref;
		}

		// Universal exists/notExists
		boolean exists = leftRaw != null && !(leftRaw instanceof Double && Double.isNaN((Double) leftRaw));
		if ("exists".equals(operation)) return exists;
		if ("notExists".equals(operation)) return !exists;

		switch (dataType) {
			case "string":
				return evaluateString(leftRaw, operation, rightRaw, ignoreCase);
			case "number":
				return evaluateNumber(leftRaw, operation, rightRaw, exists, looseType);
			case "boolean":
				return evaluateBoolean(leftRaw, operation, rightRaw, exists, looseType);
			case "dateTime":
				return evaluateDateTime(leftRaw, operation, rightRaw, exists, looseType);
			case "array":
				return evaluateArray(leftRaw, operation, rightRaw, exists, ignoreCase, looseType);
			case "object":
				return evaluateObject(leftRaw, operation, exists);
			default:
				// Default to string comparison
				return evaluateString(leftRaw, operation, rightRaw, ignoreCase);
		}
	}

	// ── String operations ──

	private boolean evaluateString(Object leftRaw, String operation, Object rightRaw, boolean ignoreCase) {
		String left = leftRaw != null ? String.valueOf(leftRaw) : "";
		String right = rightRaw != null ? String.valueOf(rightRaw) : "";

		// Apply case insensitivity (except regex)
		if (ignoreCase && !"regex".equals(operation) && !"notRegex".equals(operation)) {
			left = left.toLowerCase();
			right = right.toLowerCase();
		}

		switch (operation) {
			case "empty":
				return leftRaw == null || left.isEmpty();
			case "notEmpty":
				return leftRaw != null && !left.isEmpty();
			case "equals":
				return left.equals(right);
			case "notEquals":
				return !left.equals(right);
			case "contains":
				return left.contains(right);
			case "notContains":
				return !left.contains(right);
			case "startsWith":
				return left.startsWith(right);
			case "notStartsWith":
				return !left.startsWith(right);
			case "endsWith":
				return left.endsWith(right);
			case "notEndsWith":
				return !left.endsWith(right);
			case "regex":
				return matchesRegex(leftRaw != null ? String.valueOf(leftRaw) : "", right);
			case "notRegex":
				return !matchesRegex(leftRaw != null ? String.valueOf(leftRaw) : "", right);
			default:
				log.warn("Unknown string operation: {}", operation);
				return false;
		}
	}

	private boolean matchesRegex(String value, String pattern) {
		try {
			// Support /pattern/flags syntax
			if (pattern.startsWith("/")) {
				int lastSlash = pattern.lastIndexOf('/');
				if (lastSlash > 0 && lastSlash != pattern.indexOf('/')) {
					String regex = pattern.substring(1, lastSlash);
					String flags = pattern.substring(lastSlash + 1);
					int flagBits = 0;
					if (flags.contains("i")) flagBits |= Pattern.CASE_INSENSITIVE;
					if (flags.contains("m")) flagBits |= Pattern.MULTILINE;
					if (flags.contains("s")) flagBits |= Pattern.DOTALL;
					return Pattern.compile(regex, flagBits).matcher(value).find();
				}
			}
			return Pattern.compile(pattern).matcher(value).find();
		} catch (PatternSyntaxException e) {
			log.warn("Invalid regex pattern: {}", pattern);
			return false;
		}
	}

	// ── Number operations ──

	private boolean evaluateNumber(Object leftRaw, String operation, Object rightRaw,
			boolean exists, boolean looseType) {
		switch (operation) {
			case "empty":
				return !exists;
			case "notEmpty":
				return exists;
		}

		double left = toDouble(leftRaw, Double.NaN);
		double right = toDouble(rightRaw, Double.NaN);

		if (Double.isNaN(left) && looseType && leftRaw != null) {
			// Try parsing string to number
			try { left = Double.parseDouble(String.valueOf(leftRaw).trim()); } catch (Exception ignored) {}
		}
		if (Double.isNaN(right) && looseType && rightRaw != null) {
			try { right = Double.parseDouble(String.valueOf(rightRaw).trim()); } catch (Exception ignored) {}
		}

		switch (operation) {
			case "equals":
				return Double.compare(left, right) == 0;
			case "notEquals":
				return Double.compare(left, right) != 0;
			case "gt":
			case "greaterThan":
				return left > right;
			case "lt":
			case "lessThan":
				return left < right;
			case "gte":
				return left >= right;
			case "lte":
				return left <= right;
			default:
				log.warn("Unknown number operation: {}", operation);
				return false;
		}
	}

	// ── Boolean operations ──

	private boolean evaluateBoolean(Object leftRaw, String operation, Object rightRaw,
			boolean exists, boolean looseType) {
		switch (operation) {
			case "empty":
				return !exists;
			case "notEmpty":
				return exists;
			case "isTrue":
				return toBoolean(leftRaw, false);
			case "isFalse":
				return !toBoolean(leftRaw, true);
		}

		boolean left = toBoolean(leftRaw, false);
		boolean right = toBoolean(rightRaw, false);

		switch (operation) {
			case "equals":
				return left == right;
			case "notEquals":
				return left != right;
			default:
				log.warn("Unknown boolean operation: {}", operation);
				return false;
		}
	}

	// ── DateTime operations ──

	private boolean evaluateDateTime(Object leftRaw, String operation, Object rightRaw,
			boolean exists, boolean looseType) {
		switch (operation) {
			case "empty":
				return !exists;
			case "notEmpty":
				return exists;
		}

		long leftMs = parseDateTime(leftRaw);
		long rightMs = parseDateTime(rightRaw);

		if (leftMs == Long.MIN_VALUE || rightMs == Long.MIN_VALUE) {
			return false; // Cannot parse dates
		}

		switch (operation) {
			case "equals":
				return leftMs == rightMs;
			case "notEquals":
				return leftMs != rightMs;
			case "after":
			case "gt":
				return leftMs > rightMs;
			case "before":
			case "lt":
				return leftMs < rightMs;
			case "afterOrEquals":
			case "gte":
				return leftMs >= rightMs;
			case "beforeOrEquals":
			case "lte":
				return leftMs <= rightMs;
			default:
				log.warn("Unknown dateTime operation: {}", operation);
				return false;
		}
	}

	private long parseDateTime(Object value) {
		if (value == null) return Long.MIN_VALUE;
		if (value instanceof Number) return ((Number) value).longValue();

		String str = String.valueOf(value).trim();
		if (str.isEmpty()) return Long.MIN_VALUE;

		// Try parsing as epoch millis
		try { return Long.parseLong(str); } catch (NumberFormatException ignored) {}

		// Try ISO instant (e.g., 2024-01-15T10:30:00Z)
		try { return Instant.parse(str).toEpochMilli(); } catch (DateTimeParseException ignored) {}

		// Try ZonedDateTime (e.g., 2024-01-15T10:30:00+05:00)
		try { return ZonedDateTime.parse(str).toInstant().toEpochMilli(); } catch (DateTimeParseException ignored) {}

		// Try LocalDateTime (e.g., 2024-01-15T10:30:00)
		try { return LocalDateTime.parse(str).atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(); } catch (DateTimeParseException ignored) {}

		// Try LocalDate (e.g., 2024-01-15)
		try { return LocalDate.parse(str).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(); } catch (DateTimeParseException ignored) {}

		log.warn("Could not parse date/time value: {}", str);
		return Long.MIN_VALUE;
	}

	// ── Array operations ──

	private boolean evaluateArray(Object leftRaw, String operation, Object rightRaw,
			boolean exists, boolean ignoreCase, boolean looseType) {
		switch (operation) {
			case "empty":
				if (leftRaw == null) return true;
				if (leftRaw instanceof Collection) return ((Collection<?>) leftRaw).isEmpty();
				if (leftRaw instanceof List) return ((List<?>) leftRaw).isEmpty();
				return false;
			case "notEmpty":
				if (leftRaw == null) return false;
				if (leftRaw instanceof Collection) return !((Collection<?>) leftRaw).isEmpty();
				if (leftRaw instanceof List) return !((List<?>) leftRaw).isEmpty();
				return true;
		}

		List<?> array;
		if (leftRaw instanceof List) {
			array = (List<?>) leftRaw;
		} else if (leftRaw == null) {
			array = List.of();
		} else {
			array = List.of(leftRaw);
		}

		switch (operation) {
			case "arrayContains":
			case "contains":
				return arrayContains(array, rightRaw, ignoreCase);
			case "arrayNotContains":
			case "notContains":
				return !arrayContains(array, rightRaw, ignoreCase);
			case "lengthEquals":
				return array.size() == toInt(rightRaw, 0);
			case "lengthGt":
				return array.size() > toInt(rightRaw, 0);
			case "lengthLt":
				return array.size() < toInt(rightRaw, 0);
			case "exists":
				return exists;
			case "notExists":
				return !exists;
			default:
				log.warn("Unknown array operation: {}", operation);
				return false;
		}
	}

	private boolean arrayContains(List<?> array, Object value, boolean ignoreCase) {
		if (value == null) return array.contains(null);

		String searchStr = String.valueOf(value);
		for (Object item : array) {
			if (item == null) continue;
			if (item.equals(value)) return true;
			// String comparison with optional case insensitivity
			String itemStr = String.valueOf(item);
			if (ignoreCase) {
				if (itemStr.equalsIgnoreCase(searchStr)) return true;
			} else {
				if (itemStr.equals(searchStr)) return true;
			}
		}
		return false;
	}

	// ── Object operations ──

	private boolean evaluateObject(Object leftRaw, String operation, boolean exists) {
		switch (operation) {
			case "empty":
				if (leftRaw == null) return true;
				if (leftRaw instanceof Map) return ((Map<?, ?>) leftRaw).isEmpty();
				return false;
			case "notEmpty":
				if (leftRaw == null) return false;
				if (leftRaw instanceof Map) return !((Map<?, ?>) leftRaw).isEmpty();
				return true;
			case "exists":
				return exists;
			case "notExists":
				return !exists;
			default:
				log.warn("Unknown object operation: {}", operation);
				return false;
		}
	}
}
