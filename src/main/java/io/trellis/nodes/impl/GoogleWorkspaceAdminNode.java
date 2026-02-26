package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Google Workspace Admin — manage users and groups via the Google Admin Directory API.
 */
@Node(
		type = "gSuiteAdmin",
		displayName = "Google Workspace Admin",
		description = "Manage users and groups in Google Workspace",
		category = "Google",
		icon = "googleWorkspaceAdmin",
		credentials = {"gSuiteAdminOAuth2Api"}
)
public class GoogleWorkspaceAdminNode extends AbstractApiNode {

	private static final String BASE_URL = "https://admin.googleapis.com/admin/directory/v1";

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

		params.add(NodeParameter.builder()
				.name("resource").displayName("Resource")
				.type(ParameterType.OPTIONS).required(true).defaultValue("user")
				.noDataExpression(true)
				.options(List.of(
						ParameterOption.builder().name("User").value("user")
								.description("Manage users").build(),
						ParameterOption.builder().name("Group").value("group")
								.description("Manage groups").build()
				)).build());

		// User operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").description("Create a user").build(),
						ParameterOption.builder().name("Delete").value("delete").description("Delete a user").build(),
						ParameterOption.builder().name("Get").value("get").description("Get a user").build(),
						ParameterOption.builder().name("Get All").value("getAll").description("Get all users").build(),
						ParameterOption.builder().name("Update").value("update").description("Update a user").build()
				)).build());

		// Group operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("group"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").description("Create a group").build(),
						ParameterOption.builder().name("Delete").value("delete").description("Delete a group").build(),
						ParameterOption.builder().name("Get").value("get").description("Get a group").build(),
						ParameterOption.builder().name("Get All").value("getAll").description("Get all groups").build(),
						ParameterOption.builder().name("Update").value("update").description("Update a group").build()
				)).build());

		// Domain (used by user getAll and group getAll)
		params.add(NodeParameter.builder()
				.name("domain").displayName("Domain")
				.type(ParameterType.STRING).required(true)
				.description("The domain name (e.g., example.com).")
				.placeHolder("example.com")
				.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
				.build());

		// User > Create parameters
		params.add(NodeParameter.builder()
				.name("primaryEmail").displayName("Primary Email")
				.type(ParameterType.STRING).required(true)
				.description("The user's primary email address.")
				.placeHolder("user@example.com")
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("firstName").displayName("First Name")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("lastName").displayName("Last Name")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("password").displayName("Password")
				.type(ParameterType.STRING).required(true)
				.typeOptions(Map.of("password", true))
				.description("Password for the new user (min 8 characters).")
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("userAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("orgUnitPath").displayName("Org Unit Path")
								.type(ParameterType.STRING).defaultValue("/")
								.description("The organizational unit path.").build(),
						NodeParameter.builder().name("changePasswordAtNextLogin").displayName("Change Password at Next Login")
								.type(ParameterType.BOOLEAN).defaultValue(false).build(),
						NodeParameter.builder().name("suspended").displayName("Suspended")
								.type(ParameterType.BOOLEAN).defaultValue(false).build(),
						NodeParameter.builder().name("phone").displayName("Phone Number")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("jobTitle").displayName("Job Title")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("department").displayName("Department")
								.type(ParameterType.STRING).build()
				)).build());

		// User > Get / Delete / Update: userKey
		params.add(NodeParameter.builder()
				.name("userKey").displayName("User Key")
				.type(ParameterType.STRING).required(true)
				.description("The user's primary email, alias, or unique ID.")
				.placeHolder("user@example.com")
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("get", "delete", "update"))))
				.build());

		// User > Update: update fields
		params.add(NodeParameter.builder()
				.name("userUpdateFields").displayName("Update Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("update"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("firstName").displayName("First Name")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("lastName").displayName("Last Name")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("password").displayName("Password")
								.type(ParameterType.STRING).typeOptions(Map.of("password", true)).build(),
						NodeParameter.builder().name("suspended").displayName("Suspended")
								.type(ParameterType.BOOLEAN).build(),
						NodeParameter.builder().name("orgUnitPath").displayName("Org Unit Path")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("phone").displayName("Phone Number")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("jobTitle").displayName("Job Title")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("department").displayName("Department")
								.type(ParameterType.STRING).build()
				)).build());

		// Group > Create parameters
		params.add(NodeParameter.builder()
				.name("groupEmail").displayName("Group Email")
				.type(ParameterType.STRING).required(true)
				.description("The group's email address.")
				.placeHolder("group@example.com")
				.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("groupName").displayName("Group Name")
				.type(ParameterType.STRING).required(true)
				.description("The display name of the group.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("groupAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("description").displayName("Description")
								.type(ParameterType.STRING).typeOptions(Map.of("rows", 3)).build()
				)).build());

		// Group > Get / Delete / Update: groupKey
		params.add(NodeParameter.builder()
				.name("groupKey").displayName("Group Key")
				.type(ParameterType.STRING).required(true)
				.description("The group's email address or unique ID.")
				.placeHolder("group@example.com")
				.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("get", "delete", "update"))))
				.build());

		// Group > Update fields
		params.add(NodeParameter.builder()
				.name("groupUpdateFields").displayName("Update Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("update"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("name").displayName("Group Name")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("description").displayName("Description")
								.type(ParameterType.STRING).typeOptions(Map.of("rows", 3)).build(),
						NodeParameter.builder().name("email").displayName("Email")
								.type(ParameterType.STRING).build()
				)).build());

		// GetAll: limit
		params.add(NodeParameter.builder()
				.name("limit").displayName("Limit")
				.type(ParameterType.NUMBER).defaultValue(100)
				.description("Maximum number of results to return.")
				.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
				.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "user");
		Map<String, Object> credentials = context.getCredentials();

		try {
			return switch (resource) {
				case "user" -> executeUser(context, credentials);
				case "group" -> executeGroup(context, credentials);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Google Workspace Admin error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeUser(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		switch (operation) {
			case "create": {
				String primaryEmail = context.getParameter("primaryEmail", "");
				String firstName = context.getParameter("firstName", "");
				String lastName = context.getParameter("lastName", "");
				String password = context.getParameter("password", "");
				Map<String, Object> additionalFields = context.getParameter("userAdditionalFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("primaryEmail", primaryEmail);
				body.put("name", Map.of("givenName", firstName, "familyName", lastName));
				body.put("password", password);

				if (additionalFields.get("orgUnitPath") != null) {
					body.put("orgUnitPath", additionalFields.get("orgUnitPath"));
				}
				if (additionalFields.get("changePasswordAtNextLogin") != null) {
					body.put("changePasswordAtNextLogin", toBoolean(additionalFields.get("changePasswordAtNextLogin"), false));
				}
				if (additionalFields.get("suspended") != null) {
					body.put("suspended", toBoolean(additionalFields.get("suspended"), false));
				}
				if (additionalFields.get("phone") != null) {
					body.put("phones", List.of(Map.of("value", additionalFields.get("phone"), "type", "work")));
				}
				if (additionalFields.get("jobTitle") != null || additionalFields.get("department") != null) {
					Map<String, Object> org = new LinkedHashMap<>();
					if (additionalFields.get("jobTitle") != null) {
						org.put("title", additionalFields.get("jobTitle"));
					}
					if (additionalFields.get("department") != null) {
						org.put("department", additionalFields.get("department"));
					}
					body.put("organizations", List.of(org));
				}

				HttpResponse<String> response = post(BASE_URL + "/users", body, headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "delete": {
				String userKey = context.getParameter("userKey", "");
				HttpResponse<String> response = delete(BASE_URL + "/users/" + encode(userKey), headers);
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "deleted", userKey))));
				}
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "get": {
				String userKey = context.getParameter("userKey", "");
				HttpResponse<String> response = get(BASE_URL + "/users/" + encode(userKey), headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "getAll": {
				String domain = context.getParameter("domain", "");
				int limit = toInt(context.getParameter("limit", 100), 100);
				String url = BASE_URL + "/users?domain=" + encode(domain) + "&maxResults=" + limit;

				HttpResponse<String> response = get(url, headers);
				Map<String, Object> result = parseResponse(response);

				Object users = result.get("users");
				if (users instanceof List) {
					List<Map<String, Object>> userItems = new ArrayList<>();
					for (Object user : (List<?>) users) {
						if (user instanceof Map) {
							userItems.add(wrapInJson(user));
						}
					}
					return userItems.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(userItems);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "update": {
				String userKey = context.getParameter("userKey", "");
				Map<String, Object> updateFields = context.getParameter("userUpdateFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				if (updateFields.get("firstName") != null || updateFields.get("lastName") != null) {
					Map<String, Object> name = new LinkedHashMap<>();
					if (updateFields.get("firstName") != null) {
						name.put("givenName", updateFields.get("firstName"));
					}
					if (updateFields.get("lastName") != null) {
						name.put("familyName", updateFields.get("lastName"));
					}
					body.put("name", name);
				}
				if (updateFields.get("password") != null) {
					body.put("password", updateFields.get("password"));
				}
				if (updateFields.get("suspended") != null) {
					body.put("suspended", toBoolean(updateFields.get("suspended"), false));
				}
				if (updateFields.get("orgUnitPath") != null) {
					body.put("orgUnitPath", updateFields.get("orgUnitPath"));
				}
				if (updateFields.get("phone") != null) {
					body.put("phones", List.of(Map.of("value", updateFields.get("phone"), "type", "work")));
				}
				if (updateFields.get("jobTitle") != null || updateFields.get("department") != null) {
					Map<String, Object> org = new LinkedHashMap<>();
					if (updateFields.get("jobTitle") != null) {
						org.put("title", updateFields.get("jobTitle"));
					}
					if (updateFields.get("department") != null) {
						org.put("department", updateFields.get("department"));
					}
					body.put("organizations", List.of(org));
				}

				HttpResponse<String> response = put(BASE_URL + "/users/" + encode(userKey), body, headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			default:
				return NodeExecutionResult.error("Unknown user operation: " + operation);
		}
	}

	private NodeExecutionResult executeGroup(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		switch (operation) {
			case "create": {
				String groupEmail = context.getParameter("groupEmail", "");
				String groupName = context.getParameter("groupName", "");
				Map<String, Object> additionalFields = context.getParameter("groupAdditionalFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("email", groupEmail);
				body.put("name", groupName);

				if (additionalFields.get("description") != null) {
					body.put("description", additionalFields.get("description"));
				}

				HttpResponse<String> response = post(BASE_URL + "/groups", body, headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "delete": {
				String groupKey = context.getParameter("groupKey", "");
				HttpResponse<String> response = delete(BASE_URL + "/groups/" + encode(groupKey), headers);
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "deleted", groupKey))));
				}
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "get": {
				String groupKey = context.getParameter("groupKey", "");
				HttpResponse<String> response = get(BASE_URL + "/groups/" + encode(groupKey), headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "getAll": {
				String domain = context.getParameter("domain", "");
				int limit = toInt(context.getParameter("limit", 100), 100);
				String url = BASE_URL + "/groups?domain=" + encode(domain) + "&maxResults=" + limit;

				HttpResponse<String> response = get(url, headers);
				Map<String, Object> result = parseResponse(response);

				Object groups = result.get("groups");
				if (groups instanceof List) {
					List<Map<String, Object>> groupItems = new ArrayList<>();
					for (Object group : (List<?>) groups) {
						if (group instanceof Map) {
							groupItems.add(wrapInJson(group));
						}
					}
					return groupItems.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(groupItems);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "update": {
				String groupKey = context.getParameter("groupKey", "");
				Map<String, Object> updateFields = context.getParameter("groupUpdateFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				if (updateFields.get("name") != null) {
					body.put("name", updateFields.get("name"));
				}
				if (updateFields.get("description") != null) {
					body.put("description", updateFields.get("description"));
				}
				if (updateFields.get("email") != null) {
					body.put("email", updateFields.get("email"));
				}

				HttpResponse<String> response = put(BASE_URL + "/groups/" + encode(groupKey), body, headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			default:
				return NodeExecutionResult.error("Unknown group operation: " + operation);
		}
	}

	private Map<String, String> getAuthHeaders(String accessToken) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}
}
