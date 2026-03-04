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
 * Harvest Node -- manage clients, projects, time entries, tasks, and users
 * via the Harvest REST API v2.
 */
@Slf4j
@Node(
	type = "harvest",
	displayName = "Harvest",
	description = "Manage clients, projects, time entries, tasks, and users in Harvest",
	category = "Miscellaneous",
	icon = "harvest",
	credentials = {"harvestApi"},
	searchOnly = true
)
public class HarvestNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.harvestapp.com/v2";

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
				ParameterOption.builder().name("Time Entry").value("timeEntry").description("Manage time entries").build(),
				ParameterOption.builder().name("Task").value("task").description("Manage tasks").build(),
				ParameterOption.builder().name("User").value("user").description("Manage users").build()
			)).build());

		// Operation selectors
		addOperationSelectors(params);

		// Resource-specific parameters
		addClientParameters(params);
		addProjectParameters(params);
		addTimeEntryParameters(params);
		addTaskParameters(params);
		addUserParameters(params);

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
				ParameterOption.builder().name("Update").value("update").description("Update a time entry").build(),
				ParameterOption.builder().name("Start").value("start").description("Restart a stopped time entry").build(),
				ParameterOption.builder().name("Stop").value("stop").description("Stop a running time entry").build()
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

		// User operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get a user").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all users").build(),
				ParameterOption.builder().name("Get Current").value("getCurrent").description("Get the currently authenticated user").build()
			)).build());
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
				NodeParameter.builder().name("isActive").displayName("Is Active").type(ParameterType.BOOLEAN).build(),
				NodeParameter.builder().name("address").displayName("Address").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("currency").displayName("Currency").type(ParameterType.STRING).placeHolder("USD").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("clientAdditionalFields").displayName("Additional Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("client"), "operation", List.of("create"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("isActive").displayName("Is Active").type(ParameterType.BOOLEAN).defaultValue(true).build(),
				NodeParameter.builder().name("address").displayName("Address").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("currency").displayName("Currency").type(ParameterType.STRING).placeHolder("USD").build()
			)).build());
	}

	// ========================= Project Parameters =========================

	private void addProjectParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("projectClientId").displayName("Client ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("project"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("projectName").displayName("Project Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("project"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("projectBillBy").displayName("Bill By").type(ParameterType.OPTIONS).required(true).defaultValue("none")
			.displayOptions(Map.of("show", Map.of("resource", List.of("project"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("None").value("none").build(),
				ParameterOption.builder().name("People").value("People").build(),
				ParameterOption.builder().name("Project").value("Project").build(),
				ParameterOption.builder().name("Tasks").value("Tasks").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("projectBudgetBy").displayName("Budget By").type(ParameterType.OPTIONS).required(true).defaultValue("none")
			.displayOptions(Map.of("show", Map.of("resource", List.of("project"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("None").value("none").build(),
				ParameterOption.builder().name("Hours per Project").value("project").build(),
				ParameterOption.builder().name("Total Project Fees").value("project_cost").build(),
				ParameterOption.builder().name("Hours per Task").value("task").build(),
				ParameterOption.builder().name("Fees per Task").value("task_fees").build(),
				ParameterOption.builder().name("Hours per Person").value("person").build()
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
				NodeParameter.builder().name("isActive").displayName("Is Active").type(ParameterType.BOOLEAN).build(),
				NodeParameter.builder().name("isBillable").displayName("Is Billable").type(ParameterType.BOOLEAN).build(),
				NodeParameter.builder().name("notes").displayName("Notes").type(ParameterType.STRING).build()
			)).build());
	}

	// ========================= Time Entry Parameters =========================

	private void addTimeEntryParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("timeEntryProjectId").displayName("Project ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeEntry"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("timeEntryTaskId").displayName("Task ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeEntry"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("timeEntrySpentDate").displayName("Spent Date").type(ParameterType.STRING).required(true)
			.placeHolder("2024-01-15")
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeEntry"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("timeEntryAdditionalFields").displayName("Additional Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeEntry"), "operation", List.of("create"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("hours").displayName("Hours").type(ParameterType.NUMBER).build(),
				NodeParameter.builder().name("notes").displayName("Notes").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("userId").displayName("User ID").type(ParameterType.STRING).build()
			)).build());

		params.add(NodeParameter.builder()
			.name("timeEntryId").displayName("Time Entry ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeEntry"), "operation", List.of("get", "delete", "update", "start", "stop"))))
			.build());

		params.add(NodeParameter.builder()
			.name("timeEntryUpdateFields").displayName("Update Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeEntry"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("projectId").displayName("Project ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("taskId").displayName("Task ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("spentDate").displayName("Spent Date").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("hours").displayName("Hours").type(ParameterType.NUMBER).build(),
				NodeParameter.builder().name("notes").displayName("Notes").type(ParameterType.STRING).build()
			)).build());
	}

	// ========================= Task Parameters =========================

	private void addTaskParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("taskName").displayName("Task Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("taskAdditionalFields").displayName("Additional Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("create"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("billableByDefault").displayName("Billable by Default").type(ParameterType.BOOLEAN).build(),
				NodeParameter.builder().name("defaultHourlyRate").displayName("Default Hourly Rate").type(ParameterType.NUMBER).build(),
				NodeParameter.builder().name("isDefault").displayName("Is Default").type(ParameterType.BOOLEAN).build()
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
				NodeParameter.builder().name("billableByDefault").displayName("Billable by Default").type(ParameterType.BOOLEAN).build(),
				NodeParameter.builder().name("defaultHourlyRate").displayName("Default Hourly Rate").type(ParameterType.NUMBER).build(),
				NodeParameter.builder().name("isActive").displayName("Is Active").type(ParameterType.BOOLEAN).build()
			)).build());
	}

	// ========================= User Parameters =========================

	private void addUserParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("userId").displayName("User ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("get"))))
			.build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "timeEntry");
		Map<String, Object> credentials = context.getCredentials();

		try {
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (resource) {
				case "client" -> executeClient(context, headers);
				case "project" -> executeProject(context, headers);
				case "timeEntry" -> executeTimeEntry(context, headers);
				case "task" -> executeTask(context, headers);
				case "user" -> executeUser(context, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Harvest API error: " + e.getMessage(), e);
		}
	}

	// ========================= Client Execute =========================

	private NodeExecutionResult executeClient(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "create": {
				String name = context.getParameter("clientName", "");
				Map<String, Object> additional = context.getParameter("clientAdditionalFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", name);
				putIfPresent(body, "is_active", additional.get("isActive"));
				putIfPresent(body, "address", additional.get("address"));
				putIfPresent(body, "currency", additional.get("currency"));
				HttpResponse<String> response = post(BASE_URL + "/clients", body, headers);
				return toResult(response);
			}
			case "delete": {
				String clientId = context.getParameter("clientId", "");
				HttpResponse<String> response = delete(BASE_URL + "/clients/" + encode(clientId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String clientId = context.getParameter("clientId", "");
				HttpResponse<String> response = get(BASE_URL + "/clients/" + encode(clientId), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(BASE_URL + "/clients", headers);
				return toListResult(response, "clients");
			}
			case "update": {
				String clientId = context.getParameter("clientId", "");
				Map<String, Object> updateFields = context.getParameter("clientUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "name", updateFields.get("name"));
				putIfPresent(body, "is_active", updateFields.get("isActive"));
				putIfPresent(body, "address", updateFields.get("address"));
				putIfPresent(body, "currency", updateFields.get("currency"));
				HttpResponse<String> response = patch(BASE_URL + "/clients/" + encode(clientId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown client operation: " + operation);
		}
	}

	// ========================= Project Execute =========================

	private NodeExecutionResult executeProject(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "create": {
				String clientId = context.getParameter("projectClientId", "");
				String name = context.getParameter("projectName", "");
				String billBy = context.getParameter("projectBillBy", "none");
				String budgetBy = context.getParameter("projectBudgetBy", "none");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("client_id", Integer.parseInt(clientId));
				body.put("name", name);
				body.put("bill_by", billBy);
				body.put("budget_by", budgetBy);
				body.put("is_billable", !"none".equals(billBy));
				HttpResponse<String> response = post(BASE_URL + "/projects", body, headers);
				return toResult(response);
			}
			case "delete": {
				String projectId = context.getParameter("projectId", "");
				HttpResponse<String> response = delete(BASE_URL + "/projects/" + encode(projectId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String projectId = context.getParameter("projectId", "");
				HttpResponse<String> response = get(BASE_URL + "/projects/" + encode(projectId), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(BASE_URL + "/projects", headers);
				return toListResult(response, "projects");
			}
			case "update": {
				String projectId = context.getParameter("projectId", "");
				Map<String, Object> updateFields = context.getParameter("projectUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "name", updateFields.get("name"));
				putIfPresent(body, "client_id", updateFields.get("clientId"));
				putIfPresent(body, "is_active", updateFields.get("isActive"));
				putIfPresent(body, "is_billable", updateFields.get("isBillable"));
				putIfPresent(body, "notes", updateFields.get("notes"));
				HttpResponse<String> response = patch(BASE_URL + "/projects/" + encode(projectId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown project operation: " + operation);
		}
	}

	// ========================= Time Entry Execute =========================

	private NodeExecutionResult executeTimeEntry(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "create": {
				String projectId = context.getParameter("timeEntryProjectId", "");
				String taskId = context.getParameter("timeEntryTaskId", "");
				String spentDate = context.getParameter("timeEntrySpentDate", "");
				Map<String, Object> additional = context.getParameter("timeEntryAdditionalFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("project_id", Integer.parseInt(projectId));
				body.put("task_id", Integer.parseInt(taskId));
				body.put("spent_date", spentDate);
				putIfPresent(body, "hours", additional.get("hours"));
				putIfPresent(body, "notes", additional.get("notes"));
				putIfPresent(body, "user_id", additional.get("userId"));
				HttpResponse<String> response = post(BASE_URL + "/time_entries", body, headers);
				return toResult(response);
			}
			case "delete": {
				String timeEntryId = context.getParameter("timeEntryId", "");
				HttpResponse<String> response = delete(BASE_URL + "/time_entries/" + encode(timeEntryId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String timeEntryId = context.getParameter("timeEntryId", "");
				HttpResponse<String> response = get(BASE_URL + "/time_entries/" + encode(timeEntryId), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(BASE_URL + "/time_entries", headers);
				return toListResult(response, "time_entries");
			}
			case "update": {
				String timeEntryId = context.getParameter("timeEntryId", "");
				Map<String, Object> updateFields = context.getParameter("timeEntryUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "project_id", updateFields.get("projectId"));
				putIfPresent(body, "task_id", updateFields.get("taskId"));
				putIfPresent(body, "spent_date", updateFields.get("spentDate"));
				putIfPresent(body, "hours", updateFields.get("hours"));
				putIfPresent(body, "notes", updateFields.get("notes"));
				HttpResponse<String> response = patch(BASE_URL + "/time_entries/" + encode(timeEntryId), body, headers);
				return toResult(response);
			}
			case "start": {
				String timeEntryId = context.getParameter("timeEntryId", "");
				HttpResponse<String> response = patch(BASE_URL + "/time_entries/" + encode(timeEntryId) + "/restart", Map.of(), headers);
				return toResult(response);
			}
			case "stop": {
				String timeEntryId = context.getParameter("timeEntryId", "");
				HttpResponse<String> response = patch(BASE_URL + "/time_entries/" + encode(timeEntryId) + "/stop", Map.of(), headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown time entry operation: " + operation);
		}
	}

	// ========================= Task Execute =========================

	private NodeExecutionResult executeTask(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "create": {
				String name = context.getParameter("taskName", "");
				Map<String, Object> additional = context.getParameter("taskAdditionalFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", name);
				putIfPresent(body, "billable_by_default", additional.get("billableByDefault"));
				putIfPresent(body, "default_hourly_rate", additional.get("defaultHourlyRate"));
				putIfPresent(body, "is_default", additional.get("isDefault"));
				HttpResponse<String> response = post(BASE_URL + "/tasks", body, headers);
				return toResult(response);
			}
			case "delete": {
				String taskId = context.getParameter("taskId", "");
				HttpResponse<String> response = delete(BASE_URL + "/tasks/" + encode(taskId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String taskId = context.getParameter("taskId", "");
				HttpResponse<String> response = get(BASE_URL + "/tasks/" + encode(taskId), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(BASE_URL + "/tasks", headers);
				return toListResult(response, "tasks");
			}
			case "update": {
				String taskId = context.getParameter("taskId", "");
				Map<String, Object> updateFields = context.getParameter("taskUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "name", updateFields.get("name"));
				putIfPresent(body, "billable_by_default", updateFields.get("billableByDefault"));
				putIfPresent(body, "default_hourly_rate", updateFields.get("defaultHourlyRate"));
				putIfPresent(body, "is_active", updateFields.get("isActive"));
				HttpResponse<String> response = patch(BASE_URL + "/tasks/" + encode(taskId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown task operation: " + operation);
		}
	}

	// ========================= User Execute =========================

	private NodeExecutionResult executeUser(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "get": {
				String userId = context.getParameter("userId", "");
				HttpResponse<String> response = get(BASE_URL + "/users/" + encode(userId), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(BASE_URL + "/users", headers);
				return toListResult(response, "users");
			}
			case "getCurrent": {
				HttpResponse<String> response = get(BASE_URL + "/users/me", headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown user operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));
		String accountId = String.valueOf(credentials.getOrDefault("accountId", ""));
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Harvest-Account-Id", accountId);
		headers.put("Content-Type", "application/json");
		headers.put("User-Agent", "Trellis");
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

	private NodeExecutionResult toListResult(HttpResponse<String> response, String dataKey) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		Object data = parsed.getOrDefault(dataKey, parsed);
		if (data instanceof List) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Object item : (List<?>) data) {
				if (item instanceof Map) {
					items.add(wrapInJson(item));
				}
			}
			return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
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
		return NodeExecutionResult.error("Harvest API error (HTTP " + response.statusCode() + "): " + body);
	}
}
