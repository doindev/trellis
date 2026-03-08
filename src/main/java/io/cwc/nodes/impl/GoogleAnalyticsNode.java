package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Google Analytics — access Google Analytics reporting and user activity data.
 */
@Node(
		type = "googleAnalytics",
		displayName = "Google Analytics",
		description = "Access Google Analytics data including reports and user activity",
		category = "Analytics",
		icon = "googleAnalytics",
		credentials = {"googleAnalyticsOAuth2Api"}
)
public class GoogleAnalyticsNode extends AbstractApiNode {

	private static final String REPORTING_BASE_URL = "https://analyticsreporting.googleapis.com/v4";
	private static final String MANAGEMENT_BASE_URL = "https://www.googleapis.com/analytics/v3";

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
				.type(ParameterType.OPTIONS).required(true).defaultValue("report")
				.noDataExpression(true)
				.options(List.of(
						ParameterOption.builder().name("Report").value("report")
								.description("Get analytics reports").build(),
						ParameterOption.builder().name("User Activity").value("userActivity")
								.description("Search user activity").build(),
						ParameterOption.builder().name("Account").value("account")
								.description("List analytics accounts (Management API)").build(),
						ParameterOption.builder().name("Property").value("property")
								.description("List web properties for an account (Management API)").build(),
						ParameterOption.builder().name("View").value("view")
								.description("List views (profiles) for a property (Management API)").build()
				)).build());

		// Report operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("get")
				.displayOptions(Map.of("show", Map.of("resource", List.of("report"))))
				.options(List.of(
						ParameterOption.builder().name("Get").value("get")
								.description("Get an analytics report").build()
				)).build());

		// User Activity operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("search")
				.displayOptions(Map.of("show", Map.of("resource", List.of("userActivity"))))
				.options(List.of(
						ParameterOption.builder().name("Search").value("search")
								.description("Search user activity").build()
				)).build());

		// Management resource operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("account", "property", "view"))))
				.options(List.of(
						ParameterOption.builder().name("Get All").value("getAll")
								.description("List all items").build()
				)).build());

		// Management parameters
		params.add(NodeParameter.builder()
				.name("accountId").displayName("Account ID")
				.type(ParameterType.STRING).required(true)
				.description("The Analytics account ID.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("property", "view"))))
				.build());

		params.add(NodeParameter.builder()
				.name("propertyId").displayName("Web Property ID")
				.type(ParameterType.STRING).required(true)
				.description("The web property ID (e.g., UA-XXXXX-Y).")
				.displayOptions(Map.of("show", Map.of("resource", List.of("view"))))
				.build());

		// Report > Get parameters
		params.add(NodeParameter.builder()
				.name("viewId").displayName("View ID")
				.type(ParameterType.STRING).required(true)
				.description("The Analytics view ID to retrieve data from.")
				.placeHolder("ga:123456789")
				.displayOptions(Map.of("show", Map.of("resource", List.of("report"), "operation", List.of("get"))))
				.build());

		params.add(NodeParameter.builder()
				.name("startDate").displayName("Start Date")
				.type(ParameterType.STRING).required(true)
				.defaultValue("7daysAgo")
				.description("Start date for the report (e.g., 7daysAgo, 2024-01-01).")
				.placeHolder("7daysAgo")
				.displayOptions(Map.of("show", Map.of("resource", List.of("report"), "operation", List.of("get"))))
				.build());

		params.add(NodeParameter.builder()
				.name("endDate").displayName("End Date")
				.type(ParameterType.STRING).required(true)
				.defaultValue("today")
				.description("End date for the report (e.g., today, 2024-01-31).")
				.placeHolder("today")
				.displayOptions(Map.of("show", Map.of("resource", List.of("report"), "operation", List.of("get"))))
				.build());

		params.add(NodeParameter.builder()
				.name("metrics").displayName("Metrics")
				.type(ParameterType.STRING).required(true)
				.defaultValue("ga:sessions")
				.description("Comma-separated metrics (e.g., ga:sessions,ga:pageviews,ga:users).")
				.placeHolder("ga:sessions,ga:pageviews")
				.displayOptions(Map.of("show", Map.of("resource", List.of("report"), "operation", List.of("get"))))
				.build());

		params.add(NodeParameter.builder()
				.name("dimensions").displayName("Dimensions")
				.type(ParameterType.STRING)
				.description("Comma-separated dimensions (e.g., ga:date,ga:country).")
				.placeHolder("ga:date,ga:country")
				.displayOptions(Map.of("show", Map.of("resource", List.of("report"), "operation", List.of("get"))))
				.build());

		params.add(NodeParameter.builder()
				.name("additionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("report"), "operation", List.of("get"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("filtersExpression").displayName("Filters Expression")
								.type(ParameterType.STRING)
								.description("A filter expression for the report (e.g., ga:country==US).")
								.build(),
						NodeParameter.builder().name("orderBy").displayName("Order By")
								.type(ParameterType.STRING)
								.description("Metric or dimension to sort by (e.g., ga:sessions).")
								.build(),
						NodeParameter.builder().name("pageSize").displayName("Page Size")
								.type(ParameterType.NUMBER).defaultValue(1000)
								.description("Maximum number of rows to return.")
								.build(),
						NodeParameter.builder().name("includeEmptyRows").displayName("Include Empty Rows")
								.type(ParameterType.BOOLEAN).defaultValue(false)
								.build()
				)).build());

		// User Activity > Search parameters
		params.add(NodeParameter.builder()
				.name("userActivityViewId").displayName("View ID")
				.type(ParameterType.STRING).required(true)
				.description("The Analytics view ID.")
				.placeHolder("123456789")
				.displayOptions(Map.of("show", Map.of("resource", List.of("userActivity"), "operation", List.of("search"))))
				.build());

		params.add(NodeParameter.builder()
				.name("userId").displayName("User ID")
				.type(ParameterType.STRING).required(true)
				.description("The Analytics user ID to search for.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("userActivity"), "operation", List.of("search"))))
				.build());

		params.add(NodeParameter.builder()
				.name("activityStartDate").displayName("Start Date")
				.type(ParameterType.STRING).required(true)
				.defaultValue("7daysAgo")
				.displayOptions(Map.of("show", Map.of("resource", List.of("userActivity"), "operation", List.of("search"))))
				.build());

		params.add(NodeParameter.builder()
				.name("activityEndDate").displayName("End Date")
				.type(ParameterType.STRING).required(true)
				.defaultValue("today")
				.displayOptions(Map.of("show", Map.of("resource", List.of("userActivity"), "operation", List.of("search"))))
				.build());

		params.add(NodeParameter.builder()
				.name("activityTypes").displayName("Activity Types")
				.type(ParameterType.MULTI_OPTIONS)
				.description("Filter by activity types.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("userActivity"), "operation", List.of("search"))))
				.options(List.of(
						ParameterOption.builder().name("Pageview").value("PAGEVIEW").build(),
						ParameterOption.builder().name("Screenview").value("SCREENVIEW").build(),
						ParameterOption.builder().name("Goal").value("GOAL").build(),
						ParameterOption.builder().name("Ecommerce").value("ECOMMERCE").build(),
						ParameterOption.builder().name("Event").value("EVENT").build()
				)).build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "report");
		Map<String, Object> credentials = context.getCredentials();

		try {
			return switch (resource) {
				case "report" -> executeReport(context, credentials);
				case "userActivity" -> executeUserActivity(context, credentials);
				case "account" -> executeListAccounts(credentials);
				case "property" -> executeListProperties(context, credentials);
				case "view" -> executeListViews(context, credentials);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Google Analytics error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeReport(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		String viewId = context.getParameter("viewId", "");
		String startDate = context.getParameter("startDate", "7daysAgo");
		String endDate = context.getParameter("endDate", "today");
		String metrics = context.getParameter("metrics", "ga:sessions");
		String dimensions = context.getParameter("dimensions", "");
		Map<String, Object> additionalFields = context.getParameter("additionalFields", Map.of());

		// Build the report request
		Map<String, Object> dateRange = new LinkedHashMap<>();
		dateRange.put("startDate", startDate);
		dateRange.put("endDate", endDate);

		List<Map<String, Object>> metricsList = new ArrayList<>();
		for (String metric : metrics.split(",")) {
			metricsList.add(Map.of("expression", metric.trim()));
		}

		Map<String, Object> reportRequest = new LinkedHashMap<>();
		reportRequest.put("viewId", viewId);
		reportRequest.put("dateRanges", List.of(dateRange));
		reportRequest.put("metrics", metricsList);

		if (dimensions != null && !dimensions.isEmpty()) {
			List<Map<String, Object>> dimensionsList = new ArrayList<>();
			for (String dimension : dimensions.split(",")) {
				dimensionsList.add(Map.of("name", dimension.trim()));
			}
			reportRequest.put("dimensions", dimensionsList);
		}

		if (additionalFields.get("filtersExpression") != null) {
			reportRequest.put("filtersExpression", additionalFields.get("filtersExpression"));
		}
		if (additionalFields.get("orderBy") != null) {
			reportRequest.put("orderBys", List.of(Map.of("fieldName", additionalFields.get("orderBy"), "sortOrder", "DESCENDING")));
		}
		if (additionalFields.get("pageSize") != null) {
			reportRequest.put("pageSize", toInt(additionalFields.get("pageSize"), 1000));
		}
		if (additionalFields.get("includeEmptyRows") != null) {
			reportRequest.put("includeEmptyRows", toBoolean(additionalFields.get("includeEmptyRows"), false));
		}

		Map<String, Object> body = Map.of("reportRequests", List.of(reportRequest));

		HttpResponse<String> response = post(REPORTING_BASE_URL + "/reports:batchGet", body, headers);
		Map<String, Object> result = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private NodeExecutionResult executeUserActivity(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		String viewId = context.getParameter("userActivityViewId", "");
		String userId = context.getParameter("userId", "");
		String startDate = context.getParameter("activityStartDate", "7daysAgo");
		String endDate = context.getParameter("activityEndDate", "today");
		List<String> activityTypes = context.getParameter("activityTypes", List.of());

		Map<String, Object> dateRange = new LinkedHashMap<>();
		dateRange.put("startDate", startDate);
		dateRange.put("endDate", endDate);

		Map<String, Object> user = Map.of("type", "USER_ID", "userId", userId);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("viewId", viewId);
		body.put("user", user);
		body.put("dateRange", dateRange);

		if (activityTypes != null && !activityTypes.isEmpty()) {
			body.put("activityTypes", activityTypes);
		}

		HttpResponse<String> response = post(REPORTING_BASE_URL + "/userActivity:search", body, headers);
		Map<String, Object> result = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private NodeExecutionResult executeListAccounts(Map<String, Object> credentials) throws Exception {
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);
		HttpResponse<String> response = get(MANAGEMENT_BASE_URL + "/management/accountSummaries", headers);
		Map<String, Object> result = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private NodeExecutionResult executeListProperties(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		String accountId = context.getParameter("accountId", "");
		Map<String, String> headers = getAuthHeaders(accessToken);
		HttpResponse<String> response = get(MANAGEMENT_BASE_URL + "/management/accounts/" + encode(accountId) + "/webproperties", headers);
		Map<String, Object> result = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private NodeExecutionResult executeListViews(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		String accountId = context.getParameter("accountId", "");
		String propertyId = context.getParameter("propertyId", "");
		Map<String, String> headers = getAuthHeaders(accessToken);
		HttpResponse<String> response = get(MANAGEMENT_BASE_URL + "/management/accounts/" + encode(accountId)
				+ "/webproperties/" + encode(propertyId) + "/profiles", headers);
		Map<String, Object> result = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private Map<String, String> getAuthHeaders(String accessToken) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}
}
