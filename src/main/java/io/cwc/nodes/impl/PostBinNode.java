package io.cwc.nodes.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * PostBin — create and manage request bins via the PostBin API.
 * No authentication required.
 */
@Node(
		type = "postBin",
		displayName = "PostBin",
		description = "Create and manage request bins via PostBin",
		category = "Miscellaneous",
		icon = "inbox",
		searchOnly = true
)
public class PostBinNode extends AbstractApiNode {

	private static final String BASE_URL = "https://www.toptal.com/developers/postbin/api/bin";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String operation = context.getParameter("operation", "create");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					Map<String, Object> result;
					switch (operation) {
						case "create" -> {
							var response = post(BASE_URL, Map.of(),
									Map.of("Content-Type", "application/json"));
							result = parseResponse(response);
						}
						case "get" -> {
							String binId = context.getParameter("binId", "");
							var response = get(BASE_URL + "/" + encode(binId) + "/req/shift", Map.of());
							result = parseResponse(response);
						}
						default -> result = Map.of("error", "Unknown operation: " + operation);
					}
					results.add(wrapInJson(result));
				} catch (Exception e) {
					if (context.isContinueOnFail()) {
						results.add(wrapInJson(Map.of("error", e.getMessage())));
					} else {
						return handleError(context, e.getMessage(), e);
					}
				}
			}
			return NodeExecutionResult.success(results);
		} catch (Exception e) {
			return handleError(context, e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("create")
						.options(List.of(
								ParameterOption.builder().name("Create Bin").value("create").build(),
								ParameterOption.builder().name("Get Request").value("get").build()
						)).build(),
				NodeParameter.builder()
						.name("binId").displayName("Bin ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("PostBin bin ID.").build()
		);
	}
}
