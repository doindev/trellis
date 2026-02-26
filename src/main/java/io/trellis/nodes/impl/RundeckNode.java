package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Rundeck — execute jobs and retrieve metadata from Rundeck.
 */
@Node(
		type = "rundeck",
		displayName = "Rundeck",
		description = "Execute jobs and get metadata from Rundeck",
		category = "Development / DevOps",
		icon = "rundeck",
		credentials = {"rundeckApi"}
)
public class RundeckNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String baseUrl = (String) credentials.getOrDefault("url", "");
		if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		String token = (String) credentials.getOrDefault("token", "");

		String operation = context.getParameter("operation", "execute");

		Map<String, String> headers = new HashMap<>();
		headers.put("X-Rundeck-Auth-Token", token);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "execute" -> {
						String jobId = context.getParameter("jobId", "");
						Map<String, Object> body = new LinkedHashMap<>();

						// Build argument string from parameters
						String arguments = context.getParameter("arguments", "");
						if (!arguments.isEmpty()) {
							body.put("argString", arguments);
						}

						String filter = context.getParameter("filter", "");
						if (!filter.isEmpty()) {
							body.put("filter", filter);
						}

						HttpResponse<String> response = post(baseUrl + "/api/14/job/" + jobId + "/run", body, headers);
						yield parseResponse(response);
					}
					case "getMetadata" -> {
						String jobId = context.getParameter("jobId", "");
						HttpResponse<String> response = get(baseUrl + "/api/18/job/" + jobId + "/info", headers);
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
						.type(ParameterType.OPTIONS).defaultValue("execute")
						.options(List.of(
								ParameterOption.builder().name("Execute").value("execute").build(),
								ParameterOption.builder().name("Get Metadata").value("getMetadata").build()
						)).build(),
				NodeParameter.builder()
						.name("jobId").displayName("Job ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("ID of the Rundeck job.").build(),
				NodeParameter.builder()
						.name("arguments").displayName("Arguments")
						.type(ParameterType.STRING).defaultValue("")
						.description("Arguments string for job execution (e.g., -arg1 val1 -arg2 val2).").build(),
				NodeParameter.builder()
						.name("filter").displayName("Node Filter")
						.type(ParameterType.STRING).defaultValue("")
						.description("Filter Rundeck nodes by name.").build()
		);
	}
}
