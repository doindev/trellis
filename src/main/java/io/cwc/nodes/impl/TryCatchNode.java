package io.cwc.nodes.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * Try/Catch Node - routes items based on whether they contain errors.
 * Items with an 'error' field (from upstream continueOnFail) go to the error output,
 * clean items go to the success output.
 */
@Slf4j
@Node(
	type = "tryCatch",
	displayName = "Try / Catch",
	description = "Route items based on errors. Clean items go to 'success', items with errors go to 'error' output.",
	category = "Flow",
	icon = "umbrella",
	implementationNotes = "Output index 0 is 'success' (clean items), output index 1 is 'error' (items with errors). " +
		"Connect the nodes you want to protect AFTER the success output. The error output receives items that " +
		"threw exceptions, with error details attached. Useful for wrapping HTTP calls or database operations."
)
public class TryCatchNode extends AbstractNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
			NodeOutput.builder().name("success").displayName("success").build(),
			NodeOutput.builder().name("error").displayName("error").build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("errorOutput")
				.displayName("Error Output Format")
				.description("What to include in the error output items.")
				.type(ParameterType.OPTIONS)
				.defaultValue("fullError")
				.options(List.of(
					ParameterOption.builder()
						.name("Error Message Only")
						.value("errorMessage")
						.description("Only include the error message string")
						.build(),
					ParameterOption.builder()
						.name("Full Error Details")
						.value("fullError")
						.description("Include all item data plus the error details")
						.build()
				))
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.successMultiOutput(List.of(List.of(), List.of()));
		}

		String errorOutput = context.getParameter("errorOutput", "fullError");

		List<Map<String, Object>> successItems = new ArrayList<>();
		List<Map<String, Object>> errorItems = new ArrayList<>();

		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = unwrapJson(item);

			if (json.containsKey("error")) {
				if ("errorMessage".equals(errorOutput)) {
					Map<String, Object> errorOnly = new LinkedHashMap<>();
					errorOnly.put("error", json.get("error"));
					errorOnly.put("message", json.getOrDefault("message", json.get("error")));
					errorItems.add(wrapInJson(errorOnly));
				} else {
					errorItems.add(item);
				}
			} else {
				successItems.add(item);
			}
		}

		log.debug("TryCatch: {} items -> {} success, {} error",
			inputData.size(), successItems.size(), errorItems.size());
		return NodeExecutionResult.successMultiOutput(List.of(successItems, errorItems));
	}
}
