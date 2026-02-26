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
 * Splunk Node -- create searches, retrieve search results, and fire alerts
 * via the Splunk REST API.
 */
@Slf4j
@Node(
	type = "splunk",
	displayName = "Splunk",
	description = "Create searches, retrieve results, and fire alerts in Splunk",
	category = "Miscellaneous",
	icon = "splunk",
	credentials = {"splunkApi"}
)
public class SplunkNode extends AbstractApiNode {

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("search")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Search").value("search").description("Create and manage searches").build(),
				ParameterOption.builder().name("Alert").value("alert").description("Manage fired alerts").build()
			)).build());

		// Search operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("search"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a search job").build(),
				ParameterOption.builder().name("Get Results").value("getResults").description("Get search job results").build()
			)).build());

		// Alert operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("fire")
			.displayOptions(Map.of("show", Map.of("resource", List.of("alert"))))
			.options(List.of(
				ParameterOption.builder().name("Fire").value("fire").description("Fire a saved search alert").build()
			)).build());

		// Search parameters
		addSearchParameters(params);

		// Alert parameters
		addAlertParameters(params);

		return params;
	}

	// ========================= Search Parameters =========================

	private void addSearchParameters(List<NodeParameter> params) {
		// Search > Create
		params.add(NodeParameter.builder()
			.name("searchQuery").displayName("Search Query").type(ParameterType.STRING).required(true)
			.placeHolder("search index=main error | head 10")
			.description("The Splunk search query. Must start with 'search' or '|'.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("search"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("searchAdditionalFields").displayName("Additional Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("search"), "operation", List.of("create"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("earliestTime").displayName("Earliest Time").type(ParameterType.STRING)
					.placeHolder("-24h").description("Earliest time for the search (e.g., -24h, -7d, 2024-01-01T00:00:00).").build(),
				NodeParameter.builder().name("latestTime").displayName("Latest Time").type(ParameterType.STRING)
					.placeHolder("now").description("Latest time for the search (e.g., now, -1h).").build(),
				NodeParameter.builder().name("execMode").displayName("Execution Mode").type(ParameterType.OPTIONS)
					.defaultValue("normal")
					.options(List.of(
						ParameterOption.builder().name("Normal").value("normal").description("Asynchronous search").build(),
						ParameterOption.builder().name("Blocking").value("blocking").description("Synchronous search that waits for completion").build(),
						ParameterOption.builder().name("One Shot").value("oneshot").description("Run and return results immediately").build()
					)).build(),
				NodeParameter.builder().name("maxCount").displayName("Max Results").type(ParameterType.NUMBER)
					.defaultValue(100).description("Maximum number of results to return.").build()
			)).build());

		// Search > Get Results
		params.add(NodeParameter.builder()
			.name("searchJobId").displayName("Search Job ID (SID)").type(ParameterType.STRING).required(true)
			.description("The search job ID returned from a search create operation.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("search"), "operation", List.of("getResults"))))
			.build());

		params.add(NodeParameter.builder()
			.name("searchResultCount").displayName("Result Count").type(ParameterType.NUMBER).defaultValue(100)
			.description("Number of results to return.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("search"), "operation", List.of("getResults"))))
			.build());

		params.add(NodeParameter.builder()
			.name("searchResultOffset").displayName("Result Offset").type(ParameterType.NUMBER).defaultValue(0)
			.description("Offset index for results.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("search"), "operation", List.of("getResults"))))
			.build());
	}

	// ========================= Alert Parameters =========================

	private void addAlertParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("alertName").displayName("Saved Search Name").type(ParameterType.STRING).required(true)
			.description("The name of the saved search to dispatch/fire.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("alert"), "operation", List.of("fire"))))
			.build());

		params.add(NodeParameter.builder()
			.name("alertDispatchFields").displayName("Dispatch Options")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("alert"), "operation", List.of("fire"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("triggerActions").displayName("Trigger Actions").type(ParameterType.BOOLEAN)
					.defaultValue(true).description("Whether to trigger alert actions.").build(),
				NodeParameter.builder().name("earliestTime").displayName("Earliest Time").type(ParameterType.STRING)
					.placeHolder("-24h").build(),
				NodeParameter.builder().name("latestTime").displayName("Latest Time").type(ParameterType.STRING)
					.placeHolder("now").build()
			)).build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "search");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String baseUrl = getApiBaseUrl(credentials);
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (resource) {
				case "search" -> executeSearch(context, baseUrl, headers);
				case "alert" -> executeAlert(context, baseUrl, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Splunk API error: " + e.getMessage(), e);
		}
	}

	// ========================= Search Execute =========================

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeSearch(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "create");

		switch (operation) {
			case "create": {
				String query = context.getParameter("searchQuery", "");
				Map<String, Object> additional = context.getParameter("searchAdditionalFields", Map.of());

				// Splunk search API expects form-encoded data
				StringBuilder formBody = new StringBuilder();
				formBody.append("search=").append(encode(query));

				String earliestTime = String.valueOf(additional.getOrDefault("earliestTime", ""));
				if (!earliestTime.isEmpty()) {
					formBody.append("&earliest_time=").append(encode(earliestTime));
				}
				String latestTime = String.valueOf(additional.getOrDefault("latestTime", ""));
				if (!latestTime.isEmpty()) {
					formBody.append("&latest_time=").append(encode(latestTime));
				}
				String execMode = String.valueOf(additional.getOrDefault("execMode", "normal"));
				formBody.append("&exec_mode=").append(encode(execMode));

				Object maxCount = additional.get("maxCount");
				if (maxCount != null) {
					formBody.append("&max_count=").append(encode(String.valueOf(maxCount)));
				}

				formBody.append("&output_mode=json");

				Map<String, String> formHeaders = new LinkedHashMap<>(headers);
				formHeaders.put("Content-Type", "application/x-www-form-urlencoded");

				HttpResponse<String> response = post(baseUrl + "/services/search/jobs", formBody.toString(), formHeaders);
				if (response.statusCode() >= 400) {
					return apiError(response);
				}
				Map<String, Object> parsed = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			case "getResults": {
				String jobId = context.getParameter("searchJobId", "");
				int count = toInt(context.getParameter("searchResultCount", 100), 100);
				int offset = toInt(context.getParameter("searchResultOffset", 0), 0);

				Map<String, Object> queryParams = new LinkedHashMap<>();
				queryParams.put("output_mode", "json");
				queryParams.put("count", count);
				queryParams.put("offset", offset);

				String url = buildUrl(baseUrl + "/services/search/jobs/" + encode(jobId) + "/results", queryParams);
				HttpResponse<String> response = get(url, headers);

				if (response.statusCode() >= 400) {
					return apiError(response);
				}

				Map<String, Object> parsed = parseResponse(response);
				Object results = parsed.getOrDefault("results", List.of());
				if (results instanceof List) {
					List<Map<String, Object>> items = new ArrayList<>();
					for (Object item : (List<?>) results) {
						if (item instanceof Map) {
							items.add(wrapInJson(item));
						}
					}
					return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			default:
				return NodeExecutionResult.error("Unknown search operation: " + operation);
		}
	}

	// ========================= Alert Execute =========================

	private NodeExecutionResult executeAlert(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "fire");

		if ("fire".equals(operation)) {
			String alertName = context.getParameter("alertName", "");
			Map<String, Object> dispatchFields = context.getParameter("alertDispatchFields", Map.of());

			StringBuilder formBody = new StringBuilder();
			formBody.append("output_mode=json");

			boolean triggerActions = toBoolean(dispatchFields.get("triggerActions"), true);
			formBody.append("&dispatch.triggerAlertActions=").append(triggerActions);

			String earliestTime = String.valueOf(dispatchFields.getOrDefault("earliestTime", ""));
			if (!earliestTime.isEmpty()) {
				formBody.append("&dispatch.earliest_time=").append(encode(earliestTime));
			}
			String latestTime = String.valueOf(dispatchFields.getOrDefault("latestTime", ""));
			if (!latestTime.isEmpty()) {
				formBody.append("&dispatch.latest_time=").append(encode(latestTime));
			}

			Map<String, String> formHeaders = new LinkedHashMap<>(headers);
			formHeaders.put("Content-Type", "application/x-www-form-urlencoded");

			String url = baseUrl + "/services/saved/searches/" + encode(alertName) + "/dispatch";
			HttpResponse<String> response = post(url, formBody.toString(), formHeaders);

			if (response.statusCode() >= 400) {
				return apiError(response);
			}
			Map<String, Object> parsed = parseResponse(response);
			return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
		}

		return NodeExecutionResult.error("Unknown alert operation: " + operation);
	}

	// ========================= Helpers =========================

	@Override
	protected String getBaseUrl(Map<String, Object> credentials) {
		return getApiBaseUrl(credentials);
	}

	private String getApiBaseUrl(Map<String, Object> credentials) {
		String baseUrl = String.valueOf(credentials.getOrDefault("baseUrl", "https://localhost:8089"));
		if (baseUrl.endsWith("/")) {
			baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		}
		return baseUrl;
	}

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		String authToken = String.valueOf(credentials.getOrDefault("authToken", ""));
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + authToken);
		headers.put("Accept", "application/json");
		return headers;
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Splunk API error (HTTP " + response.statusCode() + "): " + body);
	}
}
