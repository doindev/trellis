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
 * PagerDuty Node -- manage incidents, log entries, and users via
 * the PagerDuty REST API v2.
 */
@Slf4j
@Node(
	type = "pagerDuty",
	displayName = "PagerDuty",
	description = "Manage incidents, log entries, and users in PagerDuty",
	category = "Miscellaneous",
	icon = "pagerDuty",
	credentials = {"pagerDutyApi"}
)
public class PagerDutyNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.pagerduty.com";

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
				ParameterOption.builder().name("Incident").value("incident").description("Manage incidents").build(),
				ParameterOption.builder().name("Log Entry").value("logEntry").description("View log entries").build(),
				ParameterOption.builder().name("User").value("user").description("View users").build()
			)).build());

		// Incident operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("incident"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an incident").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an incident").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all incidents").build(),
				ParameterOption.builder().name("Update").value("update").description("Update an incident").build()
			)).build());

		// Log Entry operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("logEntry"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get a log entry").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all log entries").build()
			)).build());

		// User operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get a user").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all users").build()
			)).build());

		// Incident parameters
		addIncidentParameters(params);

		// Log Entry parameters
		addLogEntryParameters(params);

		// User parameters
		addUserParameters(params);

		return params;
	}

	// ========================= Incident Parameters =========================

	private void addIncidentParameters(List<NodeParameter> params) {
		// Incident > Create
		params.add(NodeParameter.builder()
			.name("incidentTitle").displayName("Title").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("incident"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("incidentServiceId").displayName("Service ID").type(ParameterType.STRING).required(true)
			.description("The ID of the PagerDuty service to create the incident on.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("incident"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("incidentUrgency").displayName("Urgency").type(ParameterType.OPTIONS).defaultValue("high")
			.displayOptions(Map.of("show", Map.of("resource", List.of("incident"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("High").value("high").build(),
				ParameterOption.builder().name("Low").value("low").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("incidentAdditionalFields").displayName("Additional Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("incident"), "operation", List.of("create"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("body").displayName("Body").type(ParameterType.STRING)
					.description("Detailed description of the incident.").build(),
				NodeParameter.builder().name("escalationPolicyId").displayName("Escalation Policy ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("incidentKey").displayName("Incident Key").type(ParameterType.STRING)
					.description("A string which identifies the incident. Sending subsequent requests with the same key will update the existing incident.").build()
			)).build());

		// Incident > Get/Update
		params.add(NodeParameter.builder()
			.name("incidentId").displayName("Incident ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("incident"), "operation", List.of("get", "update"))))
			.build());

		// Incident > Update
		params.add(NodeParameter.builder()
			.name("incidentUpdateFields").displayName("Update Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("incident"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("title").displayName("Title").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("status").displayName("Status").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("Acknowledged").value("acknowledged").build(),
						ParameterOption.builder().name("Resolved").value("resolved").build()
					)).build(),
				NodeParameter.builder().name("urgency").displayName("Urgency").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("High").value("high").build(),
						ParameterOption.builder().name("Low").value("low").build()
					)).build(),
				NodeParameter.builder().name("escalationLevel").displayName("Escalation Level").type(ParameterType.NUMBER).build(),
				NodeParameter.builder().name("resolution").displayName("Resolution").type(ParameterType.STRING).build()
			)).build());

		// Incident > GetAll
		params.add(NodeParameter.builder()
			.name("incidentStatuses").displayName("Statuses").type(ParameterType.STRING)
			.placeHolder("triggered,acknowledged")
			.description("Comma-separated list of statuses to filter by (triggered, acknowledged, resolved).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("incident"), "operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("incidentLimit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(25)
			.displayOptions(Map.of("show", Map.of("resource", List.of("incident"), "operation", List.of("getAll"))))
			.build());

		// From header (required by PagerDuty for write operations)
		params.add(NodeParameter.builder()
			.name("fromEmail").displayName("From Email").type(ParameterType.STRING).required(true)
			.placeHolder("user@example.com")
			.description("The email address of a valid user associated with the account making the request.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("incident"), "operation", List.of("create", "update"))))
			.build());
	}

	// ========================= Log Entry Parameters =========================

	private void addLogEntryParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("logEntryId").displayName("Log Entry ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("logEntry"), "operation", List.of("get"))))
			.build());

		params.add(NodeParameter.builder()
			.name("logEntryLimit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(25)
			.displayOptions(Map.of("show", Map.of("resource", List.of("logEntry"), "operation", List.of("getAll"))))
			.build());
	}

	// ========================= User Parameters =========================

	private void addUserParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("pdUserId").displayName("User ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("get"))))
			.build());

		params.add(NodeParameter.builder()
			.name("pdUserLimit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(25)
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("pdUserQuery").displayName("Query").type(ParameterType.STRING)
			.description("Filter users by name, email, or other fields.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("getAll"))))
			.build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "incident");
		Map<String, Object> credentials = context.getCredentials();

		try {
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (resource) {
				case "incident" -> executeIncident(context, headers);
				case "logEntry" -> executeLogEntry(context, headers);
				case "user" -> executeUser(context, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "PagerDuty API error: " + e.getMessage(), e);
		}
	}

	// ========================= Incident Execute =========================

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeIncident(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "create": {
				String title = context.getParameter("incidentTitle", "");
				String serviceId = context.getParameter("incidentServiceId", "");
				String urgency = context.getParameter("incidentUrgency", "high");
				String fromEmail = context.getParameter("fromEmail", "");
				Map<String, Object> additional = context.getParameter("incidentAdditionalFields", Map.of());

				Map<String, Object> incident = new LinkedHashMap<>();
				incident.put("type", "incident");
				incident.put("title", title);
				incident.put("service", Map.of("id", serviceId, "type", "service_reference"));
				incident.put("urgency", urgency);

				String bodyText = String.valueOf(additional.getOrDefault("body", ""));
				if (!bodyText.isEmpty()) {
					incident.put("body", Map.of("type", "incident_body", "details", bodyText));
				}
				putIfPresent(incident, "incident_key", additional.get("incidentKey"));
				String escalationPolicyId = String.valueOf(additional.getOrDefault("escalationPolicyId", ""));
				if (!escalationPolicyId.isEmpty()) {
					incident.put("escalation_policy", Map.of("id", escalationPolicyId, "type", "escalation_policy_reference"));
				}

				Map<String, Object> body = Map.of("incident", incident);
				headers.put("From", fromEmail);
				HttpResponse<String> response = post(BASE_URL + "/incidents", body, headers);
				return toResultWithKey(response, "incident");
			}
			case "get": {
				String incidentId = context.getParameter("incidentId", "");
				HttpResponse<String> response = get(BASE_URL + "/incidents/" + encode(incidentId), headers);
				return toResultWithKey(response, "incident");
			}
			case "getAll": {
				String statuses = context.getParameter("incidentStatuses", "");
				int limit = toInt(context.getParameter("incidentLimit", 25), 25);
				Map<String, Object> queryParams = new LinkedHashMap<>();
				queryParams.put("limit", limit);
				if (!statuses.isEmpty()) {
					queryParams.put("statuses[]", statuses);
				}
				String url = buildUrl(BASE_URL + "/incidents", queryParams);
				HttpResponse<String> response = get(url, headers);
				return toListResult(response, "incidents");
			}
			case "update": {
				String incidentId = context.getParameter("incidentId", "");
				String fromEmail = context.getParameter("fromEmail", "");
				Map<String, Object> updateFields = context.getParameter("incidentUpdateFields", Map.of());

				Map<String, Object> incident = new LinkedHashMap<>();
				incident.put("id", incidentId);
				incident.put("type", "incident_reference");
				putIfPresent(incident, "title", updateFields.get("title"));
				putIfPresent(incident, "status", updateFields.get("status"));
				putIfPresent(incident, "urgency", updateFields.get("urgency"));
				putIfPresent(incident, "escalation_level", updateFields.get("escalationLevel"));
				String resolution = String.valueOf(updateFields.getOrDefault("resolution", ""));
				if (!resolution.isEmpty()) {
					incident.put("resolution", resolution);
				}

				Map<String, Object> body = Map.of("incident", incident);
				headers.put("From", fromEmail);
				HttpResponse<String> response = put(BASE_URL + "/incidents/" + encode(incidentId), body, headers);
				return toResultWithKey(response, "incident");
			}
			default:
				return NodeExecutionResult.error("Unknown incident operation: " + operation);
		}
	}

	// ========================= Log Entry Execute =========================

	private NodeExecutionResult executeLogEntry(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "get": {
				String logEntryId = context.getParameter("logEntryId", "");
				HttpResponse<String> response = get(BASE_URL + "/log_entries/" + encode(logEntryId), headers);
				return toResultWithKey(response, "log_entry");
			}
			case "getAll": {
				int limit = toInt(context.getParameter("logEntryLimit", 25), 25);
				Map<String, Object> queryParams = Map.of("limit", limit);
				String url = buildUrl(BASE_URL + "/log_entries", queryParams);
				HttpResponse<String> response = get(url, headers);
				return toListResult(response, "log_entries");
			}
			default:
				return NodeExecutionResult.error("Unknown log entry operation: " + operation);
		}
	}

	// ========================= User Execute =========================

	private NodeExecutionResult executeUser(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "get": {
				String userId = context.getParameter("pdUserId", "");
				HttpResponse<String> response = get(BASE_URL + "/users/" + encode(userId), headers);
				return toResultWithKey(response, "user");
			}
			case "getAll": {
				int limit = toInt(context.getParameter("pdUserLimit", 25), 25);
				String query = context.getParameter("pdUserQuery", "");
				Map<String, Object> queryParams = new LinkedHashMap<>();
				queryParams.put("limit", limit);
				if (!query.isEmpty()) {
					queryParams.put("query", query);
				}
				String url = buildUrl(BASE_URL + "/users", queryParams);
				HttpResponse<String> response = get(url, headers);
				return toListResult(response, "users");
			}
			default:
				return NodeExecutionResult.error("Unknown user operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		String apiKey = String.valueOf(credentials.getOrDefault("apiKey", ""));
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Token token=" + apiKey);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/vnd.pagerduty+json;version=2");
		return headers;
	}

	private void putIfPresent(Map<String, Object> map, String key, Object value) {
		if (value != null && !String.valueOf(value).isEmpty()) {
			map.put(key, value);
		}
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult toResultWithKey(HttpResponse<String> response, String dataKey) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		Object data = parsed.getOrDefault(dataKey, parsed);
		if (data instanceof Map) {
			return NodeExecutionResult.success(List.of(wrapInJson(data)));
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	@SuppressWarnings("unchecked")
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

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("PagerDuty API error (HTTP " + response.statusCode() + "): " + body);
	}
}
