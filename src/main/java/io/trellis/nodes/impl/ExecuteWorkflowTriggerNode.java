package io.trellis.nodes.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractTriggerNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Execute Workflow Trigger - entry point for sub-workflows.
 * When a parent workflow calls this workflow via the Execute Sub-Workflow node,
 * this trigger receives the input data and passes it through to downstream nodes.
 *
 * Supports three input data modes:
 * - Accept All Data: passes through all incoming data unchanged
 * - Define Using Fields Below: filters input to only declared fields (with types)
 * - Define Using JSON Example: infers field schema from a sample JSON object
 */
@Slf4j
@Node(
	type = "executeWorkflowTrigger",
	displayName = "When Called by Another Workflow",
	description = "Starts when called by the Execute Sub-Workflow node. Define expected input fields or accept all data from the parent workflow.",
	category = "Core Triggers",
	icon = "play",
	trigger = true
)
public class ExecuteWorkflowTriggerNode extends AbstractTriggerNode {

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			// Informational notice
			NodeParameter.builder()
				.name("notice")
				.displayName("")
				.description("When an Execute Sub-Workflow node calls this workflow, the execution starts here. Any data passed into that node will be output by this node.")
				.type(ParameterType.NOTICE)
				.noDataExpression(true)
				.build(),

			// Input data mode selector
			NodeParameter.builder()
				.name("inputSource")
				.displayName("Input Data Mode")
				.description("How to handle incoming data from the parent workflow.")
				.type(ParameterType.OPTIONS)
				.defaultValue("passthrough")
				.noDataExpression(true)
				.options(List.of(
					ParameterOption.builder()
						.name("Accept All Data")
						.value("passthrough")
						.description("Use all incoming data from the parent workflow unchanged")
						.build(),
					ParameterOption.builder()
						.name("Define Using Fields Below")
						.value("workflowInputs")
						.description("Define expected input fields with names and types")
						.build(),
					ParameterOption.builder()
						.name("Define Using JSON Example")
						.value("jsonExample")
						.description("Infer input schema from an example JSON object")
						.build()
				))
				.build(),

			// Workflow Input Schema (shown when inputSource = workflowInputs)
			NodeParameter.builder()
				.name("workflowInputs")
				.displayName("Workflow Input Schema")
				.description("Define expected input fields. Only these fields will be passed through from the parent workflow. Missing fields will be set to null.")
				.type(ParameterType.FIXED_COLLECTION)
				.defaultValue(Map.of())
				.displayOptions(Map.of("show", Map.of("inputSource", List.of("workflowInputs"))))
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("name")
						.displayName("Name")
						.type(ParameterType.STRING)
						.required(true)
						.placeHolder("e.g. fieldName")
						.description("A unique name for this workflow input, used to reference it from the parent workflow")
						.build(),
					NodeParameter.builder()
						.name("type")
						.displayName("Type")
						.type(ParameterType.OPTIONS)
						.defaultValue("string")
						.required(true)
						.description("Expected data type for this input value")
						.options(List.of(
							ParameterOption.builder().name("Allow Any Type").value("any").build(),
							ParameterOption.builder().name("String").value("string").build(),
							ParameterOption.builder().name("Number").value("number").build(),
							ParameterOption.builder().name("Boolean").value("boolean").build(),
							ParameterOption.builder().name("Array").value("array").build(),
							ParameterOption.builder().name("Object").value("object").build()
						))
						.build()
				))
				.build(),

			// JSON Example notice (shown when inputSource = jsonExample)
			NodeParameter.builder()
				.name("jsonExampleNotice")
				.displayName("")
				.description("Provide an example JSON object to infer field names. Only the top-level keys will be used as the input schema. Set a value to null to allow any type for that field.")
				.type(ParameterType.NOTICE)
				.noDataExpression(true)
				.displayOptions(Map.of("show", Map.of("inputSource", List.of("jsonExample"))))
				.build(),

			// JSON Example editor (shown when inputSource = jsonExample)
			NodeParameter.builder()
				.name("jsonExample")
				.displayName("JSON Example")
				.description("An example JSON object. Top-level keys become the expected input fields.")
				.type(ParameterType.JSON)
				.defaultValue("{\n  \"name\": \"example\",\n  \"count\": 123,\n  \"active\": true\n}")
				.noDataExpression(true)
				.displayOptions(Map.of("show", Map.of("inputSource", List.of("jsonExample"))))
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		String inputSource = context.getParameter("inputSource", "passthrough");

		if (inputData == null || inputData.isEmpty()) {
			log.debug("Execute Workflow Trigger: no input data, producing empty trigger item");
			return NodeExecutionResult.success(List.of(createEmptyTriggerItem()));
		}

		// Passthrough mode: return all data unchanged
		if ("passthrough".equals(inputSource)) {
			log.debug("Execute Workflow Trigger: passthrough mode, {} items", inputData.size());
			return NodeExecutionResult.success(inputData);
		}

		// Parse declared fields based on input mode
		Set<String> declaredFields;
		if ("workflowInputs".equals(inputSource)) {
			declaredFields = parseWorkflowInputFields(context);
		} else if ("jsonExample".equals(inputSource)) {
			declaredFields = parseJsonExampleFields(context);
		} else {
			declaredFields = new LinkedHashSet<>();
		}

		// If no fields defined, fall back to passthrough
		if (declaredFields.isEmpty()) {
			log.debug("Execute Workflow Trigger: no fields defined, falling back to passthrough");
			return NodeExecutionResult.success(inputData);
		}

		// Filter each input item to only include declared fields
		List<Map<String, Object>> filteredItems = new ArrayList<>();
		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = unwrapJson(item);

			// Start with all declared fields set to null (default for missing)
			Map<String, Object> filtered = new LinkedHashMap<>();
			for (String field : declaredFields) {
				filtered.put(field, null);
			}

			// Overlay values from input that match declared fields
			for (String field : declaredFields) {
				if (json.containsKey(field)) {
					filtered.put(field, json.get(field));
				}
			}

			filteredItems.add(wrapInJson(filtered));
		}

		log.debug("Execute Workflow Trigger: filtered {} items to {} declared fields",
			filteredItems.size(), declaredFields.size());
		return NodeExecutionResult.success(filteredItems);
	}

	/**
	 * Parse field names from the workflowInputs FIXED_COLLECTION parameter.
	 * Expected structure: { "values": [ { "name": "fieldA", "type": "string" }, ... ] }
	 */
	@SuppressWarnings("unchecked")
	private Set<String> parseWorkflowInputFields(NodeExecutionContext context) {
		Set<String> fields = new LinkedHashSet<>();
		try {
			Object workflowInputs = context.getParameters().get("workflowInputs");
			if (workflowInputs instanceof Map) {
				Object values = ((Map<String, Object>) workflowInputs).get("values");
				if (values instanceof List) {
					for (Object entry : (List<?>) values) {
						if (entry instanceof Map) {
							String name = (String) ((Map<String, Object>) entry).get("name");
							if (name != null && !name.isBlank()) {
								fields.add(name.trim());
							}
						}
					}
				}
			}
		} catch (Exception e) {
			log.warn("Failed to parse workflow input fields: {}", e.getMessage());
		}
		return fields;
	}

	/**
	 * Parse field names from the jsonExample JSON parameter.
	 * Extracts top-level keys from the example JSON object.
	 */
	@SuppressWarnings("unchecked")
	private Set<String> parseJsonExampleFields(NodeExecutionContext context) {
		Set<String> fields = new LinkedHashSet<>();
		try {
			String jsonExample = context.getParameter("jsonExample", "{}");
			if (jsonExample != null && !jsonExample.isBlank()) {
				ObjectMapper mapper = new ObjectMapper();
				Map<String, Object> example = mapper.readValue(jsonExample,
					mapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
				fields.addAll(example.keySet());
			}
		} catch (Exception e) {
			log.warn("Failed to parse JSON example: {}", e.getMessage());
		}
		return fields;
	}
}
