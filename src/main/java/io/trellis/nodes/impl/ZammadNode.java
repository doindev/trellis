package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

import lombok.extern.slf4j.Slf4j;

/**
 * Zammad — manage tickets, users, organizations and groups via the Zammad REST API v1.
 */
@Slf4j
@Node(
		type = "zammad",
		displayName = "Zammad",
		description = "Manage tickets in Zammad helpdesk",
		category = "Customer Support",
		icon = "zammad",
		credentials = {"zammadApi"}
)
public class ZammadNode extends AbstractApiNode {

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		// Resource selector
		params.add(NodeParameter.builder()
				.name("resource").displayName("Resource")
				.type(ParameterType.OPTIONS).required(true).defaultValue("ticket")
				.noDataExpression(true)
				.options(List.of(
						ParameterOption.builder().name("Ticket").value("ticket").description("Manage tickets").build(),
						ParameterOption.builder().name("User").value("user").description("Manage users").build(),
						ParameterOption.builder().name("Organization").value("organization").description("Manage organizations").build(),
						ParameterOption.builder().name("Group").value("group").description("Manage groups").build()
				)).build());

		// Operation selectors
		for (String res : List.of("ticket", "user", "organization", "group")) {
			params.add(NodeParameter.builder()
					.name("operation").displayName("Operation")
					.type(ParameterType.OPTIONS).required(true).defaultValue("create")
					.displayOptions(Map.of("show", Map.of("resource", List.of(res))))
					.options(List.of(
							ParameterOption.builder().name("Create").value("create").build(),
							ParameterOption.builder().name("Get").value("get").build(),
							ParameterOption.builder().name("Get Many").value("getAll").build(),
							ParameterOption.builder().name("Delete").value("delete").build()
					)).build());
		}

		// Ticket parameters
		addTicketParameters(params);
		// User parameters
		addUserParameters(params);
		// Organization parameters
		addOrganizationParameters(params);
		// Group parameters
		addGroupParameters(params);

		// Common getAll
		for (String res : List.of("ticket", "user", "organization", "group")) {
			params.add(NodeParameter.builder()
					.name("returnAll").displayName("Return All")
					.type(ParameterType.BOOLEAN).defaultValue(false)
					.displayOptions(Map.of("show", Map.of("resource", List.of(res), "operation", List.of("getAll"))))
					.build());
			params.add(NodeParameter.builder()
					.name("limit").displayName("Limit")
					.type(ParameterType.NUMBER).defaultValue(50)
					.displayOptions(Map.of("show", Map.of("resource", List.of(res), "operation", List.of("getAll"))))
					.build());
		}

		return params;
	}

	private void addTicketParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
				.name("ticketId").displayName("Ticket ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("get", "delete"))))
				.build());

		params.add(NodeParameter.builder()
				.name("ticketTitle").displayName("Title")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("ticketGroup").displayName("Group")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("ticketCustomer").displayName("Customer Email")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("ticketAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("state").displayName("State")
								.type(ParameterType.OPTIONS)
								.options(List.of(
										ParameterOption.builder().name("New").value("new").build(),
										ParameterOption.builder().name("Open").value("open").build(),
										ParameterOption.builder().name("Pending Reminder").value("pending reminder").build(),
										ParameterOption.builder().name("Pending Close").value("pending close").build(),
										ParameterOption.builder().name("Closed").value("closed").build()
								)).build(),
						NodeParameter.builder().name("priority").displayName("Priority")
								.type(ParameterType.OPTIONS)
								.options(List.of(
										ParameterOption.builder().name("1 Low").value("1 low").build(),
										ParameterOption.builder().name("2 Normal").value("2 normal").build(),
										ParameterOption.builder().name("3 High").value("3 high").build()
								)).build(),
						NodeParameter.builder().name("body").displayName("Article Body")
								.type(ParameterType.STRING).typeOptions(Map.of("rows", 4)).build(),
						NodeParameter.builder().name("subject").displayName("Article Subject")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("type").displayName("Article Type")
								.type(ParameterType.OPTIONS)
								.options(List.of(
										ParameterOption.builder().name("Email").value("email").build(),
										ParameterOption.builder().name("Note").value("note").build(),
										ParameterOption.builder().name("Phone").value("phone").build(),
										ParameterOption.builder().name("Web").value("web").build()
								)).build()
				)).build());
	}

	private void addUserParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
				.name("zammadUserId").displayName("User ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("get", "delete"))))
				.build());

		params.add(NodeParameter.builder()
				.name("zammadUserFirstname").displayName("First Name")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("zammadUserLastname").displayName("Last Name")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("zammadUserEmail").displayName("Email")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("zammadUserAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("phone").displayName("Phone").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("organization").displayName("Organization").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("note").displayName("Note").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("roles").displayName("Roles")
								.type(ParameterType.STRING).description("Comma-separated role names").build()
				)).build());
	}

	private void addOrganizationParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
				.name("organizationId").displayName("Organization ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("organization"), "operation", List.of("get", "delete"))))
				.build());

		params.add(NodeParameter.builder()
				.name("organizationName").displayName("Name")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("organization"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("organizationAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("organization"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("domain").displayName("Domain").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("note").displayName("Note").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("shared").displayName("Shared").type(ParameterType.BOOLEAN).build(),
						NodeParameter.builder().name("active").displayName("Active").type(ParameterType.BOOLEAN).build()
				)).build());
	}

	private void addGroupParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
				.name("groupId").displayName("Group ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("get", "delete"))))
				.build());

		params.add(NodeParameter.builder()
				.name("groupName").displayName("Name")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("groupAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("note").displayName("Note").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("active").displayName("Active").type(ParameterType.BOOLEAN).defaultValue(true).build(),
						NodeParameter.builder().name("emailAddress").displayName("Email Address").type(ParameterType.STRING).build()
				)).build());
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "ticket");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String baseUrl = getBaseUrl(credentials);
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (resource) {
				case "ticket" -> executeTicket(context, baseUrl, headers);
				case "user" -> executeUser(context, baseUrl, headers);
				case "organization" -> executeOrganization(context, baseUrl, headers);
				case "group" -> executeGroup(context, baseUrl, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Zammad API error: " + e.getMessage(), e);
		}
	}

	// ========================= Ticket =========================

	private NodeExecutionResult executeTicket(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "create");

		switch (operation) {
			case "create": {
				String title = context.getParameter("ticketTitle", "");
				String group = context.getParameter("ticketGroup", "");
				String customer = context.getParameter("ticketCustomer", "");
				Map<String, Object> additional = context.getParameter("ticketAdditionalFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", title);
				body.put("group", group);
				body.put("customer", customer);
				putIfPresent(body, "state", additional.get("state"));
				putIfPresent(body, "priority", additional.get("priority"));

				// Include article if body is provided
				if (additional.get("body") != null && !String.valueOf(additional.get("body")).isEmpty()) {
					Map<String, Object> article = new LinkedHashMap<>();
					article.put("body", additional.get("body"));
					putIfPresent(article, "subject", additional.get("subject"));
					putIfPresent(article, "type", additional.get("type"));
					body.put("article", article);
				}

				HttpResponse<String> response = post(baseUrl + "/tickets", body, headers);
				return toResult(response);
			}
			case "get": {
				String id = context.getParameter("ticketId", "");
				HttpResponse<String> response = get(baseUrl + "/tickets/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				HttpResponse<String> response = get(baseUrl + "/tickets?per_page=" + limit + "&page=1", headers);
				return toArrayResult(response);
			}
			case "delete": {
				String id = context.getParameter("ticketId", "");
				HttpResponse<String> response = delete(baseUrl + "/tickets/" + encode(id), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown ticket operation: " + operation);
		}
	}

	// ========================= User =========================

	private NodeExecutionResult executeUser(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "create");

		switch (operation) {
			case "create": {
				Map<String, Object> additional = context.getParameter("zammadUserAdditionalFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("firstname", context.getParameter("zammadUserFirstname", ""));
				body.put("lastname", context.getParameter("zammadUserLastname", ""));
				body.put("email", context.getParameter("zammadUserEmail", ""));
				putIfPresent(body, "phone", additional.get("phone"));
				putIfPresent(body, "organization", additional.get("organization"));
				putIfPresent(body, "note", additional.get("note"));
				if (additional.get("roles") != null && !String.valueOf(additional.get("roles")).isEmpty()) {
					body.put("roles", parseCsv(String.valueOf(additional.get("roles"))));
				}

				HttpResponse<String> response = post(baseUrl + "/users", body, headers);
				return toResult(response);
			}
			case "get": {
				String id = context.getParameter("zammadUserId", "");
				HttpResponse<String> response = get(baseUrl + "/users/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				HttpResponse<String> response = get(baseUrl + "/users?per_page=" + limit + "&page=1", headers);
				return toArrayResult(response);
			}
			case "delete": {
				String id = context.getParameter("zammadUserId", "");
				HttpResponse<String> response = delete(baseUrl + "/users/" + encode(id), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown user operation: " + operation);
		}
	}

	// ========================= Organization =========================

	private NodeExecutionResult executeOrganization(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "create");

		switch (operation) {
			case "create": {
				Map<String, Object> additional = context.getParameter("organizationAdditionalFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", context.getParameter("organizationName", ""));
				putIfPresent(body, "domain", additional.get("domain"));
				putIfPresent(body, "note", additional.get("note"));
				if (additional.get("shared") != null) body.put("shared", toBoolean(additional.get("shared"), true));
				if (additional.get("active") != null) body.put("active", toBoolean(additional.get("active"), true));

				HttpResponse<String> response = post(baseUrl + "/organizations", body, headers);
				return toResult(response);
			}
			case "get": {
				String id = context.getParameter("organizationId", "");
				HttpResponse<String> response = get(baseUrl + "/organizations/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				HttpResponse<String> response = get(baseUrl + "/organizations?per_page=" + limit + "&page=1", headers);
				return toArrayResult(response);
			}
			case "delete": {
				String id = context.getParameter("organizationId", "");
				HttpResponse<String> response = delete(baseUrl + "/organizations/" + encode(id), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown organization operation: " + operation);
		}
	}

	// ========================= Group =========================

	private NodeExecutionResult executeGroup(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "create");

		switch (operation) {
			case "create": {
				Map<String, Object> additional = context.getParameter("groupAdditionalFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", context.getParameter("groupName", ""));
				putIfPresent(body, "note", additional.get("note"));
				if (additional.get("active") != null) body.put("active", toBoolean(additional.get("active"), true));
				putIfPresent(body, "email_address", additional.get("emailAddress"));

				HttpResponse<String> response = post(baseUrl + "/groups", body, headers);
				return toResult(response);
			}
			case "get": {
				String id = context.getParameter("groupId", "");
				HttpResponse<String> response = get(baseUrl + "/groups/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				HttpResponse<String> response = get(baseUrl + "/groups?per_page=" + limit + "&page=1", headers);
				return toArrayResult(response);
			}
			case "delete": {
				String id = context.getParameter("groupId", "");
				HttpResponse<String> response = delete(baseUrl + "/groups/" + encode(id), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown group operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	@Override
	protected String getBaseUrl(Map<String, Object> credentials) {
		String baseUrl = String.valueOf(credentials.getOrDefault("baseUrl", ""));
		if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		return baseUrl + "/api/v1";
	}

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");

		String authType = String.valueOf(credentials.getOrDefault("authType", "token"));
		if ("basic".equalsIgnoreCase(authType)) {
			String username = String.valueOf(credentials.getOrDefault("username", ""));
			String password = String.valueOf(credentials.getOrDefault("password", ""));
			String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
			headers.put("Authorization", "Basic " + auth);
		} else {
			String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));
			headers.put("Authorization", "Token token=" + accessToken);
		}

		return headers;
	}

	private void putIfPresent(Map<String, Object> map, String key, Object value) {
		if (value != null && !String.valueOf(value).isEmpty()) {
			map.put(key, value);
		}
	}

	private List<String> parseCsv(String value) {
		if (value == null || value.isBlank()) return List.of();
		return Arrays.stream(value.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
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
		List<Map<String, Object>> wrapped = new ArrayList<>();
		for (Map<String, Object> item : items) {
			wrapped.add(wrapInJson(item));
		}
		return wrapped.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(wrapped);
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response) {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Zammad API error (HTTP " + response.statusCode() + "): " + body);
	}
}
