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
 * Intercom — manage leads, users and companies via the Intercom REST API.
 */
@Slf4j
@Node(
		type = "intercom",
		displayName = "Intercom",
		description = "Manage leads, users and companies in Intercom",
		category = "Customer Support",
		icon = "intercom",
		credentials = {"intercomApi"}
)
public class IntercomNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.intercom.io";

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		// Resource selector
		params.add(NodeParameter.builder()
				.name("resource").displayName("Resource")
				.type(ParameterType.OPTIONS).required(true).defaultValue("lead")
				.noDataExpression(true)
				.options(List.of(
						ParameterOption.builder().name("Lead").value("lead").description("Manage leads").build(),
						ParameterOption.builder().name("User").value("user").description("Manage users").build(),
						ParameterOption.builder().name("Company").value("company").description("Manage companies").build()
				)).build());

		// Operation selectors
		for (String res : List.of("lead", "user", "company")) {
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

		// Lead parameters
		addLeadParameters(params);
		// User parameters
		addUserParameters(params);
		// Company parameters
		addCompanyParameters(params);

		// Common getAll
		for (String res : List.of("lead", "user", "company")) {
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

	private void addLeadParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
				.name("leadId").displayName("Lead ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("lead"), "operation", List.of("get", "update", "delete"))))
				.build());

		params.add(NodeParameter.builder()
				.name("leadAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("lead"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("email").displayName("Email").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("phone").displayName("Phone").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("avatar").displayName("Avatar URL").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("companyId").displayName("Company ID").type(ParameterType.STRING).build()
				)).build());

		params.add(NodeParameter.builder()
				.name("leadUpdateFields").displayName("Update Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("lead"), "operation", List.of("update"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("email").displayName("Email").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("phone").displayName("Phone").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("avatar").displayName("Avatar URL").type(ParameterType.STRING).build()
				)).build());
	}

	private void addUserParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
				.name("userId").displayName("User ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("get", "update", "delete"))))
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
						NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("phone").displayName("Phone").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("externalId").displayName("External ID").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("avatar").displayName("Avatar URL").type(ParameterType.STRING).build()
				)).build());

		params.add(NodeParameter.builder()
				.name("userUpdateFields").displayName("Update Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("update"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("email").displayName("Email").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("phone").displayName("Phone").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("avatar").displayName("Avatar URL").type(ParameterType.STRING).build()
				)).build());
	}

	private void addCompanyParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
				.name("companyId").displayName("Company ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("company"), "operation", List.of("get", "update", "delete"))))
				.build());

		params.add(NodeParameter.builder()
				.name("companyName").displayName("Name")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("company"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("companyAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("company"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("companyId").displayName("Company ID (external)").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("plan").displayName("Plan").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("website").displayName("Website").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("industry").displayName("Industry").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("size").displayName("Size").type(ParameterType.NUMBER).build()
				)).build());

		params.add(NodeParameter.builder()
				.name("companyUpdateFields").displayName("Update Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("company"), "operation", List.of("update"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("plan").displayName("Plan").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("website").displayName("Website").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("industry").displayName("Industry").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("size").displayName("Size").type(ParameterType.NUMBER).build()
				)).build());
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "lead");
		Map<String, Object> credentials = context.getCredentials();

		try {
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (resource) {
				case "lead" -> executeLead(context, headers);
				case "user" -> executeUser(context, headers);
				case "company" -> executeCompany(context, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Intercom API error: " + e.getMessage(), e);
		}
	}

	// ========================= Lead =========================

	private NodeExecutionResult executeLead(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "create");

		switch (operation) {
			case "create": {
				Map<String, Object> additional = context.getParameter("leadAdditionalFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("role", "lead");
				putIfPresent(body, "email", additional.get("email"));
				putIfPresent(body, "name", additional.get("name"));
				putIfPresent(body, "phone", additional.get("phone"));
				putIfPresent(body, "avatar", additional.get("avatar"));
				if (additional.get("companyId") != null && !String.valueOf(additional.get("companyId")).isEmpty()) {
					body.put("companies", List.of(Map.of("company_id", additional.get("companyId"))));
				}

				HttpResponse<String> response = post(BASE_URL + "/contacts", body, headers);
				return toResult(response);
			}
			case "get": {
				String id = context.getParameter("leadId", "");
				HttpResponse<String> response = get(BASE_URL + "/contacts/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				HttpResponse<String> response = get(BASE_URL + "/contacts?per_page=" + Math.min(limit, 150), headers);
				return toListResult(response, "data");
			}
			case "update": {
				String id = context.getParameter("leadId", "");
				Map<String, Object> updateFields = context.getParameter("leadUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "email", updateFields.get("email"));
				putIfPresent(body, "name", updateFields.get("name"));
				putIfPresent(body, "phone", updateFields.get("phone"));
				putIfPresent(body, "avatar", updateFields.get("avatar"));

				HttpResponse<String> response = put(BASE_URL + "/contacts/" + encode(id), body, headers);
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("leadId", "");
				HttpResponse<String> response = delete(BASE_URL + "/contacts/" + encode(id), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown lead operation: " + operation);
		}
	}

	// ========================= User =========================

	private NodeExecutionResult executeUser(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "create");

		switch (operation) {
			case "create": {
				String email = context.getParameter("userEmail", "");
				Map<String, Object> additional = context.getParameter("userAdditionalFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("role", "user");
				body.put("email", email);
				putIfPresent(body, "name", additional.get("name"));
				putIfPresent(body, "phone", additional.get("phone"));
				putIfPresent(body, "external_id", additional.get("externalId"));
				putIfPresent(body, "avatar", additional.get("avatar"));

				HttpResponse<String> response = post(BASE_URL + "/contacts", body, headers);
				return toResult(response);
			}
			case "get": {
				String id = context.getParameter("userId", "");
				HttpResponse<String> response = get(BASE_URL + "/contacts/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				HttpResponse<String> response = get(BASE_URL + "/contacts?per_page=" + Math.min(limit, 150), headers);
				return toListResult(response, "data");
			}
			case "update": {
				String id = context.getParameter("userId", "");
				Map<String, Object> updateFields = context.getParameter("userUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "email", updateFields.get("email"));
				putIfPresent(body, "name", updateFields.get("name"));
				putIfPresent(body, "phone", updateFields.get("phone"));
				putIfPresent(body, "avatar", updateFields.get("avatar"));

				HttpResponse<String> response = put(BASE_URL + "/contacts/" + encode(id), body, headers);
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("userId", "");
				HttpResponse<String> response = delete(BASE_URL + "/contacts/" + encode(id), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown user operation: " + operation);
		}
	}

	// ========================= Company =========================

	private NodeExecutionResult executeCompany(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "create");

		switch (operation) {
			case "create": {
				String name = context.getParameter("companyName", "");
				Map<String, Object> additional = context.getParameter("companyAdditionalFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", name);
				putIfPresent(body, "company_id", additional.get("companyId"));
				putIfPresent(body, "plan", additional.get("plan"));
				putIfPresent(body, "website", additional.get("website"));
				putIfPresent(body, "industry", additional.get("industry"));
				putIfPresent(body, "size", additional.get("size"));

				HttpResponse<String> response = post(BASE_URL + "/companies", body, headers);
				return toResult(response);
			}
			case "get": {
				String id = context.getParameter("companyId", "");
				HttpResponse<String> response = get(BASE_URL + "/companies/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				HttpResponse<String> response = get(BASE_URL + "/companies?per_page=" + Math.min(limit, 150), headers);
				return toListResult(response, "data");
			}
			case "update": {
				String id = context.getParameter("companyId", "");
				Map<String, Object> updateFields = context.getParameter("companyUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("company_id", id);
				putIfPresent(body, "name", updateFields.get("name"));
				putIfPresent(body, "plan", updateFields.get("plan"));
				putIfPresent(body, "website", updateFields.get("website"));
				putIfPresent(body, "industry", updateFields.get("industry"));
				putIfPresent(body, "size", updateFields.get("size"));

				HttpResponse<String> response = post(BASE_URL + "/companies", body, headers);
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("companyId", "");
				HttpResponse<String> response = delete(BASE_URL + "/companies/" + encode(id), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown company operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		headers.put("Intercom-Version", "2.10");
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
		String body = response.body();
		if (body != null && !body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(parseResponse(response))));
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Intercom API error (HTTP " + response.statusCode() + "): " + body);
	}
}
