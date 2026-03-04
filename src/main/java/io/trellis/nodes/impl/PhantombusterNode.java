package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Phantombuster — manage and launch web scraping/automation agents via the Phantombuster API.
 */
@Node(
		type = "phantombuster",
		displayName = "Phantombuster",
		description = "Manage and launch automation agents on Phantombuster",
		category = "Miscellaneous",
		icon = "phantombuster",
		credentials = {"phantombusterApi"},
		searchOnly = true
)
public class PhantombusterNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.phantombuster.com/api/v2";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = (String) credentials.getOrDefault("apiKey", "");

		String operation = context.getParameter("operation", "get");

		Map<String, String> headers = new HashMap<>();
		headers.put("X-Phantombuster-Key", apiKey);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "delete" -> {
						String agentId = context.getParameter("agentId", "");
						Map<String, Object> body = Map.of("id", agentId);
						HttpResponse<String> response = post(BASE_URL + "/agents/delete", body, headers);
						yield parseResponse(response);
					}
					case "get" -> {
						String agentId = context.getParameter("agentId", "");
						HttpResponse<String> response = get(BASE_URL + "/agents/fetch?id=" + encode(agentId), headers);
						yield parseResponse(response);
					}
					case "getOutput" -> {
						String agentId = context.getParameter("agentId", "");
						HttpResponse<String> response = get(BASE_URL + "/agents/fetch-output?id=" + encode(agentId), headers);
						yield parseResponse(response);
					}
					case "getAll" -> {
						HttpResponse<String> response = get(BASE_URL + "/agents/fetch-all", headers);
						yield parseResponse(response);
					}
					case "launch" -> {
						String agentId = context.getParameter("agentId", "");
						Map<String, Object> body = new LinkedHashMap<>();
						body.put("id", agentId);
						String arguments = context.getParameter("arguments", "");
						if (!arguments.isEmpty()) body.put("argument", parseJson(arguments));
						HttpResponse<String> response = post(BASE_URL + "/agents/launch", body, headers);
						yield parseResponse(response);
					}
					default -> throw new IllegalArgumentException("Unknown operation: " + operation);
				};
				results.add(wrapInJson(result));
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					return handleError(context, e.getMessage(), e);
				}
				throw new RuntimeException(e);
			}
		}

		return NodeExecutionResult.success(results);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("get")
						.options(List.of(
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get Output").value("getOutput").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Launch").value("launch").build()
						)).build(),
				NodeParameter.builder()
						.name("agentId").displayName("Agent ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The Phantombuster agent ID.").build(),
				NodeParameter.builder()
						.name("arguments").displayName("Arguments (JSON)")
						.type(ParameterType.STRING).defaultValue("")
						.description("JSON arguments to pass to the agent on launch.").build()
		);
	}
}
