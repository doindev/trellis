package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Travis CI — manage builds in Travis CI.
 */
@Node(
		type = "travisCi",
		displayName = "Travis CI",
		description = "Manage builds in Travis CI",
		category = "Development / DevOps",
		icon = "travisCi",
		credentials = {"travisCiApi"}
)
public class TravisCiNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.travis-ci.com";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiToken = (String) credentials.getOrDefault("apiToken", "");

		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "token " + apiToken);
		headers.put("Travis-API-Version", "3");
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "get" -> {
						String buildId = context.getParameter("buildId", "");
						String include = context.getParameter("include", "");
						String url = BASE_URL + "/build/" + buildId;
						if (!include.isEmpty()) url += "?include=" + encode(include);
						HttpResponse<String> response = get(url, headers);
						yield parseResponse(response);
					}
					case "getAll" -> {
						int limit = toInt(context.getParameters().get("limit"), 100);
						String sortBy = context.getParameter("sortBy", "");
						String order = context.getParameter("order", "");
						StringBuilder params = new StringBuilder();
						params.append("limit=").append(limit);
						if (!sortBy.isEmpty()) {
							params.append("&sort_by=").append(sortBy);
							if (!order.isEmpty()) params.append(":").append(order);
						}
						HttpResponse<String> response = get(BASE_URL + "/builds?" + params, headers);
						yield parseResponse(response);
					}
					case "cancel" -> {
						String buildId = context.getParameter("buildId", "");
						HttpResponse<String> response = post(BASE_URL + "/build/" + buildId + "/cancel", Map.of(), headers);
						yield parseResponse(response);
					}
					case "restart" -> {
						String buildId = context.getParameter("buildId", "");
						HttpResponse<String> response = post(BASE_URL + "/build/" + buildId + "/restart", Map.of(), headers);
						yield parseResponse(response);
					}
					case "trigger" -> {
						String slug = context.getParameter("slug", "");
						String branch = context.getParameter("branch", "");
						String message = context.getParameter("message", "");
						String mergeMode = context.getParameter("mergeMode", "");

						Map<String, Object> request = new LinkedHashMap<>();
						request.put("branch", branch);
						if (!message.isEmpty()) request.put("message", message);
						if (!mergeMode.isEmpty()) request.put("merge_mode", mergeMode);

						Map<String, Object> body = Map.of("request", request);
						HttpResponse<String> response = post(BASE_URL + "/repo/" + encode(slug) + "/requests", body, headers);
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
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Cancel").value("cancel").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Restart").value("restart").build(),
								ParameterOption.builder().name("Trigger").value("trigger").build()
						)).build(),
				NodeParameter.builder()
						.name("buildId").displayName("Build ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("slug").displayName("Repository Slug")
						.type(ParameterType.STRING).defaultValue("")
						.description("Slug in owner/repo format.").build(),
				NodeParameter.builder()
						.name("branch").displayName("Branch")
						.type(ParameterType.STRING).defaultValue("")
						.description("Branch to trigger build for.").build(),
				NodeParameter.builder()
						.name("message").displayName("Message")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("mergeMode").displayName("Merge Mode")
						.type(ParameterType.OPTIONS).defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("None").value("").build(),
								ParameterOption.builder().name("Deep Merge").value("deep_merge").build(),
								ParameterOption.builder().name("Deep Merge Append").value("deep_merge_append").build(),
								ParameterOption.builder().name("Deep Merge Prepend").value("deep_merge_prepend").build(),
								ParameterOption.builder().name("Merge").value("merge").build(),
								ParameterOption.builder().name("Replace").value("replace").build()
						)).build(),
				NodeParameter.builder()
						.name("include").displayName("Include")
						.type(ParameterType.STRING).defaultValue("")
						.description("Eager-load related attributes.").build(),
				NodeParameter.builder()
						.name("sortBy").displayName("Sort By")
						.type(ParameterType.OPTIONS).defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("None").value("").build(),
								ParameterOption.builder().name("Created At").value("created_at").build(),
								ParameterOption.builder().name("Finished At").value("finished_at").build(),
								ParameterOption.builder().name("ID").value("id").build(),
								ParameterOption.builder().name("Number").value("number").build(),
								ParameterOption.builder().name("Started At").value("started_at").build()
						)).build(),
				NodeParameter.builder()
						.name("order").displayName("Order")
						.type(ParameterType.OPTIONS).defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("None").value("").build(),
								ParameterOption.builder().name("Ascending").value("asc").build(),
								ParameterOption.builder().name("Descending").value("desc").build()
						)).build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max builds to return (1-500).").build()
		);
	}
}
