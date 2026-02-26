package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Google Tasks — manage tasks and task lists using the Google Tasks API.
 */
@Node(
		type = "googleTasks",
		displayName = "Google Tasks",
		description = "Manage tasks in Google Tasks",
		category = "Project Management",
		icon = "googleTasks",
		credentials = {"googleTasksOAuth2Api"}
)
public class GoogleTasksNode extends AbstractApiNode {

	private static final String BASE_URL = "https://www.googleapis.com/tasks/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");

		String operation = context.getParameter("operation", "getAll");
		String taskListId = context.getParameter("taskListId", "@default");

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
						Map<String, Object> body = new LinkedHashMap<>();
						body.put("title", context.getParameter("title", ""));
						String notes = context.getParameter("notes", "");
						if (!notes.isEmpty()) body.put("notes", notes);
						String dueDate = context.getParameter("dueDate", "");
						if (!dueDate.isEmpty()) body.put("due", dueDate);
						String status = context.getParameter("status", "");
						if (!status.isEmpty()) body.put("status", status);
						HttpResponse<String> response = post(BASE_URL + "/lists/" + encode(taskListId) + "/tasks", body, headers);
						yield parseResponse(response);
					}
					case "delete" -> {
						String taskId = context.getParameter("taskId", "");
						HttpResponse<String> response = delete(BASE_URL + "/lists/" + encode(taskListId) + "/tasks/" + encode(taskId), headers);
						yield parseResponse(response);
					}
					case "get" -> {
						String taskId = context.getParameter("taskId", "");
						HttpResponse<String> response = get(BASE_URL + "/lists/" + encode(taskListId) + "/tasks/" + encode(taskId), headers);
						yield parseResponse(response);
					}
					case "getAll" -> {
						int limit = toInt(context.getParameters().get("limit"), 100);
						String url = BASE_URL + "/lists/" + encode(taskListId) + "/tasks?maxResults=" + limit;
						boolean showCompleted = toBoolean(context.getParameters().get("showCompleted"), true);
						url += "&showCompleted=" + showCompleted;
						HttpResponse<String> response = get(url, headers);
						yield parseResponse(response);
					}
					case "update" -> {
						String taskId = context.getParameter("taskId", "");
						Map<String, Object> body = new LinkedHashMap<>();
						String title = context.getParameter("title", "");
						if (!title.isEmpty()) body.put("title", title);
						String notes = context.getParameter("notes", "");
						if (!notes.isEmpty()) body.put("notes", notes);
						String dueDate = context.getParameter("dueDate", "");
						if (!dueDate.isEmpty()) body.put("due", dueDate);
						String status = context.getParameter("status", "");
						if (!status.isEmpty()) body.put("status", status);
						HttpResponse<String> response = patch(BASE_URL + "/lists/" + encode(taskListId) + "/tasks/" + encode(taskId), body, headers);
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
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("taskListId").displayName("Task List ID")
						.type(ParameterType.STRING).defaultValue("@default")
						.description("Task list ID (@default for primary list).").build(),
				NodeParameter.builder()
						.name("taskId").displayName("Task ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("notes").displayName("Notes")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("dueDate").displayName("Due Date")
						.type(ParameterType.STRING).defaultValue("")
						.description("Due date in RFC 3339 format.").build(),
				NodeParameter.builder()
						.name("status").displayName("Status")
						.type(ParameterType.OPTIONS).defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("Needs Action").value("needsAction").build(),
								ParameterOption.builder().name("Completed").value("completed").build()
						)).build(),
				NodeParameter.builder()
						.name("showCompleted").displayName("Show Completed")
						.type(ParameterType.BOOLEAN).defaultValue(true).build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max tasks to return.").build()
		);
	}
}
