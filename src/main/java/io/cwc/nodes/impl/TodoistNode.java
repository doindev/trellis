package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Todoist — manage tasks, projects, sections, comments, and labels using the Todoist API.
 */
@Node(
		type = "todoist",
		displayName = "Todoist",
		description = "Manage tasks, projects, sections, and more with Todoist",
		category = "Project Management",
		icon = "todoist",
		credentials = {"todoistApi"}
)
public class TodoistNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.todoist.com/api/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");

		String resource = context.getParameter("resource", "task");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + apiKey);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "task" -> handleTask(context, headers, operation);
					case "project" -> handleProject(context, headers, operation);
					case "section" -> handleSection(context, headers, operation);
					case "comment" -> handleComment(context, headers, operation);
					case "label" -> handleLabel(context, headers, operation);
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
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("content", context.getParameter("content", ""));
				String projectId = context.getParameter("projectId", "");
				if (!projectId.isEmpty()) body.put("project_id", projectId);
				String sectionId = context.getParameter("sectionId", "");
				if (!sectionId.isEmpty()) body.put("section_id", sectionId);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) body.put("description", description);
				String labels = context.getParameter("labels", "");
				if (!labels.isEmpty()) body.put("labels", List.of(labels.split(",")));
				int priority = toInt(context.getParameters().get("priority"), 1);
				if (priority > 1) body.put("priority", priority);
				String dueString = context.getParameter("dueString", "");
				if (!dueString.isEmpty()) body.put("due_string", dueString);
				String dueDate = context.getParameter("dueDate", "");
				if (!dueDate.isEmpty()) body.put("due_date", dueDate);
				String assigneeId = context.getParameter("assigneeId", "");
				if (!assigneeId.isEmpty()) body.put("assignee_id", assigneeId);
				HttpResponse<String> response = post(BASE_URL + "/tasks", body, headers);
				yield parseResponse(response);
			}
			case "close" -> {
				String taskId = context.getParameter("taskId", "");
				HttpResponse<String> response = post(BASE_URL + "/tasks/" + encode(taskId) + "/close", Map.of(), headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String taskId = context.getParameter("taskId", "");
				HttpResponse<String> response = delete(BASE_URL + "/tasks/" + encode(taskId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String taskId = context.getParameter("taskId", "");
				HttpResponse<String> response = get(BASE_URL + "/tasks/" + encode(taskId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				StringBuilder url = new StringBuilder(BASE_URL + "/tasks");
				String projectId = context.getParameter("projectId", "");
				if (!projectId.isEmpty()) url.append("?project_id=").append(encode(projectId));
				HttpResponse<String> response = get(url.toString(), headers);
				yield parseResponse(response);
			}
			case "reopen" -> {
				String taskId = context.getParameter("taskId", "");
				HttpResponse<String> response = post(BASE_URL + "/tasks/" + encode(taskId) + "/reopen", Map.of(), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String taskId = context.getParameter("taskId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String content = context.getParameter("content", "");
				if (!content.isEmpty()) body.put("content", content);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) body.put("description", description);
				String labels = context.getParameter("labels", "");
				if (!labels.isEmpty()) body.put("labels", List.of(labels.split(",")));
				int priority = toInt(context.getParameters().get("priority"), 0);
				if (priority > 0) body.put("priority", priority);
				String dueString = context.getParameter("dueString", "");
				if (!dueString.isEmpty()) body.put("due_string", dueString);
				String dueDate = context.getParameter("dueDate", "");
				if (!dueDate.isEmpty()) body.put("due_date", dueDate);
				HttpResponse<String> response = post(BASE_URL + "/tasks/" + encode(taskId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown task operation: " + operation);
		};
	}

	private Map<String, Object> handleProject(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", context.getParameter("projectName", ""));
				String color = context.getParameter("color", "");
				if (!color.isEmpty()) body.put("color", color);
				boolean isFavorite = toBoolean(context.getParameters().get("isFavorite"), false);
				if (isFavorite) body.put("is_favorite", true);
				HttpResponse<String> response = post(BASE_URL + "/projects", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String projectId = context.getParameter("projectId", "");
				HttpResponse<String> response = delete(BASE_URL + "/projects/" + encode(projectId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String projectId = context.getParameter("projectId", "");
				HttpResponse<String> response = get(BASE_URL + "/projects/" + encode(projectId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/projects", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String projectId = context.getParameter("projectId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String name = context.getParameter("projectName", "");
				if (!name.isEmpty()) body.put("name", name);
				String color = context.getParameter("color", "");
				if (!color.isEmpty()) body.put("color", color);
				HttpResponse<String> response = post(BASE_URL + "/projects/" + encode(projectId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown project operation: " + operation);
		};
	}

	private Map<String, Object> handleSection(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("project_id", context.getParameter("projectId", ""));
				body.put("name", context.getParameter("sectionName", ""));
				HttpResponse<String> response = post(BASE_URL + "/sections", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String sectionId = context.getParameter("sectionId", "");
				HttpResponse<String> response = delete(BASE_URL + "/sections/" + encode(sectionId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String sectionId = context.getParameter("sectionId", "");
				HttpResponse<String> response = get(BASE_URL + "/sections/" + encode(sectionId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				String projectId = context.getParameter("projectId", "");
				HttpResponse<String> response = get(BASE_URL + "/sections?project_id=" + encode(projectId), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String sectionId = context.getParameter("sectionId", "");
				Map<String, Object> body = Map.of("name", context.getParameter("sectionName", ""));
				HttpResponse<String> response = post(BASE_URL + "/sections/" + encode(sectionId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown section operation: " + operation);
		};
	}

	private Map<String, Object> handleComment(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("task_id", context.getParameter("taskId", ""));
				body.put("content", context.getParameter("commentContent", ""));
				HttpResponse<String> response = post(BASE_URL + "/comments", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String commentId = context.getParameter("commentId", "");
				HttpResponse<String> response = delete(BASE_URL + "/comments/" + encode(commentId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String commentId = context.getParameter("commentId", "");
				HttpResponse<String> response = get(BASE_URL + "/comments/" + encode(commentId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				String taskId = context.getParameter("taskId", "");
				HttpResponse<String> response = get(BASE_URL + "/comments?task_id=" + encode(taskId), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String commentId = context.getParameter("commentId", "");
				Map<String, Object> body = Map.of("content", context.getParameter("commentContent", ""));
				HttpResponse<String> response = post(BASE_URL + "/comments/" + encode(commentId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown comment operation: " + operation);
		};
	}

	private Map<String, Object> handleLabel(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", context.getParameter("labelName", ""));
				String color = context.getParameter("color", "");
				if (!color.isEmpty()) body.put("color", color);
				HttpResponse<String> response = post(BASE_URL + "/labels", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String labelId = context.getParameter("labelId", "");
				HttpResponse<String> response = delete(BASE_URL + "/labels/" + encode(labelId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String labelId = context.getParameter("labelId", "");
				HttpResponse<String> response = get(BASE_URL + "/labels/" + encode(labelId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/labels", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String labelId = context.getParameter("labelId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String name = context.getParameter("labelName", "");
				if (!name.isEmpty()) body.put("name", name);
				String color = context.getParameter("color", "");
				if (!color.isEmpty()) body.put("color", color);
				HttpResponse<String> response = post(BASE_URL + "/labels/" + encode(labelId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown label operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("task")
						.options(List.of(
								ParameterOption.builder().name("Comment").value("comment").build(),
								ParameterOption.builder().name("Label").value("label").build(),
								ParameterOption.builder().name("Project").value("project").build(),
								ParameterOption.builder().name("Section").value("section").build(),
								ParameterOption.builder().name("Task").value("task").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Close").value("close").build(),
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Reopen").value("reopen").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("taskId").displayName("Task ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("projectId").displayName("Project ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("sectionId").displayName("Section ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("commentId").displayName("Comment ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("labelId").displayName("Label ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("content").displayName("Content")
						.type(ParameterType.STRING).defaultValue("")
						.description("Task content/title.").build(),
				NodeParameter.builder()
						.name("description").displayName("Description")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("projectName").displayName("Project Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("sectionName").displayName("Section Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("commentContent").displayName("Comment Content")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("labelName").displayName("Label Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("labels").displayName("Labels")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated label names.").build(),
				NodeParameter.builder()
						.name("priority").displayName("Priority")
						.type(ParameterType.NUMBER).defaultValue(1)
						.description("Priority 1 (normal) to 4 (urgent).").build(),
				NodeParameter.builder()
						.name("dueString").displayName("Due String")
						.type(ParameterType.STRING).defaultValue("")
						.description("Natural language due date (e.g., 'tomorrow').").build(),
				NodeParameter.builder()
						.name("dueDate").displayName("Due Date")
						.type(ParameterType.STRING).defaultValue("")
						.description("Due date in YYYY-MM-DD format.").build(),
				NodeParameter.builder()
						.name("assigneeId").displayName("Assignee ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("color").displayName("Color")
						.type(ParameterType.STRING).defaultValue("")
						.description("Color name (e.g., berry_red, red, orange).").build(),
				NodeParameter.builder()
						.name("isFavorite").displayName("Is Favorite")
						.type(ParameterType.BOOLEAN).defaultValue(false).build()
		);
	}
}
