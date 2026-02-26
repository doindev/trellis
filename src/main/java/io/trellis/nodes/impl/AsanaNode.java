package io.trellis.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Asana Node -- manage projects, sections, tasks, comments, tags, and users in Asana.
 */
@Slf4j
@Node(
	type = "asana",
	displayName = "Asana",
	description = "Manage projects, sections, tasks, comments, tags, and users in Asana",
	category = "Project Management",
	icon = "asana",
	credentials = {"asanaApi"}
)
public class AsanaNode extends AbstractApiNode {

	private static final String BASE_URL = "https://app.asana.com/api/1.0";

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		// Resource selector
		params.add(NodeParameter.builder()
			.name("resource").displayName("Resource")
			.type(ParameterType.OPTIONS).required(true).defaultValue("task")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Project").value("project").description("Manage projects").build(),
				ParameterOption.builder().name("Section").value("section").description("Manage sections").build(),
				ParameterOption.builder().name("Task").value("task").description("Manage tasks").build(),
				ParameterOption.builder().name("Task Comment").value("taskComment").description("Manage task comments").build(),
				ParameterOption.builder().name("Task Tag").value("taskTag").description("Manage task tags").build(),
				ParameterOption.builder().name("User").value("user").description("Manage users").build()
			)).build());

		// Project operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("project"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a project").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a project").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a project").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many projects").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a project").build()
			)).build());

		// Section operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("section"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a section in a project").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all sections in a project").build()
			)).build());

		// Task operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"))))
			.options(List.of(
				ParameterOption.builder().name("Add Subtask").value("addSubtask").description("Add a subtask to a task").build(),
				ParameterOption.builder().name("Create").value("create").description("Create a task").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a task").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a task").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many tasks").build(),
				ParameterOption.builder().name("Move").value("move").description("Move a task to a section").build(),
				ParameterOption.builder().name("Search").value("search").description("Search tasks in a workspace").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a task").build()
			)).build());

		// Task Comment operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("add")
			.displayOptions(Map.of("show", Map.of("resource", List.of("taskComment"))))
			.options(List.of(
				ParameterOption.builder().name("Add").value("add").description("Add a comment to a task").build(),
				ParameterOption.builder().name("Remove").value("remove").description("Remove a comment (story) from a task").build()
			)).build());

		// Task Tag operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("add")
			.displayOptions(Map.of("show", Map.of("resource", List.of("taskTag"))))
			.options(List.of(
				ParameterOption.builder().name("Add").value("add").description("Add a tag to a task").build(),
				ParameterOption.builder().name("Remove").value("remove").description("Remove a tag from a task").build()
			)).build());

		// User operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get a user").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many users in a workspace").build()
			)).build());

		// Common parameters
		params.add(NodeParameter.builder()
			.name("workspaceId").displayName("Workspace ID")
			.type(ParameterType.STRING).required(true)
			.description("The GID of the workspace.")
			.build());

		params.add(NodeParameter.builder()
			.name("projectId").displayName("Project ID")
			.type(ParameterType.STRING)
			.description("The GID of the project.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("project", "section", "task"))))
			.build());

		params.add(NodeParameter.builder()
			.name("taskId").displayName("Task ID")
			.type(ParameterType.STRING)
			.description("The GID of the task.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("task", "taskComment", "taskTag"))))
			.build());

		params.add(NodeParameter.builder()
			.name("sectionId").displayName("Section ID")
			.type(ParameterType.STRING)
			.description("The GID of the section.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("move"))))
			.build());

		params.add(NodeParameter.builder()
			.name("userId").displayName("User ID")
			.type(ParameterType.STRING)
			.description("The GID of the user, or 'me' for current user.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("get"))))
			.build());

		params.add(NodeParameter.builder()
			.name("tagId").displayName("Tag ID")
			.type(ParameterType.STRING)
			.description("The GID of the tag.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("taskTag"))))
			.build());

		params.add(NodeParameter.builder()
			.name("storyId").displayName("Story/Comment ID")
			.type(ParameterType.STRING)
			.description("The GID of the story (comment) to remove.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("taskComment"), "operation", List.of("remove"))))
			.build());

		params.add(NodeParameter.builder()
			.name("name").displayName("Name")
			.type(ParameterType.STRING)
			.description("Name of the resource.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("notes").displayName("Notes / Description")
			.type(ParameterType.STRING)
			.description("Notes or description for the resource.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("project", "task"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("commentText").displayName("Comment Text")
			.type(ParameterType.STRING)
			.description("The text of the comment.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("taskComment"), "operation", List.of("add"))))
			.build());

		params.add(NodeParameter.builder()
			.name("searchText").displayName("Search Text")
			.type(ParameterType.STRING)
			.description("Text to search for in task names.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("search"))))
			.build());

		params.add(NodeParameter.builder()
			.name("assignee").displayName("Assignee")
			.type(ParameterType.STRING)
			.description("The GID of the user to assign or 'me'.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("dueOn").displayName("Due Date")
			.type(ParameterType.STRING)
			.description("Due date in YYYY-MM-DD format.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("additionalFields").displayName("Additional Fields (JSON)")
			.type(ParameterType.JSON).defaultValue("{}")
			.description("Additional fields as JSON.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create", "update"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "task");
		String operation = context.getParameter("operation", "getAll");
		Map<String, Object> credentials = context.getCredentials();
		String token = String.valueOf(credentials.getOrDefault("accessToken",
				credentials.getOrDefault("apiKey", "")));

		try {
			Map<String, String> headers = authHeaders(token);
			return switch (resource) {
				case "project" -> executeProject(context, operation, headers);
				case "section" -> executeSection(context, operation, headers);
				case "task" -> executeTask(context, operation, headers);
				case "taskComment" -> executeTaskComment(context, operation, headers);
				case "taskTag" -> executeTaskTag(context, operation, headers);
				case "user" -> executeUser(context, operation, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Asana API error: " + e.getMessage(), e);
		}
	}

	// ========================= Project Operations =========================

	private NodeExecutionResult executeProject(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String workspaceId = context.getParameter("workspaceId", "");
				String name = context.getParameter("name", "");
				String notes = context.getParameter("notes", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> data = new LinkedHashMap<>(parseJson(additionalJson));
				data.put("workspace", workspaceId);
				if (!name.isEmpty()) data.put("name", name);
				if (!notes.isEmpty()) data.put("notes", notes);
				Map<String, Object> body = Map.of("data", data);
				HttpResponse<String> response = post(BASE_URL + "/projects", body, headers);
				return toResult(response);
			}
			case "delete": {
				String projectId = context.getParameter("projectId", "");
				HttpResponse<String> response = delete(BASE_URL + "/projects/" + encode(projectId), headers);
				return toDeleteResult(response, projectId);
			}
			case "get": {
				String projectId = context.getParameter("projectId", "");
				HttpResponse<String> response = get(BASE_URL + "/projects/" + encode(projectId), headers);
				return toResult(response);
			}
			case "getAll": {
				String workspaceId = context.getParameter("workspaceId", "");
				HttpResponse<String> response = get(BASE_URL + "/projects?workspace=" + encode(workspaceId), headers);
				return toArrayResult(response, "data");
			}
			case "update": {
				String projectId = context.getParameter("projectId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> data = new LinkedHashMap<>(parseJson(additionalJson));
				putIfNotEmpty(data, "name", context.getParameter("name", ""));
				putIfNotEmpty(data, "notes", context.getParameter("notes", ""));
				Map<String, Object> body = Map.of("data", data);
				HttpResponse<String> response = put(BASE_URL + "/projects/" + encode(projectId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown project operation: " + operation);
		}
	}

	// ========================= Section Operations =========================

	private NodeExecutionResult executeSection(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String projectId = context.getParameter("projectId", "");
				String name = context.getParameter("name", "");
				Map<String, Object> data = new LinkedHashMap<>();
				data.put("name", name);
				Map<String, Object> body = Map.of("data", data);
				HttpResponse<String> response = post(BASE_URL + "/projects/" + encode(projectId) + "/sections", body, headers);
				return toResult(response);
			}
			case "getAll": {
				String projectId = context.getParameter("projectId", "");
				HttpResponse<String> response = get(BASE_URL + "/projects/" + encode(projectId) + "/sections", headers);
				return toArrayResult(response, "data");
			}
			default:
				return NodeExecutionResult.error("Unknown section operation: " + operation);
		}
	}

	// ========================= Task Operations =========================

	private NodeExecutionResult executeTask(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "addSubtask": {
				String taskId = context.getParameter("taskId", "");
				String name = context.getParameter("name", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> data = new LinkedHashMap<>(parseJson(additionalJson));
				data.put("name", name);
				Map<String, Object> body = Map.of("data", data);
				HttpResponse<String> response = post(BASE_URL + "/tasks/" + encode(taskId) + "/subtasks", body, headers);
				return toResult(response);
			}
			case "create": {
				String workspaceId = context.getParameter("workspaceId", "");
				String projectId = context.getParameter("projectId", "");
				String name = context.getParameter("name", "");
				String notes = context.getParameter("notes", "");
				String assignee = context.getParameter("assignee", "");
				String dueOn = context.getParameter("dueOn", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> data = new LinkedHashMap<>(parseJson(additionalJson));
				data.put("workspace", workspaceId);
				if (!name.isEmpty()) data.put("name", name);
				if (!notes.isEmpty()) data.put("notes", notes);
				if (!assignee.isEmpty()) data.put("assignee", assignee);
				if (!dueOn.isEmpty()) data.put("due_on", dueOn);
				if (!projectId.isEmpty()) data.put("projects", List.of(projectId));
				Map<String, Object> body = Map.of("data", data);
				HttpResponse<String> response = post(BASE_URL + "/tasks", body, headers);
				return toResult(response);
			}
			case "delete": {
				String taskId = context.getParameter("taskId", "");
				HttpResponse<String> response = delete(BASE_URL + "/tasks/" + encode(taskId), headers);
				return toDeleteResult(response, taskId);
			}
			case "get": {
				String taskId = context.getParameter("taskId", "");
				HttpResponse<String> response = get(BASE_URL + "/tasks/" + encode(taskId), headers);
				return toResult(response);
			}
			case "getAll": {
				String projectId = context.getParameter("projectId", "");
				HttpResponse<String> response = get(BASE_URL + "/tasks?project=" + encode(projectId), headers);
				return toArrayResult(response, "data");
			}
			case "move": {
				String taskId = context.getParameter("taskId", "");
				String sectionId = context.getParameter("sectionId", "");
				Map<String, Object> data = Map.of("task", taskId);
				Map<String, Object> body = Map.of("data", data);
				HttpResponse<String> response = post(BASE_URL + "/sections/" + encode(sectionId) + "/addTask", body, headers);
				return toResult(response);
			}
			case "search": {
				String workspaceId = context.getParameter("workspaceId", "");
				String searchText = context.getParameter("searchText", "");
				String url = BASE_URL + "/workspaces/" + encode(workspaceId) + "/tasks/search?text=" + encode(searchText);
				HttpResponse<String> response = get(url, headers);
				return toArrayResult(response, "data");
			}
			case "update": {
				String taskId = context.getParameter("taskId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> data = new LinkedHashMap<>(parseJson(additionalJson));
				putIfNotEmpty(data, "name", context.getParameter("name", ""));
				putIfNotEmpty(data, "notes", context.getParameter("notes", ""));
				putIfNotEmpty(data, "assignee", context.getParameter("assignee", ""));
				putIfNotEmpty(data, "due_on", context.getParameter("dueOn", ""));
				Map<String, Object> body = Map.of("data", data);
				HttpResponse<String> response = put(BASE_URL + "/tasks/" + encode(taskId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown task operation: " + operation);
		}
	}

	// ========================= Task Comment Operations =========================

	private NodeExecutionResult executeTaskComment(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "add": {
				String taskId = context.getParameter("taskId", "");
				String text = context.getParameter("commentText", "");
				Map<String, Object> data = Map.of("text", text);
				Map<String, Object> body = Map.of("data", data);
				HttpResponse<String> response = post(BASE_URL + "/tasks/" + encode(taskId) + "/stories", body, headers);
				return toResult(response);
			}
			case "remove": {
				String storyId = context.getParameter("storyId", "");
				HttpResponse<String> response = delete(BASE_URL + "/stories/" + encode(storyId), headers);
				return toDeleteResult(response, storyId);
			}
			default:
				return NodeExecutionResult.error("Unknown taskComment operation: " + operation);
		}
	}

	// ========================= Task Tag Operations =========================

	private NodeExecutionResult executeTaskTag(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String taskId = context.getParameter("taskId", "");
		String tagId = context.getParameter("tagId", "");
		switch (operation) {
			case "add": {
				Map<String, Object> data = Map.of("tag", tagId);
				Map<String, Object> body = Map.of("data", data);
				HttpResponse<String> response = post(BASE_URL + "/tasks/" + encode(taskId) + "/addTag", body, headers);
				return toResult(response);
			}
			case "remove": {
				Map<String, Object> data = Map.of("tag", tagId);
				Map<String, Object> body = Map.of("data", data);
				HttpResponse<String> response = post(BASE_URL + "/tasks/" + encode(taskId) + "/removeTag", body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown taskTag operation: " + operation);
		}
	}

	// ========================= User Operations =========================

	private NodeExecutionResult executeUser(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "get": {
				String userId = context.getParameter("userId", "me");
				HttpResponse<String> response = get(BASE_URL + "/users/" + encode(userId), headers);
				return toResult(response);
			}
			case "getAll": {
				String workspaceId = context.getParameter("workspaceId", "");
				HttpResponse<String> response = get(BASE_URL + "/users?workspace=" + encode(workspaceId), headers);
				return toArrayResult(response, "data");
			}
			default:
				return NodeExecutionResult.error("Unknown user operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> authHeaders(String token) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + token);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}

	private void putIfNotEmpty(Map<String, Object> map, String key, String value) {
		if (value != null && !value.isEmpty()) {
			map.put(key, value);
		}
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return asanaError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		Object data = parsed.get("data");
		if (data instanceof Map) {
			return NodeExecutionResult.success(List.of(wrapInJson(data)));
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult toArrayResult(HttpResponse<String> response, String key) throws Exception {
		if (response.statusCode() >= 400) {
			return asanaError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		Object data = parsed.get(key);
		if (data instanceof List) {
			List<Map<String, Object>> results = new ArrayList<>();
			for (Object item : (List<?>) data) {
				if (item instanceof Map) {
					results.add(wrapInJson(item));
				}
			}
			return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response, String id) throws Exception {
		if (response.statusCode() >= 400) {
			return asanaError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "id", id))));
	}

	private NodeExecutionResult asanaError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Asana API error (HTTP " + response.statusCode() + "): " + body);
	}
}
