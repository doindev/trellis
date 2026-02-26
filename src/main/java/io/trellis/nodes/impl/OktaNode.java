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
 * Okta Node -- manage users and groups via the Okta REST API.
 */
@Slf4j
@Node(
	type = "okta",
	displayName = "Okta",
	description = "Manage users and groups in Okta",
	category = "Miscellaneous",
	icon = "okta",
	credentials = {"oktaApi"}
)
public class OktaNode extends AbstractApiNode {

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("user")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("User").value("user").description("Manage Okta users").build(),
				ParameterOption.builder().name("Group").value("group").description("Manage Okta groups").build()
			)).build());

		// User operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a user").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Deactivate and delete a user").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a user by ID").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all users").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a user").build()
			)).build());

		// Group operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("group"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a group").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a group").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a group by ID").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all groups").build(),
				ParameterOption.builder().name("Add User").value("addUser").description("Add a user to a group").build(),
				ParameterOption.builder().name("Remove User").value("removeUser").description("Remove a user from a group").build()
			)).build());

		// User parameters
		addUserParameters(params);

		// Group parameters
		addGroupParameters(params);

		return params;
	}

	// ========================= User Parameters =========================

	private void addUserParameters(List<NodeParameter> params) {
		// User > Create
		params.add(NodeParameter.builder()
			.name("userFirstName").displayName("First Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("userLastName").displayName("Last Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("userEmail").displayName("Email").type(ParameterType.STRING).required(true)
			.placeHolder("user@example.com")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("userLogin").displayName("Login").type(ParameterType.STRING).required(true)
			.placeHolder("user@example.com")
			.description("Unique login identifier for the user. Usually the same as email.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("userAdditionalFields").displayName("Additional Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("secondEmail").displayName("Secondary Email").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("mobilePhone").displayName("Mobile Phone").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("primaryPhone").displayName("Primary Phone").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("activate").displayName("Activate Immediately").type(ParameterType.BOOLEAN).defaultValue(true).build()
			)).build());

		// User > Get/Delete/Update
		params.add(NodeParameter.builder()
			.name("userId").displayName("User ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("get", "delete", "update"))))
			.build());

		// User > Update
		params.add(NodeParameter.builder()
			.name("userUpdateFields").displayName("Update Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("firstName").displayName("First Name").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("lastName").displayName("Last Name").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("email").displayName("Email").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("login").displayName("Login").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("mobilePhone").displayName("Mobile Phone").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("primaryPhone").displayName("Primary Phone").type(ParameterType.STRING).build()
			)).build());

		// User > GetAll: search query
		params.add(NodeParameter.builder()
			.name("userSearch").displayName("Search Query").type(ParameterType.STRING)
			.description("Okta filter expression (e.g., profile.email eq \"john@example.com\").")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("userLimit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(200)
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("getAll"))))
			.build());
	}

	// ========================= Group Parameters =========================

	private void addGroupParameters(List<NodeParameter> params) {
		// Group > Create
		params.add(NodeParameter.builder()
			.name("groupName").displayName("Group Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("groupDescription").displayName("Description").type(ParameterType.STRING)
			.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("create"))))
			.build());

		// Group > Get/Delete
		params.add(NodeParameter.builder()
			.name("groupId").displayName("Group ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("get", "delete", "addUser", "removeUser"))))
			.build());

		// Group > AddUser / RemoveUser
		params.add(NodeParameter.builder()
			.name("groupUserId").displayName("User ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("addUser", "removeUser"))))
			.build());

		// Group > GetAll: search query
		params.add(NodeParameter.builder()
			.name("groupSearch").displayName("Search Query").type(ParameterType.STRING)
			.description("Okta search expression to filter groups.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("groupLimit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(200)
			.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("getAll"))))
			.build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "user");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String baseUrl = getApiBaseUrl(credentials);
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (resource) {
				case "user" -> executeUser(context, baseUrl, headers);
				case "group" -> executeGroup(context, baseUrl, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Okta API error: " + e.getMessage(), e);
		}
	}

	// ========================= User Execute =========================

	private NodeExecutionResult executeUser(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "get");

		switch (operation) {
			case "create": {
				String firstName = context.getParameter("userFirstName", "");
				String lastName = context.getParameter("userLastName", "");
				String email = context.getParameter("userEmail", "");
				String login = context.getParameter("userLogin", "");
				Map<String, Object> additional = context.getParameter("userAdditionalFields", Map.of());

				Map<String, Object> profile = new LinkedHashMap<>();
				profile.put("firstName", firstName);
				profile.put("lastName", lastName);
				profile.put("email", email);
				profile.put("login", login);
				putIfPresent(profile, "secondEmail", additional.get("secondEmail"));
				putIfPresent(profile, "mobilePhone", additional.get("mobilePhone"));
				putIfPresent(profile, "primaryPhone", additional.get("primaryPhone"));

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("profile", profile);

				String url = baseUrl + "/users";
				boolean activate = toBoolean(additional.get("activate"), true);
				if (activate) {
					url += "?activate=true";
				}

				HttpResponse<String> response = post(url, body, headers);
				return toResult(response);
			}
			case "delete": {
				String userId = context.getParameter("userId", "");
				// Deactivate first, then delete
				post(baseUrl + "/users/" + encode(userId) + "/lifecycle/deactivate", Map.of(), headers);
				HttpResponse<String> response = delete(baseUrl + "/users/" + encode(userId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String userId = context.getParameter("userId", "");
				HttpResponse<String> response = get(baseUrl + "/users/" + encode(userId), headers);
				return toResult(response);
			}
			case "getAll": {
				String search = context.getParameter("userSearch", "");
				int limit = toInt(context.getParameter("userLimit", 200), 200);
				Map<String, Object> queryParams = new LinkedHashMap<>();
				queryParams.put("limit", limit);
				if (!search.isEmpty()) {
					queryParams.put("filter", search);
				}
				String url = buildUrl(baseUrl + "/users", queryParams);
				HttpResponse<String> response = get(url, headers);
				return toArrayResult(response);
			}
			case "update": {
				String userId = context.getParameter("userId", "");
				Map<String, Object> updateFields = context.getParameter("userUpdateFields", Map.of());

				Map<String, Object> profile = new LinkedHashMap<>();
				putIfPresent(profile, "firstName", updateFields.get("firstName"));
				putIfPresent(profile, "lastName", updateFields.get("lastName"));
				putIfPresent(profile, "email", updateFields.get("email"));
				putIfPresent(profile, "login", updateFields.get("login"));
				putIfPresent(profile, "mobilePhone", updateFields.get("mobilePhone"));
				putIfPresent(profile, "primaryPhone", updateFields.get("primaryPhone"));

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("profile", profile);

				HttpResponse<String> response = post(baseUrl + "/users/" + encode(userId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown user operation: " + operation);
		}
	}

	// ========================= Group Execute =========================

	private NodeExecutionResult executeGroup(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "get");

		switch (operation) {
			case "create": {
				String name = context.getParameter("groupName", "");
				String description = context.getParameter("groupDescription", "");

				Map<String, Object> profile = new LinkedHashMap<>();
				profile.put("name", name);
				putIfPresent(profile, "description", description);

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("profile", profile);

				HttpResponse<String> response = post(baseUrl + "/groups", body, headers);
				return toResult(response);
			}
			case "delete": {
				String groupId = context.getParameter("groupId", "");
				HttpResponse<String> response = delete(baseUrl + "/groups/" + encode(groupId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String groupId = context.getParameter("groupId", "");
				HttpResponse<String> response = get(baseUrl + "/groups/" + encode(groupId), headers);
				return toResult(response);
			}
			case "getAll": {
				String search = context.getParameter("groupSearch", "");
				int limit = toInt(context.getParameter("groupLimit", 200), 200);
				Map<String, Object> queryParams = new LinkedHashMap<>();
				queryParams.put("limit", limit);
				if (!search.isEmpty()) {
					queryParams.put("q", search);
				}
				String url = buildUrl(baseUrl + "/groups", queryParams);
				HttpResponse<String> response = get(url, headers);
				return toArrayResult(response);
			}
			case "addUser": {
				String groupId = context.getParameter("groupId", "");
				String userId = context.getParameter("groupUserId", "");
				HttpResponse<String> response = put(
					baseUrl + "/groups/" + encode(groupId) + "/users/" + encode(userId), Map.of(), headers);
				return toDeleteResult(response);  // Returns 204 No Content on success
			}
			case "removeUser": {
				String groupId = context.getParameter("groupId", "");
				String userId = context.getParameter("groupUserId", "");
				HttpResponse<String> response = delete(
					baseUrl + "/groups/" + encode(groupId) + "/users/" + encode(userId), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown group operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private String getApiBaseUrl(Map<String, Object> credentials) {
		String domain = String.valueOf(credentials.getOrDefault("domain", ""));
		if (domain.startsWith("http")) {
			if (domain.endsWith("/")) {
				domain = domain.substring(0, domain.length() - 1);
			}
			return domain + "/api/v1";
		}
		return "https://" + domain + "/api/v1";
	}

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		String apiKey = String.valueOf(credentials.getOrDefault("apiKey", ""));
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "SSWS " + apiKey);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
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
		return NodeExecutionResult.error("Okta API error (HTTP " + response.statusCode() + "): " + body);
	}
}
