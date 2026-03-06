package io.cwc.nodes.impl;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
 * Remove Duplicates Node - removes duplicate items from the input.
 *
 * Three operations:
 * 1. removeDuplicateInputItems — removes duplicates within the current input (default)
 * 2. removeItemsSeenInPreviousExecutions — persistent cross-execution deduplication
 * 3. clearDeduplicationHistory — wipes stored deduplication history
 *
 * Outputs:
 * - Output 0 (Kept): unique items (first occurrence kept)
 * - Output 1 (Duplicates): duplicate items that were removed
 */
@Slf4j
@Node(
	type = "removeDuplicates",
	displayName = "Remove Duplicates",
	description = "Remove duplicate items from the input based on field comparison, with optional cross-execution deduplication.",
	category = "Data Transformation",
	icon = "copy-minus"
)
public class RemoveDuplicatesNode extends AbstractNode {

	private static final int DEFAULT_HISTORY_SIZE = 10000;

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
			NodeOutput.builder().name("kept").displayName("Kept").build(),
			NodeOutput.builder().name("duplicates").displayName("Duplicates").build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			// --- Operation ---
			NodeParameter.builder()
				.name("operation")
				.displayName("Operation")
				.description("What operation to perform.")
				.type(ParameterType.OPTIONS)
				.defaultValue("removeDuplicateInputItems")
				.required(true)
				.options(List.of(
					ParameterOption.builder()
						.name("Remove Items Repeated Within Current Input")
						.value("removeDuplicateInputItems")
						.description("Remove duplicates from the items in the current input")
						.build(),
					ParameterOption.builder()
						.name("Remove Items Processed in Previous Executions")
						.value("removeItemsSeenInPreviousExecutions")
						.description("Remove items already seen in previous workflow executions")
						.build(),
					ParameterOption.builder()
						.name("Clear Deduplication History")
						.value("clearDeduplicationHistory")
						.description("Clear the stored deduplication history for this node")
						.build()
				))
				.build(),

			// --- Parameters for removeDuplicateInputItems ---
			NodeParameter.builder()
				.name("compare")
				.displayName("Compare")
				.description("How to identify duplicate items.")
				.type(ParameterType.OPTIONS)
				.defaultValue("allFields")
				.options(List.of(
					ParameterOption.builder()
						.name("All Fields")
						.value("allFields")
						.description("Compare all fields of each item")
						.build(),
					ParameterOption.builder()
						.name("All Fields Except")
						.value("allFieldsExcept")
						.description("Compare all fields except the specified ones")
						.build(),
					ParameterOption.builder()
						.name("Selected Fields")
						.value("selectedFields")
						.description("Compare only the specified fields")
						.build()
				))
				.displayOptions(Map.of("show", Map.of("operation", List.of("removeDuplicateInputItems"))))
				.build(),

			NodeParameter.builder()
				.name("fieldsToExclude")
				.displayName("Fields to Exclude")
				.description("Comma-separated list of field names to exclude from comparison.")
				.type(ParameterType.STRING)
				.placeHolder("field1, field2")
				.displayOptions(Map.of("show", Map.of("compare", List.of("allFieldsExcept"))))
				.build(),

			NodeParameter.builder()
				.name("fieldsToCompare")
				.displayName("Fields to Compare")
				.description("Comma-separated list of field names to use for comparison.")
				.type(ParameterType.STRING)
				.placeHolder("email, name")
				.displayOptions(Map.of("show", Map.of("compare", List.of("selectedFields"))))
				.build(),

			// --- Parameters for removeItemsSeenInPreviousExecutions ---
			NodeParameter.builder()
				.name("logic")
				.displayName("Logic")
				.description("How to detect items already seen in previous executions.")
				.type(ParameterType.OPTIONS)
				.defaultValue("removeItemsWithAlreadySeenKeyValues")
				.options(List.of(
					ParameterOption.builder()
						.name("Remove Items with Already Seen Key Values")
						.value("removeItemsWithAlreadySeenKeyValues")
						.description("Track unique values of a key field and remove items with previously seen values")
						.build(),
					ParameterOption.builder()
						.name("Remove Items Up to Stored Incremental Key")
						.value("removeItemsUpToStoredIncrementalKey")
						.description("Remove items with a numeric key value less than or equal to the highest previously seen value")
						.build(),
					ParameterOption.builder()
						.name("Remove Items Up to Stored Date")
						.value("removeItemsUpToStoredDate")
						.description("Remove items with a date field value less than or equal to the latest previously seen date")
						.build()
				))
				.displayOptions(Map.of("show", Map.of("operation", List.of("removeItemsSeenInPreviousExecutions"))))
				.build(),

			NodeParameter.builder()
				.name("dedupeValue")
				.displayName("Value to Dedupe On")
				.description("The field whose value is used to identify duplicates across executions.")
				.type(ParameterType.STRING)
				.placeHolder("id")
				.displayOptions(Map.of("show", Map.of("logic", List.of("removeItemsWithAlreadySeenKeyValues"))))
				.build(),

			NodeParameter.builder()
				.name("incrementalDedupeValue")
				.displayName("Value to Dedupe On")
				.description("The field containing a numeric value that increases with each new item (e.g., auto-increment ID).")
				.type(ParameterType.STRING)
				.placeHolder("id")
				.displayOptions(Map.of("show", Map.of("logic", List.of("removeItemsUpToStoredIncrementalKey"))))
				.build(),

			NodeParameter.builder()
				.name("dateDedupeValue")
				.displayName("Value to Dedupe On")
				.description("The field containing a date/timestamp used for comparison (ISO 8601 format).")
				.type(ParameterType.STRING)
				.placeHolder("createdAt")
				.displayOptions(Map.of("show", Map.of("logic", List.of("removeItemsUpToStoredDate"))))
				.build(),

			// --- Options ---
			NodeParameter.builder()
				.name("options")
				.displayName("Options")
				.type(ParameterType.COLLECTION)
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("disableDotNotation")
						.displayName("Disable Dot Notation")
						.description("When enabled, field names with dots are treated literally instead of as nested paths.")
						.type(ParameterType.BOOLEAN)
						.defaultValue(false)
						.build(),
					NodeParameter.builder()
						.name("removeOtherFields")
						.displayName("Remove Other Fields")
						.description("When enabled, only the comparison fields are kept in the output.")
						.type(ParameterType.BOOLEAN)
						.defaultValue(false)
						.displayOptions(Map.of("show", Map.of("operation", List.of("removeDuplicateInputItems"))))
						.build(),
					NodeParameter.builder()
						.name("scope")
						.displayName("Scope")
						.description("Where to store the deduplication history. Node scope is per node instance; Workflow scope is shared across all Remove Duplicates nodes in the workflow.")
						.type(ParameterType.OPTIONS)
						.defaultValue("node")
						.options(List.of(
							ParameterOption.builder().name("Node").value("node").description("History is stored per node instance").build(),
							ParameterOption.builder().name("Workflow").value("workflow").description("History is shared across the workflow").build()
						))
						.displayOptions(Map.of("show", Map.of("operation", List.of("removeItemsSeenInPreviousExecutions"))))
						.build(),
					NodeParameter.builder()
						.name("historySize")
						.displayName("History Size")
						.description("Maximum number of entries to keep in the deduplication history (oldest entries are evicted first). Only applies to the 'Already Seen Key Values' logic.")
						.type(ParameterType.NUMBER)
						.defaultValue(DEFAULT_HISTORY_SIZE)
						.displayOptions(Map.of("show", Map.of("logic", List.of("removeItemsWithAlreadySeenKeyValues"))))
						.build()
				))
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String operation = context.getParameter("operation", "removeDuplicateInputItems");

		switch (operation) {
			case "removeItemsSeenInPreviousExecutions":
				return executeDedupeAcrossExecutions(context);
			case "clearDeduplicationHistory":
				return executeClearHistory(context);
			default:
				return executeRemoveDuplicateInputItems(context);
		}
	}

	// ======================== Operation 1: Remove Duplicate Input Items ========================

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeRemoveDuplicateInputItems(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.successMultiOutput(List.of(List.of(), List.of()));
		}

		String compare = context.getParameter("compare", "allFields");
		String fieldsToExclude = context.getParameter("fieldsToExclude", "");
		String fieldsToCompare = context.getParameter("fieldsToCompare", "");

		boolean disableDotNotation = false;
		boolean removeOtherFields = false;
		Object optionsObj = context.getParameter("options", null);
		if (optionsObj instanceof Map) {
			Map<String, Object> opts = (Map<String, Object>) optionsObj;
			disableDotNotation = toBoolean(opts.get("disableDotNotation"), false);
			removeOtherFields = toBoolean(opts.get("removeOtherFields"), false);
		}

		Set<String> excludeFields = parseFieldList(fieldsToExclude);
		Set<String> compareFields = parseFieldList(fieldsToCompare);

		List<Map<String, Object>> kept = new ArrayList<>();
		List<Map<String, Object>> duplicates = new ArrayList<>();
		Set<String> seenKeys = new LinkedHashSet<>();

		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = unwrapJson(item);
			String key = buildComparisonKey(json, compare, compareFields, excludeFields, disableDotNotation);

			if (seenKeys.add(key)) {
				if (removeOtherFields && "selectedFields".equals(compare) && !compareFields.isEmpty()) {
					kept.add(wrapInJson(pickFields(json, compareFields, disableDotNotation)));
				} else {
					kept.add(deepClone(item));
				}
			} else {
				duplicates.add(deepClone(item));
			}
		}

		log.debug("RemoveDuplicates: {} items -> {} kept, {} duplicates (compare={})",
				inputData.size(), kept.size(), duplicates.size(), compare);

		return NodeExecutionResult.successMultiOutput(List.of(kept, duplicates));
	}

	// ======================== Operation 2: Remove Items Seen in Previous Executions ========================

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeDedupeAcrossExecutions(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.successMultiOutput(List.of(List.of(), List.of()));
		}

		String logic = context.getParameter("logic", "removeItemsWithAlreadySeenKeyValues");
		String scope = "node";
		int historySize = DEFAULT_HISTORY_SIZE;

		Object optionsObj = context.getParameter("options", null);
		if (optionsObj instanceof Map) {
			Map<String, Object> opts = (Map<String, Object>) optionsObj;
			scope = String.valueOf(opts.getOrDefault("scope", "node"));
			historySize = Math.max(1, toInt(opts.get("historySize"), DEFAULT_HISTORY_SIZE));
		}

		Map<String, Object> staticData = getStaticDataStore(context, scope);
		String storageKey = getStorageKey(context, scope);

		switch (logic) {
			case "removeItemsUpToStoredIncrementalKey":
				return dedupeIncremental(context, inputData, staticData, storageKey);
			case "removeItemsUpToStoredDate":
				return dedupeDate(context, inputData, staticData, storageKey);
			default:
				return dedupeUniqueValues(context, inputData, staticData, storageKey, historySize);
		}
	}

	/**
	 * Logic: Remove items with already seen key values.
	 * Stores a set of previously seen values (bounded by historySize, FIFO eviction).
	 */
	@SuppressWarnings("unchecked")
	private NodeExecutionResult dedupeUniqueValues(NodeExecutionContext context,
			List<Map<String, Object>> inputData, Map<String, Object> staticData,
			String storageKey, int historySize) {
		String fieldName = context.getParameter("dedupeValue", "id");

		// Load existing history
		LinkedList<String> history;
		Object stored = staticData.get(storageKey);
		if (stored instanceof List) {
			history = new LinkedList<>((List<String>) stored);
		} else {
			history = new LinkedList<>();
		}
		Set<String> historySet = new LinkedHashSet<>(history);

		List<Map<String, Object>> kept = new ArrayList<>();
		List<Map<String, Object>> duplicates = new ArrayList<>();

		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = unwrapJson(item);
			Object value = getNestedValue(wrapInJson(json), "json." + fieldName);
			String key = value != null ? String.valueOf(value) : "";

			if (historySet.add(key)) {
				history.add(key);
				// Evict oldest entries if over historySize
				while (history.size() > historySize) {
					String evicted = history.removeFirst();
					historySet.remove(evicted);
				}
				kept.add(deepClone(item));
			} else {
				duplicates.add(deepClone(item));
			}
		}

		// Persist updated history
		staticData.put(storageKey, new ArrayList<>(history));

		log.debug("RemoveDuplicates (unique values): {} items -> {} new, {} seen before (history size={})",
				inputData.size(), kept.size(), duplicates.size(), history.size());

		return buildResultWithStaticData(context, kept, duplicates);
	}

	/**
	 * Logic: Remove items up to stored incremental key.
	 * Stores the highest numeric value seen; items with value <= stored max are removed.
	 */
	private NodeExecutionResult dedupeIncremental(NodeExecutionContext context,
			List<Map<String, Object>> inputData, Map<String, Object> staticData,
			String storageKey) {
		String fieldName = context.getParameter("incrementalDedupeValue", "id");

		double storedMax = toDouble(staticData.get(storageKey), Double.NEGATIVE_INFINITY);

		List<Map<String, Object>> kept = new ArrayList<>();
		List<Map<String, Object>> duplicates = new ArrayList<>();
		double newMax = storedMax;

		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = unwrapJson(item);
			Object value = getNestedValue(wrapInJson(json), "json." + fieldName);
			double numValue = toDouble(value, Double.NEGATIVE_INFINITY);

			if (numValue > storedMax) {
				kept.add(deepClone(item));
				if (numValue > newMax) {
					newMax = numValue;
				}
			} else {
				duplicates.add(deepClone(item));
			}
		}

		staticData.put(storageKey, newMax);

		log.debug("RemoveDuplicates (incremental): {} items -> {} new, {} already seen (max={})",
				inputData.size(), kept.size(), duplicates.size(), newMax);

		return buildResultWithStaticData(context, kept, duplicates);
	}

	/**
	 * Logic: Remove items up to stored date.
	 * Stores the latest date seen; items with date <= stored latest are removed.
	 */
	private NodeExecutionResult dedupeDate(NodeExecutionContext context,
			List<Map<String, Object>> inputData, Map<String, Object> staticData,
			String storageKey) {
		String fieldName = context.getParameter("dateDedupeValue", "createdAt");

		String storedDateStr = staticData.get(storageKey) instanceof String
				? (String) staticData.get(storageKey) : null;
		Instant storedDate = parseInstant(storedDateStr);

		List<Map<String, Object>> kept = new ArrayList<>();
		List<Map<String, Object>> duplicates = new ArrayList<>();
		Instant newLatest = storedDate;

		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = unwrapJson(item);
			Object value = getNestedValue(wrapInJson(json), "json." + fieldName);
			Instant itemDate = parseInstant(value);

			if (itemDate == null) {
				// Items without a parseable date are kept
				kept.add(deepClone(item));
			} else if (storedDate == null || itemDate.isAfter(storedDate)) {
				kept.add(deepClone(item));
				if (newLatest == null || itemDate.isAfter(newLatest)) {
					newLatest = itemDate;
				}
			} else {
				duplicates.add(deepClone(item));
			}
		}

		if (newLatest != null) {
			staticData.put(storageKey, newLatest.toString());
		}

		log.debug("RemoveDuplicates (date): {} items -> {} new, {} already seen (latest={})",
				inputData.size(), kept.size(), duplicates.size(), newLatest);

		return buildResultWithStaticData(context, kept, duplicates);
	}

	// ======================== Operation 3: Clear Deduplication History ========================

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeClearHistory(NodeExecutionContext context) {
		String scope = "node";
		Object optionsObj = context.getParameter("options", null);
		if (optionsObj instanceof Map) {
			Map<String, Object> opts = (Map<String, Object>) optionsObj;
			scope = String.valueOf(opts.getOrDefault("scope", "node"));
		}

		Map<String, Object> staticData = getStaticDataStore(context, scope);
		String storageKey = getStorageKey(context, scope);

		staticData.remove(storageKey);

		log.info("RemoveDuplicates: cleared deduplication history (scope={}, key={})", scope, storageKey);

		// Return all input items unchanged, with empty duplicates output
		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> output = inputData != null ? new ArrayList<>(inputData) : List.of();

		return buildResultWithStaticData(context, output, List.of());
	}

	// ======================== Static Data Helpers ========================

	/**
	 * Get the appropriate static data store based on scope.
	 * - "node" scope: data is namespaced by nodeId within workflowStaticData
	 * - "workflow" scope: data is stored directly in workflowStaticData under a shared key
	 */
	private Map<String, Object> getStaticDataStore(NodeExecutionContext context, String scope) {
		Map<String, Object> wfStaticData = context.getWorkflowStaticData();
		if (wfStaticData == null) {
			// Fallback — shouldn't happen if engine is wired up correctly
			return new LinkedHashMap<>();
		}
		return wfStaticData;
	}

	/**
	 * Get the storage key for the deduplication data.
	 * Node scope uses the nodeId to prevent collisions between multiple instances.
	 * Workflow scope uses a fixed key shared by all Remove Duplicates nodes.
	 */
	private String getStorageKey(NodeExecutionContext context, String scope) {
		if ("workflow".equals(scope)) {
			return "dedup_shared";
		}
		return "dedup_" + context.getNodeId();
	}

	/**
	 * Build a result that includes the updated workflowStaticData for persistence.
	 */
	private NodeExecutionResult buildResultWithStaticData(NodeExecutionContext context,
			List<Map<String, Object>> kept, List<Map<String, Object>> duplicates) {
		return NodeExecutionResult.builder()
				.output(List.of(kept, duplicates))
				.staticData(context.getWorkflowStaticData())
				.build();
	}

	// ======================== Comparison Key Logic (for operation 1) ========================

	private String buildComparisonKey(Map<String, Object> json, String compare,
			Set<String> compareFields, Set<String> excludeFields, boolean disableDotNotation) {
		Map<String, Object> keyMap;

		switch (compare) {
			case "selectedFields":
				keyMap = pickFields(json, compareFields, disableDotNotation);
				break;

			case "allFieldsExcept":
				keyMap = new LinkedHashMap<>(json);
				for (String field : excludeFields) {
					if (disableDotNotation) {
						keyMap.remove(field);
					} else {
						removeField(keyMap, field);
					}
				}
				break;

			default: // allFields
				keyMap = json;
				break;
		}

		return stableStringify(keyMap);
	}

	private Map<String, Object> pickFields(Map<String, Object> json, Set<String> fields,
			boolean disableDotNotation) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (String field : fields) {
			Object value;
			if (disableDotNotation) {
				value = json.get(field);
			} else {
				value = getNestedValue(wrapInJson(json), "json." + field);
			}
			if (value != null) {
				result.put(field, value);
			}
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private void removeField(Map<String, Object> map, String field) {
		String[] parts = field.split("\\.");
		if (parts.length == 1) {
			map.remove(parts[0]);
			return;
		}
		Map<String, Object> current = map;
		for (int i = 0; i < parts.length - 1; i++) {
			Object next = current.get(parts[i]);
			if (next instanceof Map) {
				current = (Map<String, Object>) next;
			} else {
				return;
			}
		}
		current.remove(parts[parts.length - 1]);
	}

	// ======================== Utility Methods ========================

	private String stableStringify(Map<String, Object> map) {
		if (map == null || map.isEmpty()) return "{}";

		StringBuilder sb = new StringBuilder("{");
		List<String> keys = new ArrayList<>(map.keySet());
		Collections.sort(keys);

		for (int i = 0; i < keys.size(); i++) {
			if (i > 0) sb.append(",");
			String key = keys.get(i);
			Object value = map.get(key);
			sb.append("\"").append(key).append("\":");
			sb.append(stableStringifyValue(value));
		}
		sb.append("}");
		return sb.toString();
	}

	@SuppressWarnings("unchecked")
	private String stableStringifyValue(Object value) {
		if (value == null) return "null";
		if (value instanceof Map) return stableStringify((Map<String, Object>) value);
		if (value instanceof List) {
			List<?> list = (List<?>) value;
			return "[" + list.stream()
				.map(this::stableStringifyValue)
				.collect(Collectors.joining(",")) + "]";
		}
		if (value instanceof String) return "\"" + value + "\"";
		return Objects.toString(value);
	}

	private Set<String> parseFieldList(String fieldList) {
		if (fieldList == null || fieldList.isBlank()) return Set.of();
		return Arrays.stream(fieldList.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private Instant parseInstant(Object value) {
		if (value == null) return null;
		if (value instanceof Instant) return (Instant) value;
		String str = String.valueOf(value).trim();
		if (str.isEmpty()) return null;
		try {
			return Instant.parse(str);
		} catch (DateTimeParseException e) {
			// Try parsing as epoch millis
			try {
				long millis = Long.parseLong(str);
				return Instant.ofEpochMilli(millis);
			} catch (NumberFormatException e2) {
				log.warn("Cannot parse date value '{}' for deduplication", str);
				return null;
			}
		}
	}
}
