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
 * Supabase Node -- manage rows in Supabase tables via the PostgREST API.
 * Supports create, read, update, and delete operations.
 */
@Slf4j
@Node(
	type = "supabase",
	displayName = "Supabase",
	description = "Manage rows in Supabase tables via the PostgREST API",
	category = "Data & Storage / Databases",
	icon = "supabase",
	credentials = {"supabaseApi"}
)
public class SupabaseNode extends AbstractApiNode {

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("row")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Row").value("row").description("Manage rows in a table").build()
			)).build());

		// Operation selector
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("row"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a new row").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete rows").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a row by ID").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many rows").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a row").build()
			)).build());

		// Common parameters
		params.add(NodeParameter.builder()
			.name("table").displayName("Table")
			.type(ParameterType.STRING).required(true)
			.description("The name of the table.")
			.build());

		params.add(NodeParameter.builder()
			.name("rowId").displayName("Row ID")
			.type(ParameterType.STRING)
			.description("The ID of the row (primary key value).")
			.displayOptions(Map.of("show", Map.of("operation", List.of("get", "update", "delete"))))
			.build());

		params.add(NodeParameter.builder()
			.name("idColumn").displayName("ID Column")
			.type(ParameterType.STRING).defaultValue("id")
			.description("The name of the primary key column.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("get", "update", "delete"))))
			.build());

		params.add(NodeParameter.builder()
			.name("rowData").displayName("Row Data (JSON)")
			.type(ParameterType.JSON).required(true).defaultValue("{}")
			.description("The row data as JSON object.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create", "update"))))
			.build());

		// Filter for getAll
		params.add(NodeParameter.builder()
			.name("filters").displayName("Filters")
			.type(ParameterType.STRING)
			.description("PostgREST filter query string, e.g., 'status=eq.active&age=gt.18'.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("select").displayName("Select Columns")
			.type(ParameterType.STRING).defaultValue("*")
			.description("Comma-separated list of columns to return.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("get", "getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("orderBy").displayName("Order By")
			.type(ParameterType.STRING)
			.description("Column to order by, e.g., 'created_at.desc'.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit")
			.type(ParameterType.NUMBER).defaultValue(100)
			.description("Max number of rows to return.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("returnAll").displayName("Return All")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Whether to return all results.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String operation = context.getParameter("operation", "getAll");
		Map<String, Object> credentials = context.getCredentials();
		String table = context.getParameter("table", "");

		try {
			String baseUrl = getSupabaseBaseUrl(credentials);
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (operation) {
				case "create" -> executeCreate(context, baseUrl, table, headers);
				case "delete" -> executeDelete(context, baseUrl, table, headers);
				case "get" -> executeGet(context, baseUrl, table, headers);
				case "getAll" -> executeGetAll(context, baseUrl, table, headers);
				case "update" -> executeUpdate(context, baseUrl, table, headers);
				default -> NodeExecutionResult.error("Unknown operation: " + operation);
			};
		} catch (Exception e) {
			return handleError(context, "Supabase API error: " + e.getMessage(), e);
		}
	}

	// ========================= Operations =========================

	private NodeExecutionResult executeCreate(NodeExecutionContext context, String baseUrl,
			String table, Map<String, String> headers) throws Exception {
		String rowDataJson = context.getParameter("rowData", "{}");
		Map<String, Object> rowData = parseJson(rowDataJson);

		Map<String, String> createHeaders = new LinkedHashMap<>(headers);
		createHeaders.put("Prefer", "return=representation");

		String url = baseUrl + "/rest/v1/" + encode(table);
		HttpResponse<String> response = post(url, rowData, createHeaders);
		return toResult(response);
	}

	private NodeExecutionResult executeDelete(NodeExecutionContext context, String baseUrl,
			String table, Map<String, String> headers) throws Exception {
		String rowId = context.getParameter("rowId", "");
		String idColumn = context.getParameter("idColumn", "id");

		String url = baseUrl + "/rest/v1/" + encode(table) + "?" + encode(idColumn) + "=eq." + encode(rowId);
		HttpResponse<String> response = delete(url, headers);

		if (response.statusCode() >= 400) {
			return supabaseError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "id", rowId))));
	}

	private NodeExecutionResult executeGet(NodeExecutionContext context, String baseUrl,
			String table, Map<String, String> headers) throws Exception {
		String rowId = context.getParameter("rowId", "");
		String idColumn = context.getParameter("idColumn", "id");
		String select = context.getParameter("select", "*");

		String url = baseUrl + "/rest/v1/" + encode(table)
			+ "?select=" + encode(select)
			+ "&" + encode(idColumn) + "=eq." + encode(rowId);
		HttpResponse<String> response = get(url, headers);
		return toResult(response);
	}

	private NodeExecutionResult executeGetAll(NodeExecutionContext context, String baseUrl,
			String table, Map<String, String> headers) throws Exception {
		String select = context.getParameter("select", "*");
		String filters = context.getParameter("filters", "");
		String orderBy = context.getParameter("orderBy", "");
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 100);

		StringBuilder url = new StringBuilder(baseUrl + "/rest/v1/" + encode(table));
		url.append("?select=").append(encode(select));

		if (!filters.isEmpty()) {
			url.append("&").append(filters);
		}
		if (!orderBy.isEmpty()) {
			url.append("&order=").append(encode(orderBy));
		}

		Map<String, String> getAllHeaders = new LinkedHashMap<>(headers);
		if (!returnAll) {
			int effectiveLimit = Math.min(limit, 1000);
			getAllHeaders.put("Range", "0-" + (effectiveLimit - 1));
			getAllHeaders.put("Range-Unit", "items");
		}

		HttpResponse<String> response = get(url.toString(), getAllHeaders);

		if (response.statusCode() >= 400) {
			return supabaseError(response);
		}

		List<Map<String, Object>> items = parseArrayResponse(response);
		if (items.isEmpty()) {
			return NodeExecutionResult.empty();
		}

		List<Map<String, Object>> results = new ArrayList<>();
		for (Map<String, Object> item : items) {
			results.add(wrapInJson(item));
		}
		return NodeExecutionResult.success(results);
	}

	private NodeExecutionResult executeUpdate(NodeExecutionContext context, String baseUrl,
			String table, Map<String, String> headers) throws Exception {
		String rowId = context.getParameter("rowId", "");
		String idColumn = context.getParameter("idColumn", "id");
		String rowDataJson = context.getParameter("rowData", "{}");
		Map<String, Object> rowData = parseJson(rowDataJson);

		Map<String, String> updateHeaders = new LinkedHashMap<>(headers);
		updateHeaders.put("Prefer", "return=representation");

		String url = baseUrl + "/rest/v1/" + encode(table) + "?" + encode(idColumn) + "=eq." + encode(rowId);
		HttpResponse<String> response = patch(url, rowData, updateHeaders);
		return toResult(response);
	}

	// ========================= Helpers =========================

	private String getSupabaseBaseUrl(Map<String, Object> credentials) {
		String baseUrl = String.valueOf(credentials.getOrDefault("url",
			credentials.getOrDefault("baseUrl",
				credentials.getOrDefault("supabaseUrl", ""))));
		if (baseUrl.endsWith("/")) {
			baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		}
		return baseUrl;
	}

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		String apiKey = String.valueOf(credentials.getOrDefault("apiKey",
			credentials.getOrDefault("serviceRoleKey", "")));
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("apikey", apiKey);
		headers.put("Authorization", "Bearer " + apiKey);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return supabaseError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("statusCode", response.statusCode()))));
		}

		// PostgREST returns arrays
		if (body.trim().startsWith("[")) {
			List<Map<String, Object>> items = parseArrayResponse(response);
			if (items.isEmpty()) {
				return NodeExecutionResult.success(List.of(wrapInJson(Map.of("statusCode", response.statusCode()))));
			}
			List<Map<String, Object>> results = new ArrayList<>();
			for (Map<String, Object> item : items) {
				results.add(wrapInJson(item));
			}
			return NodeExecutionResult.success(results);
		}

		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult supabaseError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Supabase API error (HTTP " + response.statusCode() + "): " + body);
	}
}
