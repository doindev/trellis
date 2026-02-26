package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Flow — manage tasks in Flow project management.
 */
@Node(
		type = "flow",
		displayName = "Flow",
		description = "Manage tasks in Flow",
		category = "Miscellaneous",
		icon = "flow",
		credentials = {"flowApi"}
)
public class FlowNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.getflow.com/v2";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		String organizationId = (String) credentials.getOrDefault("organizationId", "");

		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "create" -> {
						Map<String, Object> task = new LinkedHashMap<>();
						task.put("name", context.getParameter("name", ""));
						String workspaceId = context.getParameter("workspaceId", "");
						task.put("workspace_id", workspaceId);
						String ownerId = context.getParameter("ownerId", "");
						if (!ownerId.isEmpty()) task.put("owner_id", ownerId);
						String listId = context.getParameter("listId", "");
						if (!listId.isEmpty()) task.put("list_id", listId);
						Map<String, Object> body = Map.of("task", task);
						HttpResponse<String> response = post(BASE_URL + "/tasks?organization_id=" + encode(organizationId), body, headers);
						yield parseResponse(response);
					}
					case "update" -> {
						String taskId = context.getParameter("taskId", "");
						Map<String, Object> task = new LinkedHashMap<>();
						String name = context.getParameter("name", "");
						if (!name.isEmpty()) task.put("name", name);
						boolean completed = toBoolean(context.getParameters().get("completed"), false);
						if (completed) task.put("completed", true);
						Map<String, Object> body = Map.of("task", task);
						HttpResponse<String> response = put(BASE_URL + "/tasks/" + encode(taskId) + "?organization_id=" + encode(organizationId), body, headers);
						yield parseResponse(response);
					}
					case "get" -> {
						String taskId = context.getParameter("taskId", "");
						HttpResponse<String> response = get(BASE_URL + "/tasks/" + encode(taskId) + "?organization_id=" + encode(organizationId), headers);
						yield parseResponse(response);
					}
					case "getAll" -> {
						int limit = toInt(context.getParameters().get("limit"), 100);
						HttpResponse<String> response = get(BASE_URL + "/tasks?organization_id=" + encode(organizationId) + "&limit=" + limit, headers);
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
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("taskId").displayName("Task ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("name").displayName("Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("workspaceId").displayName("Workspace ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("ownerId").displayName("Owner ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("listId").displayName("List ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("completed").displayName("Completed")
						.type(ParameterType.BOOLEAN).defaultValue(false).build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max tasks to return.").build()
		);
	}
}
