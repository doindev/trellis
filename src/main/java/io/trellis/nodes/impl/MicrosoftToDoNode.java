package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Microsoft To Do — manage tasks and lists using the Microsoft Graph API.
 */
@Node(
		type = "microsoftToDo",
		displayName = "Microsoft To Do",
		description = "Manage tasks and lists in Microsoft To Do",
		category = "Project Management",
		icon = "microsoftToDo",
		credentials = {"microsoftToDoOAuth2Api"}
)
public class MicrosoftToDoNode extends AbstractApiNode {

	private static final String BASE_URL = "https://graph.microsoft.com/v1.0/me";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");

		String resource = context.getParameter("resource", "task");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "task" -> handleTask(context, headers, operation);
					case "list" -> handleList(context, headers, operation);
					default -> throw new IllegalArgumentException("Unknown resource: " + resource);
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

	private Map<String, Object> handleTask(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		String taskListId = context.getParameter("taskListId", "");
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", context.getParameter("title", ""));
				String content = context.getParameter("content", "");
				if (!content.isEmpty()) body.put("body", Map.of("content", content, "contentType", "text"));
				String dueDateTime = context.getParameter("dueDateTime", "");
				if (!dueDateTime.isEmpty()) body.put("dueDateTime", Map.of("dateTime", dueDateTime, "timeZone", "UTC"));
				HttpResponse<String> response = post(BASE_URL + "/todo/lists/" + encode(taskListId) + "/tasks", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String taskId = context.getParameter("taskId", "");
				HttpResponse<String> response = delete(BASE_URL + "/todo/lists/" + encode(taskListId) + "/tasks/" + encode(taskId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String taskId = context.getParameter("taskId", "");
				HttpResponse<String> response = get(BASE_URL + "/todo/lists/" + encode(taskListId) + "/tasks/" + encode(taskId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				HttpResponse<String> response = get(BASE_URL + "/todo/lists/" + encode(taskListId) + "/tasks?$top=" + limit, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String taskId = context.getParameter("taskId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String title = context.getParameter("title", "");
				if (!title.isEmpty()) body.put("title", title);
				String content = context.getParameter("content", "");
				if (!content.isEmpty()) body.put("body", Map.of("content", content, "contentType", "text"));
				String dueDateTime = context.getParameter("dueDateTime", "");
				if (!dueDateTime.isEmpty()) body.put("dueDateTime", Map.of("dateTime", dueDateTime, "timeZone", "UTC"));
				HttpResponse<String> response = patch(BASE_URL + "/todo/lists/" + encode(taskListId) + "/tasks/" + encode(taskId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown task operation: " + operation);
		};
	}

	private Map<String, Object> handleList(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = Map.of("displayName", context.getParameter("displayName", ""));
				HttpResponse<String> response = post(BASE_URL + "/todo/lists", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String listId = context.getParameter("listId", "");
				HttpResponse<String> response = delete(BASE_URL + "/todo/lists/" + encode(listId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String listId = context.getParameter("listId", "");
				HttpResponse<String> response = get(BASE_URL + "/todo/lists/" + encode(listId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				HttpResponse<String> response = get(BASE_URL + "/todo/lists?$top=" + limit, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String listId = context.getParameter("listId", "");
				Map<String, Object> body = Map.of("displayName", context.getParameter("displayName", ""));
				HttpResponse<String> response = patch(BASE_URL + "/todo/lists/" + encode(listId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown list operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("task")
						.options(List.of(
								ParameterOption.builder().name("Task").value("task").build(),
								ParameterOption.builder().name("List").value("list").build()
						)).build(),
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
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("taskId").displayName("Task ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("listId").displayName("List ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("displayName").displayName("Display Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name for the task list.").build(),
				NodeParameter.builder()
						.name("content").displayName("Content")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("dueDateTime").displayName("Due Date Time")
						.type(ParameterType.STRING).defaultValue("")
						.description("Due date in ISO 8601 format.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max results to return.").build()
		);
	}
}
