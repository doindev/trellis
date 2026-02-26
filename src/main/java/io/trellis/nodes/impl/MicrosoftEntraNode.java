package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Microsoft Entra ID — manage users and groups via the Microsoft Graph API.
 */
@Node(
		type = "microsoftEntra",
		displayName = "Microsoft Entra ID",
		description = "Manage users and groups in Microsoft Entra ID (Azure AD)",
		category = "Microsoft",
		icon = "microsoftEntra",
		credentials = {"microsoftEntraOAuth2Api"}
)
public class MicrosoftEntraNode extends AbstractApiNode {

	private static final String BASE_URL = "https://graph.microsoft.com/v1.0";

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

		// User > Create parameters
		params.add(NodeParameter.builder()
				.name("displayName").displayName("Display Name")
				.type(ParameterType.STRING).required(true)
				.description("The display name of the user.")
				.placeHolder("John Doe")
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("mailNickname").displayName("Mail Nickname")
				.type(ParameterType.STRING).required(true)
				.description("The mail alias for the user.")
				.placeHolder("johnd")
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("userPrincipalName").displayName("User Principal Name")
				.type(ParameterType.STRING).required(true)
				.description("The user principal name (UPN).")
				.placeHolder("johnd@contoso.com")
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("password").displayName("Password")
				.type(ParameterType.STRING).required(true)
				.typeOptions(Map.of("password", true))
				.description("The password for the user.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("accountEnabled").displayName("Account Enabled")
				.type(ParameterType.BOOLEAN).defaultValue(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("userAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("givenName").displayName("First Name")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("surname").displayName("Last Name")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("jobTitle").displayName("Job Title")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("department").displayName("Department")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("officeLocation").displayName("Office Location")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("mobilePhone").displayName("Mobile Phone")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("usageLocation").displayName("Usage Location")
								.type(ParameterType.STRING)
								.description("Two-letter country code (ISO 3166).").build(),
						NodeParameter.builder().name("forceChangePasswordNextSignIn").displayName("Force Password Change")
								.type(ParameterType.BOOLEAN).defaultValue(true).build()
				)).build());

		// User > Get / Delete / Update: userId
		params.add(NodeParameter.builder()
				.name("userId").displayName("User ID")
				.type(ParameterType.STRING).required(true)
				.description("The user ID or user principal name.")
				.placeHolder("johnd@contoso.com")
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("get", "delete", "update"))))
				.build());

		// User > Update fields
		params.add(NodeParameter.builder()
				.name("userUpdateFields").displayName("Update Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("update"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("displayName").displayName("Display Name")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("givenName").displayName("First Name")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("surname").displayName("Last Name")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("jobTitle").displayName("Job Title")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("department").displayName("Department")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("officeLocation").displayName("Office Location")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("mobilePhone").displayName("Mobile Phone")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("accountEnabled").displayName("Account Enabled")
								.type(ParameterType.BOOLEAN).build()
				)).build());

		// Group > Create parameters
		params.add(NodeParameter.builder()
				.name("groupDisplayName").displayName("Display Name")
				.type(ParameterType.STRING).required(true)
				.description("The display name for the group.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("groupMailNickname").displayName("Mail Nickname")
				.type(ParameterType.STRING).required(true)
				.description("The mail alias for the group.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("mailEnabled").displayName("Mail Enabled")
				.type(ParameterType.BOOLEAN).defaultValue(false)
				.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("securityEnabled").displayName("Security Enabled")
				.type(ParameterType.BOOLEAN).defaultValue(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("groupAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("description").displayName("Description")
								.type(ParameterType.STRING).typeOptions(Map.of("rows", 3)).build(),
						NodeParameter.builder().name("groupTypes").displayName("Group Types")
								.type(ParameterType.STRING)
								.description("Comma-separated group types (e.g., Unified for Microsoft 365 groups).").build(),
						NodeParameter.builder().name("visibility").displayName("Visibility")
								.type(ParameterType.OPTIONS)
								.options(List.of(
										ParameterOption.builder().name("Public").value("Public").build(),
										ParameterOption.builder().name("Private").value("Private").build(),
										ParameterOption.builder().name("Hidden Membership").value("HiddenMembership").build()
								)).build()
				)).build());

		// Group > Get / Delete / Update: groupId
		params.add(NodeParameter.builder()
				.name("groupId").displayName("Group ID")
				.type(ParameterType.STRING).required(true)
				.description("The group ID.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("get", "delete", "update"))))
				.build());

		// Group > Update fields
		params.add(NodeParameter.builder()
				.name("groupUpdateFields").displayName("Update Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("update"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("displayName").displayName("Display Name")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("description").displayName("Description")
								.type(ParameterType.STRING).typeOptions(Map.of("rows", 3)).build(),
						NodeParameter.builder().name("visibility").displayName("Visibility")
								.type(ParameterType.OPTIONS)
								.options(List.of(
										ParameterOption.builder().name("Public").value("Public").build(),
										ParameterOption.builder().name("Private").value("Private").build(),
										ParameterOption.builder().name("Hidden Membership").value("HiddenMembership").build()
								)).build()
				)).build());

		// GetAll: limit and filter
		params.add(NodeParameter.builder()
				.name("limit").displayName("Limit")
				.type(ParameterType.NUMBER).defaultValue(100)
				.description("Maximum number of results to return.")
				.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
				.build());

		params.add(NodeParameter.builder()
				.name("filter").displayName("Filter")
				.type(ParameterType.STRING)
				.description("OData filter expression (e.g., startswith(displayName,'J')).")
				.placeHolder("startswith(displayName,'J')")
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
			return handleError(context, "Microsoft Entra ID error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeUser(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		switch (operation) {
			case "create": {
				String displayName = context.getParameter("displayName", "");
				String mailNickname = context.getParameter("mailNickname", "");
				String userPrincipalName = context.getParameter("userPrincipalName", "");
				String password = context.getParameter("password", "");
				boolean accountEnabled = toBoolean(context.getParameter("accountEnabled", true), true);
				Map<String, Object> additionalFields = context.getParameter("userAdditionalFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("displayName", displayName);
				body.put("mailNickname", mailNickname);
				body.put("userPrincipalName", userPrincipalName);
				body.put("accountEnabled", accountEnabled);

				Map<String, Object> passwordProfile = new LinkedHashMap<>();
				passwordProfile.put("password", password);
				if (additionalFields.get("forceChangePasswordNextSignIn") != null) {
					passwordProfile.put("forceChangePasswordNextSignIn",
							toBoolean(additionalFields.get("forceChangePasswordNextSignIn"), true));
				} else {
					passwordProfile.put("forceChangePasswordNextSignIn", true);
				}
				body.put("passwordProfile", passwordProfile);

				if (additionalFields.get("givenName") != null) body.put("givenName", additionalFields.get("givenName"));
				if (additionalFields.get("surname") != null) body.put("surname", additionalFields.get("surname"));
				if (additionalFields.get("jobTitle") != null) body.put("jobTitle", additionalFields.get("jobTitle"));
				if (additionalFields.get("department") != null) body.put("department", additionalFields.get("department"));
				if (additionalFields.get("officeLocation") != null) body.put("officeLocation", additionalFields.get("officeLocation"));
				if (additionalFields.get("mobilePhone") != null) body.put("mobilePhone", additionalFields.get("mobilePhone"));
				if (additionalFields.get("usageLocation") != null) body.put("usageLocation", additionalFields.get("usageLocation"));

				HttpResponse<String> response = post(BASE_URL + "/users", body, headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "delete": {
				String userId = context.getParameter("userId", "");
				HttpResponse<String> response = delete(BASE_URL + "/users/" + encode(userId), headers);
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "deleted", userId))));
				}
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "get": {
				String userId = context.getParameter("userId", "");
				HttpResponse<String> response = get(BASE_URL + "/users/" + encode(userId), headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 100), 100);
				String filter = context.getParameter("filter", "");

				StringBuilder url = new StringBuilder(BASE_URL + "/users?$top=" + limit);
				if (filter != null && !filter.isEmpty()) {
					url.append("&$filter=").append(encode(filter));
				}

				HttpResponse<String> response = get(url.toString(), headers);
				Map<String, Object> result = parseResponse(response);

				Object value = result.get("value");
				if (value instanceof List) {
					List<Map<String, Object>> userItems = new ArrayList<>();
					for (Object user : (List<?>) value) {
						if (user instanceof Map) {
							userItems.add(wrapInJson(user));
						}
					}
					return userItems.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(userItems);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "update": {
				String userId = context.getParameter("userId", "");
				Map<String, Object> updateFields = context.getParameter("userUpdateFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				if (updateFields.get("displayName") != null) body.put("displayName", updateFields.get("displayName"));
				if (updateFields.get("givenName") != null) body.put("givenName", updateFields.get("givenName"));
				if (updateFields.get("surname") != null) body.put("surname", updateFields.get("surname"));
				if (updateFields.get("jobTitle") != null) body.put("jobTitle", updateFields.get("jobTitle"));
				if (updateFields.get("department") != null) body.put("department", updateFields.get("department"));
				if (updateFields.get("officeLocation") != null) body.put("officeLocation", updateFields.get("officeLocation"));
				if (updateFields.get("mobilePhone") != null) body.put("mobilePhone", updateFields.get("mobilePhone"));
				if (updateFields.get("accountEnabled") != null) body.put("accountEnabled", toBoolean(updateFields.get("accountEnabled"), true));

				HttpResponse<String> response = patch(BASE_URL + "/users/" + encode(userId), body, headers);
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					// PATCH returns 204 No Content on success, re-fetch the user
					HttpResponse<String> getResponse = get(BASE_URL + "/users/" + encode(userId), headers);
					Map<String, Object> result = parseResponse(getResponse);
					return NodeExecutionResult.success(List.of(wrapInJson(result)));
				}
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
				String displayName = context.getParameter("groupDisplayName", "");
				String mailNickname = context.getParameter("groupMailNickname", "");
				boolean mailEnabled = toBoolean(context.getParameter("mailEnabled", false), false);
				boolean securityEnabled = toBoolean(context.getParameter("securityEnabled", true), true);
				Map<String, Object> additionalFields = context.getParameter("groupAdditionalFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("displayName", displayName);
				body.put("mailNickname", mailNickname);
				body.put("mailEnabled", mailEnabled);
				body.put("securityEnabled", securityEnabled);

				if (additionalFields.get("description") != null) {
					body.put("description", additionalFields.get("description"));
				}
				if (additionalFields.get("groupTypes") != null) {
					String groupTypesStr = (String) additionalFields.get("groupTypes");
					body.put("groupTypes", Arrays.asList(groupTypesStr.split(",")));
				}
				if (additionalFields.get("visibility") != null) {
					body.put("visibility", additionalFields.get("visibility"));
				}

				HttpResponse<String> response = post(BASE_URL + "/groups", body, headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "delete": {
				String groupId = context.getParameter("groupId", "");
				HttpResponse<String> response = delete(BASE_URL + "/groups/" + encode(groupId), headers);
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "deleted", groupId))));
				}
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "get": {
				String groupId = context.getParameter("groupId", "");
				HttpResponse<String> response = get(BASE_URL + "/groups/" + encode(groupId), headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 100), 100);
				String filter = context.getParameter("filter", "");

				StringBuilder url = new StringBuilder(BASE_URL + "/groups?$top=" + limit);
				if (filter != null && !filter.isEmpty()) {
					url.append("&$filter=").append(encode(filter));
				}

				HttpResponse<String> response = get(url.toString(), headers);
				Map<String, Object> result = parseResponse(response);

				Object value = result.get("value");
				if (value instanceof List) {
					List<Map<String, Object>> groupItems = new ArrayList<>();
					for (Object group : (List<?>) value) {
						if (group instanceof Map) {
							groupItems.add(wrapInJson(group));
						}
					}
					return groupItems.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(groupItems);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "update": {
				String groupId = context.getParameter("groupId", "");
				Map<String, Object> updateFields = context.getParameter("groupUpdateFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				if (updateFields.get("displayName") != null) body.put("displayName", updateFields.get("displayName"));
				if (updateFields.get("description") != null) body.put("description", updateFields.get("description"));
				if (updateFields.get("visibility") != null) body.put("visibility", updateFields.get("visibility"));

				HttpResponse<String> response = patch(BASE_URL + "/groups/" + encode(groupId), body, headers);
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					HttpResponse<String> getResponse = get(BASE_URL + "/groups/" + encode(groupId), headers);
					Map<String, Object> result = parseResponse(getResponse);
					return NodeExecutionResult.success(List.of(wrapInJson(result)));
				}
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
