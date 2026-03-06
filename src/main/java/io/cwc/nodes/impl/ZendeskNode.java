package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Zendesk Node -- manage tickets, ticket fields, users, and organizations
 * in Zendesk.
 */
@Slf4j
@Node(
	type = "zendesk",
	displayName = "Zendesk",
	description = "Manage tickets, users, and organizations in Zendesk",
	category = "Customer Support",
	icon = "zendesk",
	credentials = {"zendeskApi"}
)
public class ZendeskNode extends AbstractApiNode {

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("ticket")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Ticket").value("ticket").description("Manage tickets").build(),
				ParameterOption.builder().name("Ticket Field").value("ticketField").description("Query ticket fields").build(),
				ParameterOption.builder().name("User").value("user").description("Manage users").build(),
				ParameterOption.builder().name("Organization").value("organization").description("Manage organizations").build()
			)).build());

		// Ticket operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a ticket").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a ticket").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a ticket").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many tickets").build(),
				ParameterOption.builder().name("Recover").value("recover").description("Recover a deleted ticket").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a ticket").build()
			)).build());

		// Ticket Field operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("ticketField"))))
			.options(List.of(
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many ticket fields").build()
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
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many users").build(),
				ParameterOption.builder().name("Search").value("search").description("Search users").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a user").build()
			)).build());

		// Organization operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("organization"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an organization").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an organization").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an organization").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many organizations").build(),
				ParameterOption.builder().name("Get Related Data").value("getRelatedData").description("Get related data for an organization").build(),
				ParameterOption.builder().name("Update").value("update").description("Update an organization").build()
			)).build());

		// Common parameters
		params.add(NodeParameter.builder()
			.name("resourceId").displayName("ID")
			.type(ParameterType.STRING).required(true)
			.description("The ID of the resource.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("get", "update", "delete", "recover", "getRelatedData"))))
			.build());

		params.add(NodeParameter.builder()
			.name("ticketSubject").displayName("Subject")
			.type(ParameterType.STRING)
			.description("Subject of the ticket.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("ticketDescription").displayName("Description")
			.type(ParameterType.STRING)
			.description("Description/comment body of the ticket.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("ticketStatus").displayName("Status")
			.type(ParameterType.OPTIONS)
			.description("Status of the ticket.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create", "update"))))
			.options(List.of(
				ParameterOption.builder().name("New").value("new").build(),
				ParameterOption.builder().name("Open").value("open").build(),
				ParameterOption.builder().name("Pending").value("pending").build(),
				ParameterOption.builder().name("Hold").value("hold").build(),
				ParameterOption.builder().name("Solved").value("solved").build(),
				ParameterOption.builder().name("Closed").value("closed").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("ticketPriority").displayName("Priority")
			.type(ParameterType.OPTIONS)
			.description("Priority of the ticket.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create", "update"))))
			.options(List.of(
				ParameterOption.builder().name("Low").value("low").build(),
				ParameterOption.builder().name("Normal").value("normal").build(),
				ParameterOption.builder().name("High").value("high").build(),
				ParameterOption.builder().name("Urgent").value("urgent").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("userName").displayName("Name")
			.type(ParameterType.STRING)
			.description("Name of the user.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("userEmail").displayName("Email")
			.type(ParameterType.STRING)
			.description("Email of the user.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("orgName").displayName("Organization Name")
			.type(ParameterType.STRING)
			.description("Name of the organization.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("organization"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("searchQuery").displayName("Search Query")
			.type(ParameterType.STRING).required(true)
			.description("Search query string.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("search"))))
			.build());

		params.add(NodeParameter.builder()
			.name("additionalFields").displayName("Additional Fields (JSON)")
			.type(ParameterType.JSON).defaultValue("{}")
			.description("Additional fields as JSON.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("returnAll").displayName("Return All")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Whether to return all results.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll", "search"))))
			.build());

		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit")
			.type(ParameterType.NUMBER).defaultValue(100)
			.description("Max number of results to return.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll", "search"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "ticket");
		String operation = context.getParameter("operation", "getAll");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String subdomain = String.valueOf(credentials.getOrDefault("subdomain", ""));
			String baseUrl = "https://" + subdomain + ".zendesk.com/api/v2";
			Map<String, String> headers = authHeaders(credentials);

			return switch (resource) {
				case "ticket" -> executeTicket(context, operation, baseUrl, headers);
				case "ticketField" -> executeTicketField(context, operation, baseUrl, headers);
				case "user" -> executeUser(context, operation, baseUrl, headers);
				case "organization" -> executeOrganization(context, operation, baseUrl, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Zendesk API error: " + e.getMessage(), e);
		}
	}

	// ========================= Ticket Operations =========================

	private NodeExecutionResult executeTicket(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				Map<String, Object> ticket = buildTicketBody(context);
				Map<String, Object> body = Map.of("ticket", ticket);
				HttpResponse<String> response = post(baseUrl + "/tickets.json", body, headers);
				return toResult(response, "ticket");
			}
			case "delete": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(baseUrl + "/tickets/" + encode(id) + ".json", headers);
				return toDeleteResult(response, id);
			}
			case "get": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(baseUrl + "/tickets/" + encode(id) + ".json", headers);
				return toResult(response, "ticket");
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(baseUrl + "/tickets.json?per_page=" + limit, headers);
				return toListResult(response, "tickets");
			}
			case "recover": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = put(baseUrl + "/deleted_tickets/" + encode(id) + "/restore.json", Map.of(), headers);
				return toResult(response, null);
			}
			case "update": {
				String id = context.getParameter("resourceId", "");
				Map<String, Object> ticket = buildTicketBody(context);
				Map<String, Object> body = Map.of("ticket", ticket);
				HttpResponse<String> response = put(baseUrl + "/tickets/" + encode(id) + ".json", body, headers);
				return toResult(response, "ticket");
			}
			default:
				return NodeExecutionResult.error("Unknown ticket operation: " + operation);
		}
	}

	// ========================= Ticket Field Operations =========================

	private NodeExecutionResult executeTicketField(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		if ("getAll".equals(operation)) {
			int limit = getLimit(context);
			HttpResponse<String> response = get(baseUrl + "/ticket_fields.json?per_page=" + limit, headers);
			return toListResult(response, "ticket_fields");
		}
		return NodeExecutionResult.error("Unknown ticket field operation: " + operation);
	}

	// ========================= User Operations =========================

	private NodeExecutionResult executeUser(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				Map<String, Object> user = buildUserBody(context);
				Map<String, Object> body = Map.of("user", user);
				HttpResponse<String> response = post(baseUrl + "/users.json", body, headers);
				return toResult(response, "user");
			}
			case "delete": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(baseUrl + "/users/" + encode(id) + ".json", headers);
				return toDeleteResult(response, id);
			}
			case "get": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(baseUrl + "/users/" + encode(id) + ".json", headers);
				return toResult(response, "user");
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(baseUrl + "/users.json?per_page=" + limit, headers);
				return toListResult(response, "users");
			}
			case "search": {
				String query = context.getParameter("searchQuery", "");
				int limit = getLimit(context);
				HttpResponse<String> response = get(baseUrl + "/users/search.json?query=" + encode(query) + "&per_page=" + limit, headers);
				return toListResult(response, "users");
			}
			case "update": {
				String id = context.getParameter("resourceId", "");
				Map<String, Object> user = buildUserBody(context);
				Map<String, Object> body = Map.of("user", user);
				HttpResponse<String> response = put(baseUrl + "/users/" + encode(id) + ".json", body, headers);
				return toResult(response, "user");
			}
			default:
				return NodeExecutionResult.error("Unknown user operation: " + operation);
		}
	}

	// ========================= Organization Operations =========================

	private NodeExecutionResult executeOrganization(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				Map<String, Object> org = buildOrgBody(context);
				Map<String, Object> body = Map.of("organization", org);
				HttpResponse<String> response = post(baseUrl + "/organizations.json", body, headers);
				return toResult(response, "organization");
			}
			case "delete": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(baseUrl + "/organizations/" + encode(id) + ".json", headers);
				return toDeleteResult(response, id);
			}
			case "get": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(baseUrl + "/organizations/" + encode(id) + ".json", headers);
				return toResult(response, "organization");
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(baseUrl + "/organizations.json?per_page=" + limit, headers);
				return toListResult(response, "organizations");
			}
			case "getRelatedData": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(baseUrl + "/organizations/" + encode(id) + "/related.json", headers);
				return toResult(response, null);
			}
			case "update": {
				String id = context.getParameter("resourceId", "");
				Map<String, Object> org = buildOrgBody(context);
				Map<String, Object> body = Map.of("organization", org);
				HttpResponse<String> response = put(baseUrl + "/organizations/" + encode(id) + ".json", body, headers);
				return toResult(response, "organization");
			}
			default:
				return NodeExecutionResult.error("Unknown organization operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> authHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		String accessToken = stringVal(credentials, "accessToken");
		if (!accessToken.isEmpty()) {
			headers.put("Authorization", "Bearer " + accessToken);
		} else {
			String email = stringVal(credentials, "email");
			String apiToken = stringVal(credentials, "apiToken");
			if (!email.isEmpty() && !apiToken.isEmpty()) {
				String encoded = Base64.getEncoder().encodeToString((email + "/token:" + apiToken).getBytes());
				headers.put("Authorization", "Basic " + encoded);
			}
		}
		return headers;
	}

	private String stringVal(Map<String, Object> map, String key) {
		Object val = map.get(key);
		return val != null ? String.valueOf(val) : "";
	}

	private int getLimit(NodeExecutionContext context) {
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 100);
		return returnAll ? 100 : Math.min(limit, 100);
	}

	private void putIfNotEmpty(Map<String, Object> map, String key, String value) {
		if (value != null && !value.isEmpty()) {
			map.put(key, value);
		}
	}

	private Map<String, Object> buildTicketBody(NodeExecutionContext context) {
		String additionalJson = context.getParameter("additionalFields", "{}");
		Map<String, Object> ticket = new LinkedHashMap<>(parseJson(additionalJson));
		putIfNotEmpty(ticket, "subject", context.getParameter("ticketSubject", ""));
		String description = context.getParameter("ticketDescription", "");
		if (description != null && !description.isEmpty()) {
			ticket.put("comment", Map.of("body", description));
		}
		putIfNotEmpty(ticket, "status", context.getParameter("ticketStatus", ""));
		putIfNotEmpty(ticket, "priority", context.getParameter("ticketPriority", ""));
		return ticket;
	}

	private Map<String, Object> buildUserBody(NodeExecutionContext context) {
		String additionalJson = context.getParameter("additionalFields", "{}");
		Map<String, Object> user = new LinkedHashMap<>(parseJson(additionalJson));
		putIfNotEmpty(user, "name", context.getParameter("userName", ""));
		putIfNotEmpty(user, "email", context.getParameter("userEmail", ""));
		return user;
	}

	private Map<String, Object> buildOrgBody(NodeExecutionContext context) {
		String additionalJson = context.getParameter("additionalFields", "{}");
		Map<String, Object> org = new LinkedHashMap<>(parseJson(additionalJson));
		putIfNotEmpty(org, "name", context.getParameter("orgName", ""));
		return org;
	}

	private NodeExecutionResult toResult(HttpResponse<String> response, String dataKey) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		if (dataKey != null && parsed.containsKey(dataKey)) {
			Object data = parsed.get(dataKey);
			if (data instanceof Map) {
				return NodeExecutionResult.success(List.of(wrapInJson(data)));
			}
		}
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

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response, String id) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "id", id))));
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Zendesk API error (HTTP " + response.statusCode() + "): " + body);
	}
}
