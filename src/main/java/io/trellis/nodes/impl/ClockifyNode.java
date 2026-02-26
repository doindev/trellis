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

@Slf4j
@Node(
	type = "clockify",
	displayName = "Clockify",
	description = "Track time and manage projects, clients, tags, tasks, and time entries with Clockify.",
	category = "Miscellaneous",
	icon = "clockify",
	credentials = {"clockifyApi"}
)
public class ClockifyNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.clockify.me/api/v1";

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("timeEntry")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Client").value("client").description("Manage clients").build(),
				ParameterOption.builder().name("Project").value("project").description("Manage projects").build(),
				ParameterOption.builder().name("Tag").value("tag").description("Manage tags").build(),
				ParameterOption.builder().name("Task").value("task").description("Manage tasks").build(),
				ParameterOption.builder().name("Time Entry").value("timeEntry").description("Manage time entries").build(),
				ParameterOption.builder().name("User").value("user").description("Get users").build(),
				ParameterOption.builder().name("Workspace").value("workspace").description("Get workspaces").build()
			)).build());

		// Operation selectors
		addOperationSelectors(params);

		// Common parameters
		addCommonParameters(params);

		// Resource-specific parameters
		addClientParameters(params);
		addProjectParameters(params);
		addTagParameters(params);
		addTaskParameters(params);
		addTimeEntryParameters(params);

		return params;
	}

	// ========================= Operation Selectors =========================

	private void addOperationSelectors(List<NodeParameter> params) {
		// Client operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("client"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a client").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a client").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a client").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all clients").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a client").build()
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
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all projects").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a project").build()
			)).build());

		// Tag operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("tag"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a tag").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a tag").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a tag").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all tags").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a tag").build()
			)).build());

		// Task operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a task").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a task").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a task").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all tasks").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a task").build()
			)).build());

		// Time Entry operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeEntry"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a time entry").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a time entry").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a time entry").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all time entries").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a time entry").build()
			)).build());

		// User operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"))))
			.options(List.of(
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all users").build()
			)).build());

		// Workspace operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("workspace"))))
			.options(List.of(
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all workspaces").build()
			)).build());
	}

	// ========================= Common Parameters =========================

	private void addCommonParameters(List<NodeParameter> params) {
		// Workspace ID (required for most operations)
		params.add(NodeParameter.builder()
			.name("workspaceId").displayName("Workspace ID").type(ParameterType.STRING).required(true)
			.description("The ID of the workspace.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("client", "project", "tag", "task", "timeEntry", "user"))))
			.build());
	}

	// ========================= Client Parameters =========================

	private void addClientParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("clientName").displayName("Client Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("client"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("clientId").displayName("Client ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("client"), "operation", List.of("get", "delete", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("clientUpdateFields").displayName("Update Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("client"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("archived").displayName("Archived").type(ParameterType.BOOLEAN).build()
			)).build());
	}

	// ========================= Project Parameters =========================

	private void addProjectParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("projectName").displayName("Project Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("project"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("projectAdditionalFields").displayName("Additional Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("project"), "operation", List.of("create"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("clientId").displayName("Client ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("color").displayName("Color").type(ParameterType.STRING).placeHolder("#FF0000").build(),
				NodeParameter.builder().name("billable").displayName("Billable").type(ParameterType.BOOLEAN).build(),
				NodeParameter.builder().name("isPublic").displayName("Public").type(ParameterType.BOOLEAN).build(),
				NodeParameter.builder().name("note").displayName("Note").type(ParameterType.STRING).build()
			)).build());

		params.add(NodeParameter.builder()
			.name("projectId").displayName("Project ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("project"), "operation", List.of("get", "delete", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("projectUpdateFields").displayName("Update Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("project"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("clientId").displayName("Client ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("color").displayName("Color").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("billable").displayName("Billable").type(ParameterType.BOOLEAN).build(),
				NodeParameter.builder().name("isPublic").displayName("Public").type(ParameterType.BOOLEAN).build(),
				NodeParameter.builder().name("archived").displayName("Archived").type(ParameterType.BOOLEAN).build(),
				NodeParameter.builder().name("note").displayName("Note").type(ParameterType.STRING).build()
			)).build());
	}

	// ========================= Tag Parameters =========================

	private void addTagParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("tagName").displayName("Tag Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("tag"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("tagId").displayName("Tag ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("tag"), "operation", List.of("get", "delete", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("tagUpdateFields").displayName("Update Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("tag"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("archived").displayName("Archived").type(ParameterType.BOOLEAN).build()
			)).build());
	}

	// ========================= Task Parameters =========================

	private void addTaskParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("taskProjectId").displayName("Project ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"))))
			.build());

		params.add(NodeParameter.builder()
			.name("taskName").displayName("Task Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("taskAdditionalFields").displayName("Additional Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("create"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("assigneeId").displayName("Assignee ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("billable").displayName("Billable").type(ParameterType.BOOLEAN).build(),
				NodeParameter.builder().name("estimate").displayName("Estimate (e.g. PT1H30M)").type(ParameterType.STRING).build()
			)).build());

		params.add(NodeParameter.builder()
			.name("taskId").displayName("Task ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("get", "delete", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("taskUpdateFields").displayName("Update Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("assigneeId").displayName("Assignee ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("billable").displayName("Billable").type(ParameterType.BOOLEAN).build(),
				NodeParameter.builder().name("status").displayName("Status").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("Active").value("ACTIVE").build(),
						ParameterOption.builder().name("Done").value("DONE").build()
					)).build()
			)).build());
	}

	// ========================= Time Entry Parameters =========================

	private void addTimeEntryParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("timeEntryStart").displayName("Start Time (ISO 8601)").type(ParameterType.STRING).required(true)
			.placeHolder("2024-01-15T09:00:00Z")
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeEntry"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("timeEntryAdditionalFields").displayName("Additional Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeEntry"), "operation", List.of("create"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("end").displayName("End Time (ISO 8601)").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("description").displayName("Description").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("projectId").displayName("Project ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("taskId").displayName("Task ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("billable").displayName("Billable").type(ParameterType.BOOLEAN).build(),
				NodeParameter.builder().name("tagIds").displayName("Tag IDs (comma-separated)").type(ParameterType.STRING).build()
			)).build());

		params.add(NodeParameter.builder()
			.name("timeEntryId").displayName("Time Entry ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeEntry"), "operation", List.of("get", "delete", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("timeEntryUserId").displayName("User ID").type(ParameterType.STRING)
			.description("User ID to filter time entries. If empty, returns the authenticated user's entries.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeEntry"), "operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("timeEntryUpdateFields").displayName("Update Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeEntry"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("start").displayName("Start Time (ISO 8601)").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("end").displayName("End Time (ISO 8601)").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("description").displayName("Description").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("projectId").displayName("Project ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("taskId").displayName("Task ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("billable").displayName("Billable").type(ParameterType.BOOLEAN).build(),
				NodeParameter.builder().name("tagIds").displayName("Tag IDs (comma-separated)").type(ParameterType.STRING).build()
			)).build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "timeEntry");
		Map<String, Object> credentials = context.getCredentials();

		try {
			Map<String, String> headers = getAuthHeaders(credentials);
			String workspaceId = context.getParameter("workspaceId", "");

			return switch (resource) {
				case "client" -> executeClient(context, headers, workspaceId);
				case "project" -> executeProject(context, headers, workspaceId);
				case "tag" -> executeTag(context, headers, workspaceId);
				case "task" -> executeTask(context, headers, workspaceId);
				case "timeEntry" -> executeTimeEntry(context, headers, workspaceId);
				case "user" -> executeUser(headers, workspaceId);
				case "workspace" -> executeWorkspace(headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Clockify API error: " + e.getMessage(), e);
		}
	}

	// ========================= Client Execute =========================

	private NodeExecutionResult executeClient(NodeExecutionContext context, Map<String, String> headers, String workspaceId) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		String url = BASE_URL + "/workspaces/" + encode(workspaceId) + "/clients";

		switch (operation) {
			case "create": {
				String name = context.getParameter("clientName", "");
				Map<String, Object> body = Map.of("name", name);
				HttpResponse<String> response = post(url, body, headers);
				return toResult(response);
			}
			case "delete": {
				String clientId = context.getParameter("clientId", "");
				HttpResponse<String> response = delete(url + "/" + encode(clientId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String clientId = context.getParameter("clientId", "");
				HttpResponse<String> response = get(url + "/" + encode(clientId), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(url, headers);
				return toArrayResult(response);
			}
			case "update": {
				String clientId = context.getParameter("clientId", "");
				Map<String, Object> updateFields = context.getParameter("clientUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>(updateFields);
				HttpResponse<String> response = put(url + "/" + encode(clientId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown client operation: " + operation);
		}
	}

	// ========================= Project Execute =========================

	private NodeExecutionResult executeProject(NodeExecutionContext context, Map<String, String> headers, String workspaceId) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		String url = BASE_URL + "/workspaces/" + encode(workspaceId) + "/projects";

		switch (operation) {
			case "create": {
				String name = context.getParameter("projectName", "");
				Map<String, Object> additional = context.getParameter("projectAdditionalFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", name);
				body.putAll(additional);
				HttpResponse<String> response = post(url, body, headers);
				return toResult(response);
			}
			case "delete": {
				String projectId = context.getParameter("projectId", "");
				HttpResponse<String> response = delete(url + "/" + encode(projectId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String projectId = context.getParameter("projectId", "");
				HttpResponse<String> response = get(url + "/" + encode(projectId), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(url, headers);
				return toArrayResult(response);
			}
			case "update": {
				String projectId = context.getParameter("projectId", "");
				Map<String, Object> updateFields = context.getParameter("projectUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>(updateFields);
				HttpResponse<String> response = put(url + "/" + encode(projectId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown project operation: " + operation);
		}
	}

	// ========================= Tag Execute =========================

	private NodeExecutionResult executeTag(NodeExecutionContext context, Map<String, String> headers, String workspaceId) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		String url = BASE_URL + "/workspaces/" + encode(workspaceId) + "/tags";

		switch (operation) {
			case "create": {
				String name = context.getParameter("tagName", "");
				Map<String, Object> body = Map.of("name", name);
				HttpResponse<String> response = post(url, body, headers);
				return toResult(response);
			}
			case "delete": {
				String tagId = context.getParameter("tagId", "");
				HttpResponse<String> response = delete(url + "/" + encode(tagId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String tagId = context.getParameter("tagId", "");
				HttpResponse<String> response = get(url + "/" + encode(tagId), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(url, headers);
				return toArrayResult(response);
			}
			case "update": {
				String tagId = context.getParameter("tagId", "");
				Map<String, Object> updateFields = context.getParameter("tagUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>(updateFields);
				HttpResponse<String> response = put(url + "/" + encode(tagId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown tag operation: " + operation);
		}
	}

	// ========================= Task Execute =========================

	private NodeExecutionResult executeTask(NodeExecutionContext context, Map<String, String> headers, String workspaceId) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		String projectId = context.getParameter("taskProjectId", "");
		String url = BASE_URL + "/workspaces/" + encode(workspaceId) + "/projects/" + encode(projectId) + "/tasks";

		switch (operation) {
			case "create": {
				String name = context.getParameter("taskName", "");
				Map<String, Object> additional = context.getParameter("taskAdditionalFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", name);
				body.putAll(additional);
				HttpResponse<String> response = post(url, body, headers);
				return toResult(response);
			}
			case "delete": {
				String taskId = context.getParameter("taskId", "");
				HttpResponse<String> response = delete(url + "/" + encode(taskId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String taskId = context.getParameter("taskId", "");
				HttpResponse<String> response = get(url + "/" + encode(taskId), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(url, headers);
				return toArrayResult(response);
			}
			case "update": {
				String taskId = context.getParameter("taskId", "");
				Map<String, Object> updateFields = context.getParameter("taskUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>(updateFields);
				HttpResponse<String> response = put(url + "/" + encode(taskId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown task operation: " + operation);
		}
	}

	// ========================= Time Entry Execute =========================

	private NodeExecutionResult executeTimeEntry(NodeExecutionContext context, Map<String, String> headers, String workspaceId) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "create": {
				String start = context.getParameter("timeEntryStart", "");
				Map<String, Object> additional = context.getParameter("timeEntryAdditionalFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("start", start);
				putIfPresent(body, "end", additional.get("end"));
				putIfPresent(body, "description", additional.get("description"));
				putIfPresent(body, "projectId", additional.get("projectId"));
				putIfPresent(body, "taskId", additional.get("taskId"));
				if (additional.get("billable") != null) {
					body.put("billable", toBoolean(additional.get("billable"), false));
				}
				String tagIds = String.valueOf(additional.getOrDefault("tagIds", ""));
				if (!tagIds.isEmpty()) {
					body.put("tagIds", Arrays.asList(tagIds.split(",")));
				}
				String url = BASE_URL + "/workspaces/" + encode(workspaceId) + "/time-entries";
				HttpResponse<String> response = post(url, body, headers);
				return toResult(response);
			}
			case "delete": {
				String timeEntryId = context.getParameter("timeEntryId", "");
				String url = BASE_URL + "/workspaces/" + encode(workspaceId) + "/time-entries/" + encode(timeEntryId);
				HttpResponse<String> response = delete(url, headers);
				return toDeleteResult(response);
			}
			case "get": {
				String timeEntryId = context.getParameter("timeEntryId", "");
				String url = BASE_URL + "/workspaces/" + encode(workspaceId) + "/time-entries/" + encode(timeEntryId);
				HttpResponse<String> response = get(url, headers);
				return toResult(response);
			}
			case "getAll": {
				String userId = context.getParameter("timeEntryUserId", "");
				if (userId.isEmpty()) {
					// Get current user first
					HttpResponse<String> userResponse = get(BASE_URL + "/user", headers);
					Map<String, Object> user = parseResponse(userResponse);
					userId = String.valueOf(user.get("id"));
				}
				String url = BASE_URL + "/workspaces/" + encode(workspaceId) + "/user/" + encode(userId) + "/time-entries";
				HttpResponse<String> response = get(url, headers);
				return toArrayResult(response);
			}
			case "update": {
				String timeEntryId = context.getParameter("timeEntryId", "");
				Map<String, Object> updateFields = context.getParameter("timeEntryUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "start", updateFields.get("start"));
				putIfPresent(body, "end", updateFields.get("end"));
				putIfPresent(body, "description", updateFields.get("description"));
				putIfPresent(body, "projectId", updateFields.get("projectId"));
				putIfPresent(body, "taskId", updateFields.get("taskId"));
				if (updateFields.get("billable") != null) {
					body.put("billable", toBoolean(updateFields.get("billable"), false));
				}
				String tagIds = String.valueOf(updateFields.getOrDefault("tagIds", ""));
				if (!tagIds.isEmpty()) {
					body.put("tagIds", Arrays.asList(tagIds.split(",")));
				}
				String url = BASE_URL + "/workspaces/" + encode(workspaceId) + "/time-entries/" + encode(timeEntryId);
				HttpResponse<String> response = put(url, body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown time entry operation: " + operation);
		}
	}

	// ========================= User Execute =========================

	private NodeExecutionResult executeUser(Map<String, String> headers, String workspaceId) throws Exception {
		String url = BASE_URL + "/workspaces/" + encode(workspaceId) + "/users";
		HttpResponse<String> response = get(url, headers);
		return toArrayResult(response);
	}

	// ========================= Workspace Execute =========================

	private NodeExecutionResult executeWorkspace(Map<String, String> headers) throws Exception {
		String url = BASE_URL + "/workspaces";
		HttpResponse<String> response = get(url, headers);
		return toArrayResult(response);
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("X-Api-Key", String.valueOf(credentials.getOrDefault("apiKey", "")));
		headers.put("Content-Type", "application/json");
		return headers;
	}

	private void putIfPresent(Map<String, Object> map, String key, Object value) {
		if (value != null && !String.valueOf(value).isEmpty()) {
			map.put(key, value);
		}
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toArrayResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		List<Map<String, Object>> items = parseArrayResponse(response);
		List<Map<String, Object>> result = new ArrayList<>();
		for (Map<String, Object> item : items) {
			result.add(wrapInJson(item));
		}
		return result.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(result);
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		if (response.body() == null || response.body().isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		return toResult(response);
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Clockify API error (HTTP " + response.statusCode() + "): " + body);
	}
}
