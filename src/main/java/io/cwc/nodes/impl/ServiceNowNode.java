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
 * ServiceNow Node -- manage incidents, users, table records, and more
 * in ServiceNow.
 */
@Slf4j
@Node(
	type = "serviceNow",
	displayName = "ServiceNow",
	description = "Manage incidents, users, table records, and other resources in ServiceNow",
	category = "Customer Support",
	icon = "servicenow",
	credentials = {"serviceNowApi"}
)
public class ServiceNowNode extends AbstractApiNode {

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("incident")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Business Service").value("businessService").description("Query business services").build(),
				ParameterOption.builder().name("Configuration Items").value("configurationItems").description("Query configuration items").build(),
				ParameterOption.builder().name("Department").value("department").description("Query departments").build(),
				ParameterOption.builder().name("Dictionary").value("dictionary").description("Query dictionary entries").build(),
				ParameterOption.builder().name("Incident").value("incident").description("Manage incidents").build(),
				ParameterOption.builder().name("Table Record").value("tableRecord").description("Manage table records").build(),
				ParameterOption.builder().name("User").value("user").description("Manage users").build(),
				ParameterOption.builder().name("User Group").value("userGroup").description("Query user groups").build(),
				ParameterOption.builder().name("User Role").value("userRole").description("Query user roles").build()
			)).build());

		// Business Service operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("businessService"))))
			.options(List.of(
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many business services").build()
			)).build());

		// Configuration Items operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("configurationItems"))))
			.options(List.of(
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many configuration items").build()
			)).build());

		// Department operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("department"))))
			.options(List.of(
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many departments").build()
			)).build());

		// Dictionary operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("dictionary"))))
			.options(List.of(
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many dictionary entries").build()
			)).build());

		// Incident operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("incident"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an incident").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an incident").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an incident").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many incidents").build(),
				ParameterOption.builder().name("Update").value("update").description("Update an incident").build()
			)).build());

		// Table Record operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("tableRecord"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a table record").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a table record").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a table record").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many table records").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a table record").build()
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
				ParameterOption.builder().name("Update").value("update").description("Update a user").build()
			)).build());

		// User Group operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("userGroup"))))
			.options(List.of(
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many user groups").build()
			)).build());

		// User Role operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("userRole"))))
			.options(List.of(
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many user roles").build()
			)).build());

		// Common parameters
		params.add(NodeParameter.builder()
			.name("sysId").displayName("Sys ID")
			.type(ParameterType.STRING).required(true)
			.description("The sys_id of the record.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("get", "update", "delete"))))
			.build());

		params.add(NodeParameter.builder()
			.name("tableName").displayName("Table Name")
			.type(ParameterType.STRING).required(true)
			.description("The name of the table.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("tableRecord"))))
			.build());

		params.add(NodeParameter.builder()
			.name("shortDescription").displayName("Short Description")
			.type(ParameterType.STRING)
			.description("Short description of the incident.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("incident"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("urgency").displayName("Urgency")
			.type(ParameterType.OPTIONS)
			.description("Urgency of the incident.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("incident"), "operation", List.of("create", "update"))))
			.options(List.of(
				ParameterOption.builder().name("Low").value("3").build(),
				ParameterOption.builder().name("Medium").value("2").build(),
				ParameterOption.builder().name("High").value("1").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("impact").displayName("Impact")
			.type(ParameterType.OPTIONS)
			.description("Impact of the incident.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("incident"), "operation", List.of("create", "update"))))
			.options(List.of(
				ParameterOption.builder().name("Low").value("3").build(),
				ParameterOption.builder().name("Medium").value("2").build(),
				ParameterOption.builder().name("High").value("1").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("userName").displayName("User Name")
			.type(ParameterType.STRING)
			.description("User name (user_name field).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("userEmail").displayName("Email")
			.type(ParameterType.STRING)
			.description("Email of the user.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("userFirstName").displayName("First Name")
			.type(ParameterType.STRING)
			.description("First name of the user.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("userLastName").displayName("Last Name")
			.type(ParameterType.STRING)
			.description("Last name of the user.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("additionalFields").displayName("Additional Fields (JSON)")
			.type(ParameterType.JSON).defaultValue("{}")
			.description("Additional fields as JSON.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("queryFilter").displayName("Query Filter")
			.type(ParameterType.STRING)
			.description("ServiceNow encoded query string (e.g., active=true).")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("returnAll").displayName("Return All")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Whether to return all results.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit")
			.type(ParameterType.NUMBER).defaultValue(100)
			.description("Max number of results to return.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "incident");
		String operation = context.getParameter("operation", "getAll");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String instance = String.valueOf(credentials.getOrDefault("instance", ""));
			String baseUrl = "https://" + instance + ".service-now.com/api/now";
			Map<String, String> headers = authHeaders(credentials);

			return switch (resource) {
				case "businessService" -> executeTableGetAll(context, baseUrl, "table/cmdb_ci_service", headers);
				case "configurationItems" -> executeTableGetAll(context, baseUrl, "table/cmdb_ci", headers);
				case "department" -> executeTableGetAll(context, baseUrl, "table/cmn_department", headers);
				case "dictionary" -> executeTableGetAll(context, baseUrl, "table/sys_dictionary", headers);
				case "incident" -> executeIncident(context, operation, baseUrl, headers);
				case "tableRecord" -> executeTableRecord(context, operation, baseUrl, headers);
				case "user" -> executeUser(context, operation, baseUrl, headers);
				case "userGroup" -> executeTableGetAll(context, baseUrl, "table/sys_user_group", headers);
				case "userRole" -> executeTableGetAll(context, baseUrl, "table/sys_user_role", headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "ServiceNow API error: " + e.getMessage(), e);
		}
	}

	// ========================= Incident Operations =========================

	private NodeExecutionResult executeIncident(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		String tablePath = "table/incident";
		switch (operation) {
			case "create": {
				Map<String, Object> body = buildIncidentBody(context);
				HttpResponse<String> response = post(baseUrl + "/" + tablePath, body, headers);
				return toResult(response);
			}
			case "delete": {
				String sysId = context.getParameter("sysId", "");
				HttpResponse<String> response = delete(baseUrl + "/" + tablePath + "/" + encode(sysId), headers);
				return toDeleteResult(response, sysId);
			}
			case "get": {
				String sysId = context.getParameter("sysId", "");
				HttpResponse<String> response = get(baseUrl + "/" + tablePath + "/" + encode(sysId), headers);
				return toResult(response);
			}
			case "getAll": {
				return executeTableGetAll(context, baseUrl, tablePath, headers);
			}
			case "update": {
				String sysId = context.getParameter("sysId", "");
				Map<String, Object> body = buildIncidentBody(context);
				HttpResponse<String> response = patch(baseUrl + "/" + tablePath + "/" + encode(sysId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown incident operation: " + operation);
		}
	}

	// ========================= Table Record Operations =========================

	private NodeExecutionResult executeTableRecord(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		String tableName = context.getParameter("tableName", "");
		String tablePath = "table/" + encode(tableName);
		switch (operation) {
			case "create": {
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				HttpResponse<String> response = post(baseUrl + "/" + tablePath, body, headers);
				return toResult(response);
			}
			case "delete": {
				String sysId = context.getParameter("sysId", "");
				HttpResponse<String> response = delete(baseUrl + "/" + tablePath + "/" + encode(sysId), headers);
				return toDeleteResult(response, sysId);
			}
			case "get": {
				String sysId = context.getParameter("sysId", "");
				HttpResponse<String> response = get(baseUrl + "/" + tablePath + "/" + encode(sysId), headers);
				return toResult(response);
			}
			case "getAll": {
				return executeTableGetAll(context, baseUrl, tablePath, headers);
			}
			case "update": {
				String sysId = context.getParameter("sysId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				HttpResponse<String> response = patch(baseUrl + "/" + tablePath + "/" + encode(sysId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown table record operation: " + operation);
		}
	}

	// ========================= User Operations =========================

	private NodeExecutionResult executeUser(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		String tablePath = "table/sys_user";
		switch (operation) {
			case "create": {
				Map<String, Object> body = buildUserBody(context);
				HttpResponse<String> response = post(baseUrl + "/" + tablePath, body, headers);
				return toResult(response);
			}
			case "delete": {
				String sysId = context.getParameter("sysId", "");
				HttpResponse<String> response = delete(baseUrl + "/" + tablePath + "/" + encode(sysId), headers);
				return toDeleteResult(response, sysId);
			}
			case "get": {
				String sysId = context.getParameter("sysId", "");
				HttpResponse<String> response = get(baseUrl + "/" + tablePath + "/" + encode(sysId), headers);
				return toResult(response);
			}
			case "getAll": {
				return executeTableGetAll(context, baseUrl, tablePath, headers);
			}
			case "update": {
				String sysId = context.getParameter("sysId", "");
				Map<String, Object> body = buildUserBody(context);
				HttpResponse<String> response = patch(baseUrl + "/" + tablePath + "/" + encode(sysId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown user operation: " + operation);
		}
	}

	// ========================= Generic Table GetAll =========================

	private NodeExecutionResult executeTableGetAll(NodeExecutionContext context, String baseUrl, String tablePath, Map<String, String> headers) throws Exception {
		int limit = getLimit(context);
		String queryFilter = context.getParameter("queryFilter", "");
		String url = baseUrl + "/" + tablePath + "?sysparm_limit=" + limit;
		if (queryFilter != null && !queryFilter.isEmpty()) {
			url += "&sysparm_query=" + encode(queryFilter);
		}
		HttpResponse<String> response = get(url, headers);
		return toListResult(response, "result");
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
			String username = stringVal(credentials, "username");
			String password = stringVal(credentials, "password");
			if (!username.isEmpty() && !password.isEmpty()) {
				String encoded = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
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
		return returnAll ? 1000 : Math.min(limit, 1000);
	}

	private void putIfNotEmpty(Map<String, Object> map, String key, String value) {
		if (value != null && !value.isEmpty()) {
			map.put(key, value);
		}
	}

	private Map<String, Object> buildIncidentBody(NodeExecutionContext context) {
		String additionalJson = context.getParameter("additionalFields", "{}");
		Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
		putIfNotEmpty(body, "short_description", context.getParameter("shortDescription", ""));
		putIfNotEmpty(body, "urgency", context.getParameter("urgency", ""));
		putIfNotEmpty(body, "impact", context.getParameter("impact", ""));
		return body;
	}

	private Map<String, Object> buildUserBody(NodeExecutionContext context) {
		String additionalJson = context.getParameter("additionalFields", "{}");
		Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
		putIfNotEmpty(body, "user_name", context.getParameter("userName", ""));
		putIfNotEmpty(body, "email", context.getParameter("userEmail", ""));
		putIfNotEmpty(body, "first_name", context.getParameter("userFirstName", ""));
		putIfNotEmpty(body, "last_name", context.getParameter("userLastName", ""));
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
		Object result = parsed.get("result");
		if (result instanceof Map) {
			return NodeExecutionResult.success(List.of(wrapInJson(result)));
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

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response, String sysId) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "sys_id", sysId))));
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("ServiceNow API error (HTTP " + response.statusCode() + "): " + body);
	}
}
