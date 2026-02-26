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
 * Microsoft Dynamics CRM Node -- manage accounts, contacts, leads,
 * and opportunities via the Dynamics 365 Web API.
 */
@Slf4j
@Node(
	type = "microsoftDynamicsCrm",
	displayName = "Microsoft Dynamics CRM",
	description = "Manage accounts, contacts, leads, and opportunities in Microsoft Dynamics 365 CRM",
	category = "CRM & Sales",
	icon = "microsoftDynamicsCrm",
	credentials = {"microsoftDynamicsCrmOAuth2Api"}
)
public class MicrosoftDynamicsCrmNode extends AbstractApiNode {

	private static final String API_VERSION = "/api/data/v9.2";

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("account")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Account").value("account").description("Manage accounts").build(),
				ParameterOption.builder().name("Contact").value("contact").description("Manage contacts").build(),
				ParameterOption.builder().name("Lead").value("lead").description("Manage leads").build(),
				ParameterOption.builder().name("Opportunity").value("opportunity").description("Manage opportunities").build()
			)).build());

		// Account operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("account"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an account").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an account").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an account").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many accounts").build(),
				ParameterOption.builder().name("Update").value("update").description("Update an account").build()
			)).build());

		// Contact operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a contact").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a contact").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a contact").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many contacts").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a contact").build()
			)).build());

		// Lead operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("lead"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a lead").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a lead").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a lead").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many leads").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a lead").build()
			)).build());

		// Opportunity operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("opportunity"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an opportunity").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an opportunity").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an opportunity").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many opportunities").build(),
				ParameterOption.builder().name("Update").value("update").description("Update an opportunity").build()
			)).build());

		// Common parameters
		params.add(NodeParameter.builder()
			.name("resourceId").displayName("ID")
			.type(ParameterType.STRING).required(true)
			.description("The GUID of the record.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("get", "update", "delete"))))
			.build());

		// Account fields
		params.add(NodeParameter.builder()
			.name("accountName").displayName("Account Name")
			.type(ParameterType.STRING)
			.description("Name of the account.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("account"), "operation", List.of("create", "update"))))
			.build());

		// Contact fields
		params.add(NodeParameter.builder()
			.name("contactFirstName").displayName("First Name")
			.type(ParameterType.STRING)
			.description("First name of the contact.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("contactLastName").displayName("Last Name")
			.type(ParameterType.STRING)
			.description("Last name of the contact.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("contactEmail").displayName("Email")
			.type(ParameterType.STRING)
			.description("Email address of the contact.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("create", "update"))))
			.build());

		// Lead fields
		params.add(NodeParameter.builder()
			.name("leadSubject").displayName("Subject / Topic")
			.type(ParameterType.STRING)
			.description("Subject of the lead.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("lead"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("leadFirstName").displayName("First Name")
			.type(ParameterType.STRING)
			.description("First name on the lead.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("lead"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("leadLastName").displayName("Last Name")
			.type(ParameterType.STRING)
			.description("Last name on the lead.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("lead"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("leadCompanyName").displayName("Company Name")
			.type(ParameterType.STRING)
			.description("Company name on the lead.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("lead"), "operation", List.of("create", "update"))))
			.build());

		// Opportunity fields
		params.add(NodeParameter.builder()
			.name("opportunityName").displayName("Opportunity Name")
			.type(ParameterType.STRING)
			.description("Name of the opportunity.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("opportunity"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("additionalFields").displayName("Additional Fields (JSON)")
			.type(ParameterType.JSON).defaultValue("{}")
			.description("Additional fields as JSON.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("filterQuery").displayName("OData Filter")
			.type(ParameterType.STRING)
			.description("OData $filter query (e.g., name eq 'Contoso').")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("selectFields").displayName("Select Fields")
			.type(ParameterType.STRING)
			.description("Comma-separated list of fields to return ($select).")
			.displayOptions(Map.of("show", Map.of("operation", List.of("get", "getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("returnAll").displayName("Return All")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Whether to return all results.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit")
			.type(ParameterType.NUMBER).defaultValue(50)
			.description("Max number of results to return.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "account");
		String operation = context.getParameter("operation", "getAll");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String instanceUrl = String.valueOf(credentials.getOrDefault("instanceUrl",
				credentials.getOrDefault("url", "")));
			if (instanceUrl.endsWith("/")) {
				instanceUrl = instanceUrl.substring(0, instanceUrl.length() - 1);
			}
			String baseUrl = instanceUrl + API_VERSION;
			Map<String, String> headers = authHeaders(credentials);

			String entitySet = switch (resource) {
				case "account" -> "accounts";
				case "contact" -> "contacts";
				case "lead" -> "leads";
				case "opportunity" -> "opportunities";
				default -> throw new IllegalArgumentException("Unknown resource: " + resource);
			};

			return executeEntity(context, operation, baseUrl, entitySet, resource, headers);
		} catch (Exception e) {
			return handleError(context, "Microsoft Dynamics CRM API error: " + e.getMessage(), e);
		}
	}

	// ========================= Entity Operations =========================

	private NodeExecutionResult executeEntity(NodeExecutionContext context, String operation, String baseUrl,
			String entitySet, String resource, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				Map<String, Object> body = buildEntityBody(context, resource);
				HttpResponse<String> response = post(baseUrl + "/" + entitySet, body, headers);
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(baseUrl + "/" + entitySet + "(" + id + ")", headers);
				return toDeleteResult(response, id);
			}
			case "get": {
				String id = context.getParameter("resourceId", "");
				String url = baseUrl + "/" + entitySet + "(" + id + ")";
				String selectFields = context.getParameter("selectFields", "");
				if (selectFields != null && !selectFields.isEmpty()) {
					url += "?$select=" + encode(selectFields);
				}
				HttpResponse<String> response = get(url, headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				String url = baseUrl + "/" + entitySet + "?$top=" + limit;
				String filterQuery = context.getParameter("filterQuery", "");
				if (filterQuery != null && !filterQuery.isEmpty()) {
					url += "&$filter=" + encode(filterQuery);
				}
				String selectFields = context.getParameter("selectFields", "");
				if (selectFields != null && !selectFields.isEmpty()) {
					url += "&$select=" + encode(selectFields);
				}
				HttpResponse<String> response = get(url, headers);
				return toListResult(response);
			}
			case "update": {
				String id = context.getParameter("resourceId", "");
				Map<String, Object> body = buildEntityBody(context, resource);
				HttpResponse<String> response = patch(baseUrl + "/" + entitySet + "(" + id + ")", body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> authHeaders(Map<String, Object> credentials) {
		String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		headers.put("OData-MaxVersion", "4.0");
		headers.put("OData-Version", "4.0");
		headers.put("Prefer", "return=representation");
		return headers;
	}

	private int getLimit(NodeExecutionContext context) {
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 50);
		return returnAll ? 5000 : Math.min(limit, 5000);
	}

	private void putIfNotEmpty(Map<String, Object> map, String key, String value) {
		if (value != null && !value.isEmpty()) {
			map.put(key, value);
		}
	}

	private Map<String, Object> buildEntityBody(NodeExecutionContext context, String resource) {
		String additionalJson = context.getParameter("additionalFields", "{}");
		Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));

		switch (resource) {
			case "account":
				putIfNotEmpty(body, "name", context.getParameter("accountName", ""));
				break;
			case "contact":
				putIfNotEmpty(body, "firstname", context.getParameter("contactFirstName", ""));
				putIfNotEmpty(body, "lastname", context.getParameter("contactLastName", ""));
				putIfNotEmpty(body, "emailaddress1", context.getParameter("contactEmail", ""));
				break;
			case "lead":
				putIfNotEmpty(body, "subject", context.getParameter("leadSubject", ""));
				putIfNotEmpty(body, "firstname", context.getParameter("leadFirstName", ""));
				putIfNotEmpty(body, "lastname", context.getParameter("leadLastName", ""));
				putIfNotEmpty(body, "companyname", context.getParameter("leadCompanyName", ""));
				break;
			case "opportunity":
				putIfNotEmpty(body, "name", context.getParameter("opportunityName", ""));
				break;
		}
		return body;
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

	@SuppressWarnings("unchecked")
	private NodeExecutionResult toListResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		Object data = parsed.get("value");
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
		return NodeExecutionResult.error("Microsoft Dynamics CRM API error (HTTP " + response.statusCode() + "): " + body);
	}
}
