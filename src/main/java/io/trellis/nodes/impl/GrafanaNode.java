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
 * Grafana Node -- manage Grafana dashboards, teams, team members, and users
 * via the Grafana HTTP API.
 */
@Slf4j
@Node(
	type = "grafana",
	displayName = "Grafana",
	description = "Manage Grafana dashboards, teams, and users",
	category = "Development",
	icon = "grafana",
	credentials = {"grafanaApi"}
)
public class GrafanaNode extends AbstractApiNode {

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("dashboard")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Dashboard").value("dashboard")
					.description("Manage Grafana dashboards").build(),
				ParameterOption.builder().name("Team").value("team")
					.description("Manage Grafana teams").build(),
				ParameterOption.builder().name("Team Member").value("teamMember")
					.description("Manage team members").build(),
				ParameterOption.builder().name("User").value("user")
					.description("Manage Grafana users").build()
			)).build());

		// Dashboard operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("dashboard"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a dashboard").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a dashboard").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a dashboard by UID").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many dashboards").build()
			)).build());

		// Team operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("team"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a team").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a team").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a team by ID").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many teams").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a team").build()
			)).build());

		// Team Member operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("teamMember"))))
			.options(List.of(
				ParameterOption.builder().name("Add").value("add").description("Add a member to a team").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all members of a team").build(),
				ParameterOption.builder().name("Remove").value("remove").description("Remove a member from a team").build()
			)).build());

		// User operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"))))
			.options(List.of(
				ParameterOption.builder().name("Delete").value("delete").description("Delete a user").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a user by ID").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all users in the organization").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a user").build()
			)).build());

		// ---- Dashboard parameters ----
		params.add(NodeParameter.builder()
			.name("dashboardUid").displayName("Dashboard UID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("dashboard"), "operation", List.of("get", "delete"))))
			.build());

		params.add(NodeParameter.builder()
			.name("dashboardTitle").displayName("Title")
			.type(ParameterType.STRING).required(true)
			.placeHolder("My Dashboard")
			.displayOptions(Map.of("show", Map.of("resource", List.of("dashboard"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("dashboardFolderId").displayName("Folder ID")
			.type(ParameterType.NUMBER)
			.defaultValue(0)
			.description("The ID of the folder to save the dashboard in. 0 for General folder.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("dashboard"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("dashboardJson").displayName("Dashboard JSON")
			.type(ParameterType.JSON)
			.description("The full dashboard model JSON. If empty, a default empty dashboard is created.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("dashboard"), "operation", List.of("create"))))
			.build());

		// ---- Team parameters ----
		params.add(NodeParameter.builder()
			.name("teamId").displayName("Team ID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("team"), "operation", List.of("get", "delete", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("teamName").displayName("Name")
			.type(ParameterType.STRING).required(true)
			.placeHolder("My Team")
			.displayOptions(Map.of("show", Map.of("resource", List.of("team"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("teamEmail").displayName("Email")
			.type(ParameterType.STRING)
			.displayOptions(Map.of("show", Map.of("resource", List.of("team"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("teamUpdateName").displayName("Name")
			.type(ParameterType.STRING)
			.description("The new name for the team.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("team"), "operation", List.of("update"))))
			.build());

		// ---- Team Member parameters ----
		params.add(NodeParameter.builder()
			.name("memberTeamId").displayName("Team ID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("teamMember"))))
			.build());

		params.add(NodeParameter.builder()
			.name("memberUserId").displayName("User ID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("teamMember"), "operation", List.of("add", "remove"))))
			.build());

		// ---- User parameters ----
		params.add(NodeParameter.builder()
			.name("userId").displayName("User ID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("get", "delete", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("userUpdateFields").displayName("Update Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("email").displayName("Email").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("login").displayName("Login").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("theme").displayName("Theme").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("Dark").value("dark").build(),
						ParameterOption.builder().name("Light").value("light").build()
					)).build()
			)).build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "dashboard");
		String operation = context.getParameter("operation", "get");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String baseUrl = getApiBaseUrl(credentials);
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (resource) {
				case "dashboard" -> executeDashboard(context, operation, baseUrl, headers);
				case "team" -> executeTeam(context, operation, baseUrl, headers);
				case "teamMember" -> executeTeamMember(context, operation, baseUrl, headers);
				case "user" -> executeUser(context, operation, baseUrl, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Grafana API error: " + e.getMessage(), e);
		}
	}

	// ========================= Dashboard Operations =========================

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeDashboard(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String title = context.getParameter("dashboardTitle", "");
				int folderId = toInt(context.getParameter("dashboardFolderId", 0), 0);
				String dashboardJson = context.getParameter("dashboardJson", "");

				Map<String, Object> dashboard;
				if (dashboardJson != null && !dashboardJson.isBlank()) {
					dashboard = parseJsonObject(dashboardJson);
				} else {
					dashboard = new LinkedHashMap<>();
				}
				dashboard.put("title", title);
				// Ensure no ID set for new dashboard
				dashboard.remove("id");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("dashboard", dashboard);
				body.put("folderId", folderId);
				body.put("overwrite", false);

				HttpResponse<String> response = post(baseUrl + "/dashboards/db", body, headers);
				return toResult(response);
			}
			case "delete": {
				String uid = context.getParameter("dashboardUid", "");
				HttpResponse<String> response = delete(baseUrl + "/dashboards/uid/" + encode(uid), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String uid = context.getParameter("dashboardUid", "");
				HttpResponse<String> response = get(baseUrl + "/dashboards/uid/" + encode(uid), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(baseUrl + "/search?type=dash-db", headers);
				return toArrayResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown dashboard operation: " + operation);
		}
	}

	// ========================= Team Operations =========================

	private NodeExecutionResult executeTeam(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String name = context.getParameter("teamName", "");
				String email = context.getParameter("teamEmail", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", name);
				if (email != null && !email.isEmpty()) {
					body.put("email", email);
				}

				HttpResponse<String> response = post(baseUrl + "/teams", body, headers);
				return toResult(response);
			}
			case "delete": {
				String teamId = context.getParameter("teamId", "");
				HttpResponse<String> response = delete(baseUrl + "/teams/" + encode(teamId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String teamId = context.getParameter("teamId", "");
				HttpResponse<String> response = get(baseUrl + "/teams/" + encode(teamId), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(baseUrl + "/teams/search", headers);
				return toResult(response);
			}
			case "update": {
				String teamId = context.getParameter("teamId", "");
				String updateName = context.getParameter("teamUpdateName", "");
				String email = context.getParameter("teamEmail", "");

				Map<String, Object> body = new LinkedHashMap<>();
				if (updateName != null && !updateName.isEmpty()) {
					body.put("name", updateName);
				}
				if (email != null && !email.isEmpty()) {
					body.put("email", email);
				}

				HttpResponse<String> response = put(baseUrl + "/teams/" + encode(teamId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown team operation: " + operation);
		}
	}

	// ========================= Team Member Operations =========================

	private NodeExecutionResult executeTeamMember(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		String teamId = context.getParameter("memberTeamId", "");

		switch (operation) {
			case "add": {
				String userId = context.getParameter("memberUserId", "");
				Map<String, Object> body = Map.of("userId", Integer.parseInt(userId));
				HttpResponse<String> response = post(baseUrl + "/teams/" + encode(teamId) + "/members", body, headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(baseUrl + "/teams/" + encode(teamId) + "/members", headers);
				return toArrayResult(response);
			}
			case "remove": {
				String userId = context.getParameter("memberUserId", "");
				HttpResponse<String> response = delete(
					baseUrl + "/teams/" + encode(teamId) + "/members/" + encode(userId), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown team member operation: " + operation);
		}
	}

	// ========================= User Operations =========================

	private NodeExecutionResult executeUser(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "delete": {
				String userId = context.getParameter("userId", "");
				HttpResponse<String> response = delete(baseUrl + "/admin/users/" + encode(userId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String userId = context.getParameter("userId", "");
				HttpResponse<String> response = get(baseUrl + "/users/" + encode(userId), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(baseUrl + "/org/users", headers);
				return toArrayResult(response);
			}
			case "update": {
				String userId = context.getParameter("userId", "");
				Map<String, Object> updateFields = context.getParameter("userUpdateFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "name", updateFields.get("name"));
				putIfPresent(body, "email", updateFields.get("email"));
				putIfPresent(body, "login", updateFields.get("login"));
				putIfPresent(body, "theme", updateFields.get("theme"));

				HttpResponse<String> response = put(baseUrl + "/users/" + encode(userId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown user operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private String getApiBaseUrl(Map<String, Object> credentials) {
		String baseUrl = String.valueOf(credentials.getOrDefault("baseUrl", "http://localhost:3000"));
		// Remove trailing slash
		if (baseUrl.endsWith("/")) {
			baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		}
		return baseUrl + "/api";
	}

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		String apiKey = String.valueOf(credentials.getOrDefault("apiKey", ""));
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + apiKey);
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
			return grafanaError(response);
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
			return grafanaError(response);
		}
		List<Map<String, Object>> items = parseArrayResponse(response);
		if (items.isEmpty()) {
			return NodeExecutionResult.empty();
		}
		List<Map<String, Object>> results = new ArrayList<>();
		for (Map<String, Object> item : items) {
			results.add(wrapInJson(item));
		}
		return NodeExecutionResult.success(results);
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return grafanaError(response);
		}
		if (response.body() == null || response.body().isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		return toResult(response);
	}

	private NodeExecutionResult grafanaError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Grafana API error (HTTP " + response.statusCode() + "): " + body);
	}
}
