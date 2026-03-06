package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Microsoft Graph Security — manage alerts and secure scores via the Microsoft Graph Security API.
 */
@Node(
		type = "microsoftGraphSecurity",
		displayName = "Microsoft Graph Security",
		description = "Manage security alerts and secure scores via Microsoft Graph",
		category = "Microsoft",
		icon = "microsoftGraphSecurity",
		credentials = {"microsoftGraphSecurityOAuth2Api"}
)
public class MicrosoftGraphSecurityNode extends AbstractApiNode {

	private static final String BASE_URL = "https://graph.microsoft.com/v1.0/security";

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
				.type(ParameterType.OPTIONS).required(true).defaultValue("alert")
				.noDataExpression(true)
				.options(List.of(
						ParameterOption.builder().name("Alert").value("alert")
								.description("Manage security alerts").build(),
						ParameterOption.builder().name("Secure Score").value("secureScore")
								.description("Get secure scores").build()
				)).build());

		// Alert operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("alert"))))
				.options(List.of(
						ParameterOption.builder().name("Get").value("get").description("Get an alert").build(),
						ParameterOption.builder().name("Get All").value("getAll").description("Get all alerts").build(),
						ParameterOption.builder().name("Update").value("update").description("Update an alert").build()
				)).build());

		// Secure Score operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("secureScore"))))
				.options(List.of(
						ParameterOption.builder().name("Get").value("get").description("Get a secure score").build(),
						ParameterOption.builder().name("Get All").value("getAll").description("Get all secure scores").build()
				)).build());

		// Alert > Get / Update: alertId
		params.add(NodeParameter.builder()
				.name("alertId").displayName("Alert ID")
				.type(ParameterType.STRING).required(true)
				.description("The ID of the alert.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("alert"), "operation", List.of("get", "update"))))
				.build());

		// Alert > Update fields
		params.add(NodeParameter.builder()
				.name("alertUpdateFields").displayName("Update Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("alert"), "operation", List.of("update"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("assignedTo").displayName("Assigned To")
								.type(ParameterType.STRING)
								.description("Name of the analyst the alert is assigned to.").build(),
						NodeParameter.builder().name("status").displayName("Status")
								.type(ParameterType.OPTIONS)
								.options(List.of(
										ParameterOption.builder().name("Unknown").value("unknown").build(),
										ParameterOption.builder().name("New Alert").value("newAlert").build(),
										ParameterOption.builder().name("In Progress").value("inProgress").build(),
										ParameterOption.builder().name("Resolved").value("resolved").build(),
										ParameterOption.builder().name("Dismissed").value("dismissed").build()
								)).build(),
						NodeParameter.builder().name("feedback").displayName("Feedback")
								.type(ParameterType.OPTIONS)
								.options(List.of(
										ParameterOption.builder().name("Unknown").value("unknown").build(),
										ParameterOption.builder().name("True Positive").value("truePositive").build(),
										ParameterOption.builder().name("False Positive").value("falsePositive").build(),
										ParameterOption.builder().name("Benign Positive").value("benignPositive").build()
								)).build(),
						NodeParameter.builder().name("comments").displayName("Comments")
								.type(ParameterType.STRING)
								.typeOptions(Map.of("rows", 3))
								.description("Analyst comments on the alert (appended).").build(),
						NodeParameter.builder().name("closedDateTime").displayName("Closed Date Time")
								.type(ParameterType.STRING)
								.description("Time at which the alert was closed (ISO 8601 format).").build()
				)).build());

		// Alert > GetAll filters
		params.add(NodeParameter.builder()
				.name("alertFilters").displayName("Filters")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("alert"), "operation", List.of("getAll"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("severity").displayName("Severity")
								.type(ParameterType.OPTIONS)
								.options(List.of(
										ParameterOption.builder().name("Unknown").value("unknown").build(),
										ParameterOption.builder().name("Informational").value("informational").build(),
										ParameterOption.builder().name("Low").value("low").build(),
										ParameterOption.builder().name("Medium").value("medium").build(),
										ParameterOption.builder().name("High").value("high").build()
								)).build(),
						NodeParameter.builder().name("status").displayName("Status")
								.type(ParameterType.OPTIONS)
								.options(List.of(
										ParameterOption.builder().name("Unknown").value("unknown").build(),
										ParameterOption.builder().name("New Alert").value("newAlert").build(),
										ParameterOption.builder().name("In Progress").value("inProgress").build(),
										ParameterOption.builder().name("Resolved").value("resolved").build()
								)).build(),
						NodeParameter.builder().name("category").displayName("Category")
								.type(ParameterType.STRING)
								.description("Alert category (e.g., ransomware, malware).").build(),
						NodeParameter.builder().name("vendorName").displayName("Vendor Name")
								.type(ParameterType.STRING)
								.description("Name of the vendor/provider (e.g., Microsoft).").build()
				)).build());

		// Secure Score > Get: secureScoreId
		params.add(NodeParameter.builder()
				.name("secureScoreId").displayName("Secure Score ID")
				.type(ParameterType.STRING).required(true)
				.description("The ID of the secure score.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("secureScore"), "operation", List.of("get"))))
				.build());

		// Limit for getAll
		params.add(NodeParameter.builder()
				.name("limit").displayName("Limit")
				.type(ParameterType.NUMBER).defaultValue(50)
				.description("Maximum number of results to return.")
				.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
				.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "alert");
		Map<String, Object> credentials = context.getCredentials();

		try {
			return switch (resource) {
				case "alert" -> executeAlert(context, credentials);
				case "secureScore" -> executeSecureScore(context, credentials);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Microsoft Graph Security error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeAlert(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		switch (operation) {
			case "get": {
				String alertId = context.getParameter("alertId", "");
				HttpResponse<String> response = get(BASE_URL + "/alerts/" + encode(alertId), headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				Map<String, Object> filters = context.getParameter("alertFilters", Map.of());

				StringBuilder url = new StringBuilder(BASE_URL + "/alerts?$top=" + limit);

				List<String> filterParts = new ArrayList<>();
				if (filters.get("severity") != null) {
					filterParts.add("severity eq '" + filters.get("severity") + "'");
				}
				if (filters.get("status") != null) {
					filterParts.add("status eq '" + filters.get("status") + "'");
				}
				if (filters.get("category") != null) {
					filterParts.add("category eq '" + filters.get("category") + "'");
				}
				if (filters.get("vendorName") != null) {
					filterParts.add("vendorInformation/vendor eq '" + filters.get("vendorName") + "'");
				}
				if (!filterParts.isEmpty()) {
					url.append("&$filter=").append(encode(String.join(" and ", filterParts)));
				}

				HttpResponse<String> response = get(url.toString(), headers);
				Map<String, Object> result = parseResponse(response);

				Object value = result.get("value");
				if (value instanceof List) {
					List<Map<String, Object>> alertItems = new ArrayList<>();
					for (Object alert : (List<?>) value) {
						if (alert instanceof Map) {
							alertItems.add(wrapInJson(alert));
						}
					}
					return alertItems.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(alertItems);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "update": {
				String alertId = context.getParameter("alertId", "");
				Map<String, Object> updateFields = context.getParameter("alertUpdateFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();

				if (updateFields.get("assignedTo") != null) {
					body.put("assignedTo", updateFields.get("assignedTo"));
				}
				if (updateFields.get("status") != null) {
					body.put("status", updateFields.get("status"));
				}
				if (updateFields.get("feedback") != null) {
					body.put("feedback", updateFields.get("feedback"));
				}
				if (updateFields.get("comments") != null) {
					body.put("comments", List.of(updateFields.get("comments")));
				}
				if (updateFields.get("closedDateTime") != null) {
					body.put("closedDateTime", updateFields.get("closedDateTime"));
				}

				// Microsoft Graph Security requires vendor information for updates
				body.put("vendorInformation", Map.of(
						"provider", "Microsoft",
						"vendor", "Microsoft"
				));

				HttpResponse<String> response = patch(BASE_URL + "/alerts/" + encode(alertId), body, headers);
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					// Re-fetch the updated alert
					HttpResponse<String> getResponse = get(BASE_URL + "/alerts/" + encode(alertId), headers);
					Map<String, Object> result = parseResponse(getResponse);
					return NodeExecutionResult.success(List.of(wrapInJson(result)));
				}
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			default:
				return NodeExecutionResult.error("Unknown alert operation: " + operation);
		}
	}

	private NodeExecutionResult executeSecureScore(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		switch (operation) {
			case "get": {
				String secureScoreId = context.getParameter("secureScoreId", "");
				HttpResponse<String> response = get(BASE_URL + "/secureScores/" + encode(secureScoreId), headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				String url = BASE_URL + "/secureScores?$top=" + limit;

				HttpResponse<String> response = get(url, headers);
				Map<String, Object> result = parseResponse(response);

				Object value = result.get("value");
				if (value instanceof List) {
					List<Map<String, Object>> scoreItems = new ArrayList<>();
					for (Object score : (List<?>) value) {
						if (score instanceof Map) {
							scoreItems.add(wrapInJson(score));
						}
					}
					return scoreItems.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(scoreItems);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			default:
				return NodeExecutionResult.error("Unknown secure score operation: " + operation);
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
