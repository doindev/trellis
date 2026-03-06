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
 * Google Firebase Realtime Database Node -- create, read, update, delete,
 * and push data via the Firebase Realtime Database REST API.
 */
@Slf4j
@Node(
	type = "googleFirebaseRealtimeDatabase",
	displayName = "Google Cloud Realtime Database",
	description = "Manage data in Google Firebase Realtime Database",
	category = "Data & Storage / Databases",
	icon = "googleFirebaseRealtimeDatabase",
	credentials = {"googleFirebaseRealtimeDatabaseOAuth2Api"}
)
public class GoogleFirebaseRealtimeDatabaseNode extends AbstractApiNode {

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

		// Operation selector
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Write data to a defined path, replacing any existing data at that path").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete data at a defined path").build(),
				ParameterOption.builder().name("Get").value("get").description("Read data from a defined path").build(),
				ParameterOption.builder().name("Push").value("push").description("Append data to a list at a defined path (auto-generates a unique key)").build(),
				ParameterOption.builder().name("Update").value("update").description("Update fields at a defined path without replacing the entire node").build()
			)).build());

		// Project ID
		params.add(NodeParameter.builder()
			.name("projectId").displayName("Project ID")
			.type(ParameterType.STRING).required(true)
			.description("The Firebase project ID. Used to construct the database URL.")
			.build());

		// Path
		params.add(NodeParameter.builder()
			.name("path").displayName("Path")
			.type(ParameterType.STRING).required(true)
			.description("The path in the database to operate on (e.g., 'users/123' or 'messages').")
			.placeHolder("users/123")
			.build());

		// Data JSON (for create, push, update)
		params.add(NodeParameter.builder()
			.name("dataJson").displayName("Data (JSON)")
			.type(ParameterType.JSON).required(true)
			.description("The data to write as a JSON object.")
			.placeHolder("{\"name\": \"John\", \"email\": \"john@example.com\"}")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create", "push", "update"))))
			.build());

		// Get options
		params.add(NodeParameter.builder()
			.name("getOptions").displayName("Options")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("operation", List.of("get"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("shallow").displayName("Shallow")
					.type(ParameterType.BOOLEAN).defaultValue(false)
					.description("If true, data at sub-paths is not included (returns just keys with true values).").build(),
				NodeParameter.builder().name("orderBy").displayName("Order By")
					.type(ParameterType.STRING)
					.description("A child key to order results by. Use '$key', '$value', or '$priority' for special orderings.")
					.build(),
				NodeParameter.builder().name("limitToFirst").displayName("Limit To First")
					.type(ParameterType.NUMBER)
					.description("Return only the first N results.").build(),
				NodeParameter.builder().name("limitToLast").displayName("Limit To Last")
					.type(ParameterType.NUMBER)
					.description("Return only the last N results.").build()
			)).build());

		return params;
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String operation = context.getParameter("operation", "get");

		try {
			String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));
			Map<String, String> headers = getAuthHeaders(accessToken);

			String projectId = context.getParameter("projectId", "");
			String path = context.getParameter("path", "");
			String baseUrl = "https://" + projectId + "-default-rtdb.firebaseio.com";
			String dataUrl = baseUrl + "/" + path + ".json";

			return switch (operation) {
				case "create" -> executeCreate(context, dataUrl, headers);
				case "delete" -> executeDeleteOp(dataUrl, headers);
				case "get" -> executeGet(context, dataUrl, headers);
				case "push" -> executePush(context, dataUrl, headers);
				case "update" -> executeUpdate(context, dataUrl, headers);
				default -> NodeExecutionResult.error("Unknown operation: " + operation);
			};
		} catch (Exception e) {
			return handleError(context, "Google Firebase Realtime Database error: " + e.getMessage(), e);
		}
	}

	// ========================= Operations =========================

	private NodeExecutionResult executeCreate(NodeExecutionContext context, String url,
			Map<String, String> headers) throws Exception {
		String dataJson = context.getParameter("dataJson", "{}");
		Map<String, Object> data = parseJsonObject(dataJson);

		HttpResponse<String> response = put(url, data, headers);
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult executeDeleteOp(String url, Map<String, String> headers) throws Exception {
		HttpResponse<String> response = delete(url, headers);
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true))));
	}

	private NodeExecutionResult executeGet(NodeExecutionContext context, String url,
			Map<String, String> headers) throws Exception {
		Map<String, Object> options = context.getParameter("getOptions", Map.of());
		Map<String, Object> queryParams = new LinkedHashMap<>();

		if (toBoolean(options.get("shallow"), false)) {
			queryParams.put("shallow", "true");
		}
		if (options.get("orderBy") != null) {
			queryParams.put("orderBy", "\"" + options.get("orderBy") + "\"");
		}
		if (options.get("limitToFirst") != null) {
			queryParams.put("limitToFirst", options.get("limitToFirst"));
		}
		if (options.get("limitToLast") != null) {
			queryParams.put("limitToLast", options.get("limitToLast"));
		}

		String fullUrl = buildUrl(url, queryParams);
		HttpResponse<String> response = get(fullUrl, headers);
		if (response.statusCode() >= 400) {
			return apiError(response);
		}

		String body = response.body();
		if (body == null || body.isBlank() || "null".equals(body.trim())) {
			return NodeExecutionResult.empty();
		}

		// Firebase can return either an object or an array
		if (body.trim().startsWith("[")) {
			List<Map<String, Object>> items = parseArrayResponse(response);
			List<Map<String, Object>> results = new ArrayList<>();
			for (Map<String, Object> item : items) {
				results.add(wrapInJson(item));
			}
			return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
		}

		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult executePush(NodeExecutionContext context, String url,
			Map<String, String> headers) throws Exception {
		String dataJson = context.getParameter("dataJson", "{}");
		Map<String, Object> data = parseJsonObject(dataJson);

		HttpResponse<String> response = post(url, data, headers);
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult executeUpdate(NodeExecutionContext context, String url,
			Map<String, String> headers) throws Exception {
		String dataJson = context.getParameter("dataJson", "{}");
		Map<String, Object> data = parseJsonObject(dataJson);

		HttpResponse<String> response = patch(url, data, headers);
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(String accessToken) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Firebase Realtime Database API error (HTTP " + response.statusCode() + "): " + body);
	}
}
