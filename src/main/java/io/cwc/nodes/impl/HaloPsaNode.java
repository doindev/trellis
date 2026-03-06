package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * HaloPSA — manage tickets, clients, sites and users via the HaloPSA REST API.
 */
@Slf4j
@Node(
		type = "haloPsa",
		displayName = "HaloPSA",
		description = "Manage tickets and clients in HaloPSA",
		category = "Customer Support",
		icon = "haloPsa",
		credentials = {"haloPsaApi"}
)
public class HaloPsaNode extends AbstractApiNode {

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		// Resource selector
		params.add(NodeParameter.builder()
				.name("resource").displayName("Resource")
				.type(ParameterType.OPTIONS).required(true).defaultValue("ticket")
				.noDataExpression(true)
				.options(List.of(
						ParameterOption.builder().name("Client").value("client").description("Manage clients").build(),
						ParameterOption.builder().name("Ticket").value("ticket").description("Manage tickets").build(),
						ParameterOption.builder().name("Site").value("site").description("Manage sites").build(),
						ParameterOption.builder().name("User").value("user").description("Manage users").build()
				)).build());

		// Operation selectors
		for (String res : List.of("client", "ticket", "site", "user")) {
			params.add(NodeParameter.builder()
					.name("operation").displayName("Operation")
					.type(ParameterType.OPTIONS).required(true).defaultValue("create")
					.displayOptions(Map.of("show", Map.of("resource", List.of(res))))
					.options(List.of(
							ParameterOption.builder().name("Create").value("create").build(),
							ParameterOption.builder().name("Get").value("get").build(),
							ParameterOption.builder().name("Get Many").value("getAll").build(),
							ParameterOption.builder().name("Update").value("update").build(),
							ParameterOption.builder().name("Delete").value("delete").build()
					)).build());
		}

		// Client parameters
		params.add(NodeParameter.builder()
				.name("clientId").displayName("Client ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("client"), "operation", List.of("get", "update", "delete"))))
				.build());
		params.add(NodeParameter.builder()
				.name("clientName").displayName("Name")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("client"), "operation", List.of("create"))))
				.build());
		params.add(NodeParameter.builder()
				.name("clientAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("client"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("email").displayName("Email").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("phone").displayName("Phone").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("website").displayName("Website").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("notes").displayName("Notes").type(ParameterType.STRING).build()
				)).build());
		params.add(NodeParameter.builder()
				.name("clientUpdateFields").displayName("Update Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("client"), "operation", List.of("update"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("email").displayName("Email").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("phone").displayName("Phone").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("website").displayName("Website").type(ParameterType.STRING).build()
				)).build());

		// Ticket parameters
		params.add(NodeParameter.builder()
				.name("ticketId").displayName("Ticket ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("get", "update", "delete"))))
				.build());
		params.add(NodeParameter.builder()
				.name("ticketSummary").displayName("Summary")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create"))))
				.build());
		params.add(NodeParameter.builder()
				.name("ticketDetails").displayName("Details")
				.type(ParameterType.STRING).typeOptions(Map.of("rows", 4))
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create"))))
				.build());
		params.add(NodeParameter.builder()
				.name("ticketAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("clientId").displayName("Client ID").type(ParameterType.NUMBER).build(),
						NodeParameter.builder().name("userId").displayName("User ID").type(ParameterType.NUMBER).build(),
						NodeParameter.builder().name("siteId").displayName("Site ID").type(ParameterType.NUMBER).build(),
						NodeParameter.builder().name("priority").displayName("Priority").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("ticketTypeId").displayName("Ticket Type ID").type(ParameterType.NUMBER).build()
				)).build());
		params.add(NodeParameter.builder()
				.name("ticketUpdateFields").displayName("Update Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("update"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("summary").displayName("Summary").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("details").displayName("Details").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("priority").displayName("Priority").type(ParameterType.STRING).build()
				)).build());

		// Site parameters
		params.add(NodeParameter.builder()
				.name("siteId").displayName("Site ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("site"), "operation", List.of("get", "update", "delete"))))
				.build());
		params.add(NodeParameter.builder()
				.name("siteName").displayName("Name")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("site"), "operation", List.of("create"))))
				.build());
		params.add(NodeParameter.builder()
				.name("siteClientId").displayName("Client ID")
				.type(ParameterType.NUMBER).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("site"), "operation", List.of("create"))))
				.build());
		params.add(NodeParameter.builder()
				.name("siteAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("site"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("phonenumber").displayName("Phone").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("address").displayName("Address").type(ParameterType.STRING).build()
				)).build());
		params.add(NodeParameter.builder()
				.name("siteUpdateFields").displayName("Update Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("site"), "operation", List.of("update"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("phonenumber").displayName("Phone").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("address").displayName("Address").type(ParameterType.STRING).build()
				)).build());

		// User parameters
		params.add(NodeParameter.builder()
				.name("userId").displayName("User ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("get", "update", "delete"))))
				.build());
		params.add(NodeParameter.builder()
				.name("userName").displayName("Name")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
				.build());
		params.add(NodeParameter.builder()
				.name("userEmail").displayName("Email")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
				.build());
		params.add(NodeParameter.builder()
				.name("userAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("clientId").displayName("Client ID").type(ParameterType.NUMBER).build(),
						NodeParameter.builder().name("siteId").displayName("Site ID").type(ParameterType.NUMBER).build(),
						NodeParameter.builder().name("phone").displayName("Phone").type(ParameterType.STRING).build()
				)).build());
		params.add(NodeParameter.builder()
				.name("userUpdateFields").displayName("Update Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("update"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("email").displayName("Email").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("phone").displayName("Phone").type(ParameterType.STRING).build()
				)).build());

		// Common getAll parameters
		for (String res : List.of("client", "ticket", "site", "user")) {
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

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "ticket");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String accessToken = getAccessToken(credentials);
			String baseUrl = getBaseUrl(credentials);
			Map<String, String> headers = new LinkedHashMap<>();
			headers.put("Authorization", "Bearer " + accessToken);
			headers.put("Content-Type", "application/json");

			return switch (resource) {
				case "client" -> executeResource(context, baseUrl, "/client", "clientId", "clientName",
						"clientAdditionalFields", "clientUpdateFields", headers);
				case "ticket" -> executeTicket(context, baseUrl, headers);
				case "site" -> executeSite(context, baseUrl, headers);
				case "user" -> executeResource(context, baseUrl, "/users", "userId", "userName",
						"userAdditionalFields", "userUpdateFields", headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "HaloPSA API error: " + e.getMessage(), e);
		}
	}

	// ========================= Ticket =========================

	private NodeExecutionResult executeTicket(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "create");

		switch (operation) {
			case "create": {
				String summary = context.getParameter("ticketSummary", "");
				String details = context.getParameter("ticketDetails", "");
				Map<String, Object> additional = context.getParameter("ticketAdditionalFields", Map.of());

				Map<String, Object> ticket = new LinkedHashMap<>();
				ticket.put("summary", summary);
				putIfPresent(ticket, "details", details);
				putIfPresent(ticket, "client_id", additional.get("clientId"));
				putIfPresent(ticket, "user_id", additional.get("userId"));
				putIfPresent(ticket, "site_id", additional.get("siteId"));
				putIfPresent(ticket, "priority_id", additional.get("priority"));
				putIfPresent(ticket, "tickettype_id", additional.get("ticketTypeId"));

				HttpResponse<String> response = post(baseUrl + "/tickets", List.of(ticket), headers);
				return toResult(response);
			}
			case "get": {
				String id = context.getParameter("ticketId", "");
				HttpResponse<String> response = get(baseUrl + "/tickets/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				HttpResponse<String> response = get(baseUrl + "/tickets?count=" + limit, headers);
				return toListResult(response, "tickets");
			}
			case "update": {
				String id = context.getParameter("ticketId", "");
				Map<String, Object> updateFields = context.getParameter("ticketUpdateFields", Map.of());
				Map<String, Object> ticket = new LinkedHashMap<>();
				ticket.put("id", Integer.parseInt(id));
				putIfPresent(ticket, "summary", updateFields.get("summary"));
				putIfPresent(ticket, "details", updateFields.get("details"));
				putIfPresent(ticket, "priority_id", updateFields.get("priority"));

				HttpResponse<String> response = post(baseUrl + "/tickets", List.of(ticket), headers);
				return toResult(response);
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

	// ========================= Site =========================

	private NodeExecutionResult executeSite(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "create");

		switch (operation) {
			case "create": {
				String name = context.getParameter("siteName", "");
				int clientId = toInt(context.getParameter("siteClientId", 0), 0);
				Map<String, Object> additional = context.getParameter("siteAdditionalFields", Map.of());

				Map<String, Object> site = new LinkedHashMap<>();
				site.put("name", name);
				site.put("client_id", clientId);
				putIfPresent(site, "phonenumber", additional.get("phonenumber"));
				putIfPresent(site, "address", additional.get("address"));

				HttpResponse<String> response = post(baseUrl + "/site", List.of(site), headers);
				return toResult(response);
			}
			case "get": {
				String id = context.getParameter("siteId", "");
				HttpResponse<String> response = get(baseUrl + "/site/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				HttpResponse<String> response = get(baseUrl + "/site?count=" + limit, headers);
				return toListResult(response, "sites");
			}
			case "update": {
				String id = context.getParameter("siteId", "");
				Map<String, Object> updateFields = context.getParameter("siteUpdateFields", Map.of());
				Map<String, Object> site = new LinkedHashMap<>();
				site.put("id", Integer.parseInt(id));
				putIfPresent(site, "name", updateFields.get("name"));
				putIfPresent(site, "phonenumber", updateFields.get("phonenumber"));
				putIfPresent(site, "address", updateFields.get("address"));

				HttpResponse<String> response = post(baseUrl + "/site", List.of(site), headers);
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("siteId", "");
				HttpResponse<String> response = delete(baseUrl + "/site/" + encode(id), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown site operation: " + operation);
		}
	}

	// ========================= Generic Resource =========================

	private NodeExecutionResult executeResource(NodeExecutionContext context, String baseUrl, String endpoint,
			String idParam, String nameParam, String additionalParam, String updateParam,
			Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "create");

		switch (operation) {
			case "create": {
				String name = context.getParameter(nameParam, "");
				Map<String, Object> additional = context.getParameter(additionalParam, Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				if ("userName".equals(nameParam)) {
					body.put("name", name);
					body.put("emailaddress", context.getParameter("userEmail", ""));
					putIfPresent(body, "client_id", additional.get("clientId"));
					putIfPresent(body, "site_id", additional.get("siteId"));
					putIfPresent(body, "phonenumber", additional.get("phone"));
				} else {
					body.put("name", name);
					putIfPresent(body, "email", additional.get("email"));
					putIfPresent(body, "phone", additional.get("phone"));
					putIfPresent(body, "website", additional.get("website"));
					putIfPresent(body, "notes", additional.get("notes"));
				}

				HttpResponse<String> response = post(baseUrl + endpoint, List.of(body), headers);
				return toResult(response);
			}
			case "get": {
				String id = context.getParameter(idParam, "");
				HttpResponse<String> response = get(baseUrl + endpoint + "/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				HttpResponse<String> response = get(baseUrl + endpoint + "?count=" + limit, headers);
				return toResult(response);
			}
			case "update": {
				String id = context.getParameter(idParam, "");
				Map<String, Object> updateFields = context.getParameter(updateParam, Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("id", Integer.parseInt(id));
				for (Map.Entry<String, Object> entry : updateFields.entrySet()) {
					if (entry.getValue() != null && !String.valueOf(entry.getValue()).isEmpty()) {
						body.put(entry.getKey(), entry.getValue());
					}
				}

				HttpResponse<String> response = post(baseUrl + endpoint, List.of(body), headers);
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter(idParam, "");
				HttpResponse<String> response = delete(baseUrl + endpoint + "/" + encode(id), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	@Override
	protected String getBaseUrl(Map<String, Object> credentials) {
		String url = String.valueOf(credentials.getOrDefault("baseUrl", ""));
		if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
		return url + "/api";
	}

	private String getAccessToken(Map<String, Object> credentials) throws Exception {
		String authUrl = String.valueOf(credentials.getOrDefault("authUrl", ""));
		String clientId = String.valueOf(credentials.getOrDefault("clientId", ""));
		String clientSecret = String.valueOf(credentials.getOrDefault("clientSecret", ""));

		if (authUrl.endsWith("/")) authUrl = authUrl.substring(0, authUrl.length() - 1);

		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/x-www-form-urlencoded");

		String body = "grant_type=client_credentials"
				+ "&client_id=" + encode(clientId)
				+ "&client_secret=" + encode(clientSecret)
				+ "&scope=all";

		HttpResponse<String> response = post(authUrl + "/token", body, headers);
		Map<String, Object> tokenResponse = parseResponse(response);
		return String.valueOf(tokenResponse.getOrDefault("access_token", ""));
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
		// HaloPSA may return arrays or objects
		if (body.trim().startsWith("[")) {
			List<Map<String, Object>> items = parseArrayResponse(response);
			List<Map<String, Object>> wrapped = new ArrayList<>();
			for (Map<String, Object> item : items) {
				wrapped.add(wrapInJson(item));
			}
			return wrapped.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(wrapped);
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

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response) {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("HaloPSA API error (HTTP " + response.statusCode() + "): " + body);
	}
}
