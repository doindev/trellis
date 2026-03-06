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
 * Google BigQuery Node -- manage datasets, tables, and records via the BigQuery REST API.
 */
@Slf4j
@Node(
	type = "googleBigQuery",
	displayName = "Google BigQuery",
	description = "Manage Google BigQuery datasets, tables, and records",
	category = "Data & Storage / Databases",
	icon = "googleBigQuery",
	credentials = {"googleBigQueryOAuth2Api"}
)
public class GoogleBigQueryNode extends AbstractApiNode {

	private static final String BASE_URL = "https://bigquery.googleapis.com/bigquery/v2";

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("dataset")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Dataset").value("dataset").description("Manage datasets").build(),
				ParameterOption.builder().name("Table").value("table").description("Manage tables").build(),
				ParameterOption.builder().name("Record").value("record").description("Manage records").build()
			)).build());

		// Operation selectors
		addOperationSelectors(params);

		// Resource-specific parameters
		addDatasetParameters(params);
		addTableParameters(params);
		addRecordParameters(params);

		return params;
	}

	// ========================= Operation Selectors =========================

	private void addOperationSelectors(List<NodeParameter> params) {
		// Dataset operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("dataset"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get a dataset").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all datasets").build()
			)).build());

		// Table operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("table"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get a table").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all tables in a dataset").build()
			)).build());

		// Record operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("record"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Insert rows into a table").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Query records from a table").build()
			)).build());
	}

	// ========================= Dataset Parameters =========================

	private void addDatasetParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("projectId").displayName("Project ID")
			.type(ParameterType.STRING).required(true)
			.description("The Google Cloud project ID.")
			.build());

		// Dataset > Get: dataset ID
		params.add(NodeParameter.builder()
			.name("datasetId").displayName("Dataset ID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("dataset"), "operation", List.of("get"))))
			.build());

		// Dataset > GetAll: filters
		params.add(NodeParameter.builder()
			.name("datasetFilters").displayName("Filters")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("dataset"), "operation", List.of("getAll"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("maxResults").displayName("Max Results").type(ParameterType.NUMBER)
					.defaultValue(50).description("Maximum number of datasets to return.").build(),
				NodeParameter.builder().name("all").displayName("All").type(ParameterType.BOOLEAN)
					.defaultValue(false).description("Whether to list all datasets, including hidden ones.").build()
			)).build());
	}

	// ========================= Table Parameters =========================

	private void addTableParameters(List<NodeParameter> params) {
		// Table: dataset ID (required for all table operations)
		params.add(NodeParameter.builder()
			.name("tableDatasetId").displayName("Dataset ID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("table"))))
			.build());

		// Table > Get: table ID
		params.add(NodeParameter.builder()
			.name("tableId").displayName("Table ID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("table"), "operation", List.of("get"))))
			.build());

		// Table > GetAll: filters
		params.add(NodeParameter.builder()
			.name("tableFilters").displayName("Filters")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("table"), "operation", List.of("getAll"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("maxResults").displayName("Max Results").type(ParameterType.NUMBER)
					.defaultValue(50).description("Maximum number of tables to return.").build()
			)).build());
	}

	// ========================= Record Parameters =========================

	private void addRecordParameters(List<NodeParameter> params) {
		// Record: dataset ID
		params.add(NodeParameter.builder()
			.name("recordDatasetId").displayName("Dataset ID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("record"))))
			.build());

		// Record: table ID
		params.add(NodeParameter.builder()
			.name("recordTableId").displayName("Table ID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("record"))))
			.build());

		// Record > Create: rows JSON
		params.add(NodeParameter.builder()
			.name("rowsJson").displayName("Rows (JSON)")
			.type(ParameterType.JSON).required(true)
			.description("JSON array of row objects to insert. Each object should have keys matching the table columns.")
			.placeHolder("[{\"column1\": \"value1\", \"column2\": \"value2\"}]")
			.displayOptions(Map.of("show", Map.of("resource", List.of("record"), "operation", List.of("create"))))
			.build());

		// Record > GetAll: SQL query
		params.add(NodeParameter.builder()
			.name("sqlQuery").displayName("SQL Query")
			.type(ParameterType.STRING).required(true)
			.typeOptions(Map.of("rows", 5))
			.placeHolder("SELECT * FROM `project.dataset.table` LIMIT 100")
			.description("The Google Standard SQL query to run.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("record"), "operation", List.of("getAll"))))
			.build());

		// Record > GetAll: options
		params.add(NodeParameter.builder()
			.name("recordOptions").displayName("Options")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("record"), "operation", List.of("getAll"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("useLegacySql").displayName("Use Legacy SQL").type(ParameterType.BOOLEAN)
					.defaultValue(false).description("Whether to use legacy SQL dialect.").build(),
				NodeParameter.builder().name("maxResults").displayName("Max Results").type(ParameterType.NUMBER)
					.defaultValue(100).description("Maximum number of rows to return.").build(),
				NodeParameter.builder().name("location").displayName("Location").type(ParameterType.STRING)
					.description("The geographic location where the job should run (e.g., US, EU).").build()
			)).build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "dataset");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));
			Map<String, String> headers = getAuthHeaders(accessToken);
			String projectId = context.getParameter("projectId", "");

			return switch (resource) {
				case "dataset" -> executeDataset(context, projectId, headers);
				case "table" -> executeTable(context, projectId, headers);
				case "record" -> executeRecord(context, projectId, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Google BigQuery API error: " + e.getMessage(), e);
		}
	}

	// ========================= Dataset Execute =========================

	private NodeExecutionResult executeDataset(NodeExecutionContext context, String projectId,
			Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "get": {
				String datasetId = context.getParameter("datasetId", "");
				String url = BASE_URL + "/projects/" + encode(projectId) + "/datasets/" + encode(datasetId);
				HttpResponse<String> response = get(url, headers);
				if (response.statusCode() >= 400) {
					return apiError(response);
				}
				Map<String, Object> parsed = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			case "getAll": {
				Map<String, Object> filters = context.getParameter("datasetFilters", Map.of());
				Map<String, Object> queryParams = new LinkedHashMap<>();
				if (filters.get("maxResults") != null) queryParams.put("maxResults", filters.get("maxResults"));
				if (Boolean.TRUE.equals(filters.get("all"))) queryParams.put("all", "true");

				String url = buildUrl(BASE_URL + "/projects/" + encode(projectId) + "/datasets", queryParams);
				HttpResponse<String> response = get(url, headers);
				if (response.statusCode() >= 400) {
					return apiError(response);
				}
				Map<String, Object> parsed = parseResponse(response);
				return extractListResult(parsed, "datasets");
			}
			default:
				return NodeExecutionResult.error("Unknown dataset operation: " + operation);
		}
	}

	// ========================= Table Execute =========================

	private NodeExecutionResult executeTable(NodeExecutionContext context, String projectId,
			Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		String datasetId = context.getParameter("tableDatasetId", "");

		switch (operation) {
			case "get": {
				String tableId = context.getParameter("tableId", "");
				String url = BASE_URL + "/projects/" + encode(projectId) + "/datasets/" + encode(datasetId)
					+ "/tables/" + encode(tableId);
				HttpResponse<String> response = get(url, headers);
				if (response.statusCode() >= 400) {
					return apiError(response);
				}
				Map<String, Object> parsed = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			case "getAll": {
				Map<String, Object> filters = context.getParameter("tableFilters", Map.of());
				Map<String, Object> queryParams = new LinkedHashMap<>();
				if (filters.get("maxResults") != null) queryParams.put("maxResults", filters.get("maxResults"));

				String url = buildUrl(
					BASE_URL + "/projects/" + encode(projectId) + "/datasets/" + encode(datasetId) + "/tables",
					queryParams);
				HttpResponse<String> response = get(url, headers);
				if (response.statusCode() >= 400) {
					return apiError(response);
				}
				Map<String, Object> parsed = parseResponse(response);
				return extractListResult(parsed, "tables");
			}
			default:
				return NodeExecutionResult.error("Unknown table operation: " + operation);
		}
	}

	// ========================= Record Execute =========================

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeRecord(NodeExecutionContext context, String projectId,
			Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		String datasetId = context.getParameter("recordDatasetId", "");
		String tableId = context.getParameter("recordTableId", "");

		switch (operation) {
			case "create": {
				String rowsJson = context.getParameter("rowsJson", "[]");
				List<Map<String, Object>> rows = parseJsonArray(rowsJson);

				// Build insertAll request body
				List<Map<String, Object>> insertRows = new ArrayList<>();
				for (Map<String, Object> row : rows) {
					insertRows.add(Map.of("json", row));
				}
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("kind", "bigquery#tableDataInsertAllRequest");
				body.put("rows", insertRows);

				String url = BASE_URL + "/projects/" + encode(projectId) + "/datasets/" + encode(datasetId)
					+ "/tables/" + encode(tableId) + "/insertAll";
				HttpResponse<String> response = post(url, body, headers);
				if (response.statusCode() >= 400) {
					return apiError(response);
				}
				Map<String, Object> parsed = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			case "getAll": {
				String sqlQuery = context.getParameter("sqlQuery", "");
				Map<String, Object> options = context.getParameter("recordOptions", Map.of());

				Map<String, Object> queryRequest = new LinkedHashMap<>();
				queryRequest.put("query", sqlQuery);
				queryRequest.put("useLegacySql", toBoolean(options.get("useLegacySql"), false));
				if (options.get("maxResults") != null) {
					queryRequest.put("maxResults", toInt(options.get("maxResults"), 100));
				}
				if (options.get("location") != null) {
					queryRequest.put("location", options.get("location"));
				}

				String url = BASE_URL + "/projects/" + encode(projectId) + "/queries";
				HttpResponse<String> response = post(url, queryRequest, headers);
				if (response.statusCode() >= 400) {
					return apiError(response);
				}
				Map<String, Object> parsed = parseResponse(response);

				// Extract rows from query result
				Object rows = parsed.get("rows");
				Object schema = parsed.get("schema");
				List<String> fieldNames = extractFieldNames(schema);

				if (rows instanceof List) {
					List<Map<String, Object>> items = new ArrayList<>();
					for (Object row : (List<?>) rows) {
						if (row instanceof Map) {
							Map<String, Object> rowMap = (Map<String, Object>) row;
							Object cells = rowMap.get("f");
							if (cells instanceof List) {
								Map<String, Object> record = new LinkedHashMap<>();
								List<?> cellList = (List<?>) cells;
								for (int i = 0; i < cellList.size(); i++) {
									String fieldName = i < fieldNames.size() ? fieldNames.get(i) : "field_" + i;
									Object cell = cellList.get(i);
									if (cell instanceof Map) {
										record.put(fieldName, ((Map<String, Object>) cell).get("v"));
									}
								}
								items.add(wrapInJson(record));
							}
						}
					}
					return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			default:
				return NodeExecutionResult.error("Unknown record operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(String accessToken) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}

	@SuppressWarnings("unchecked")
	private List<String> extractFieldNames(Object schema) {
		List<String> fieldNames = new ArrayList<>();
		if (schema instanceof Map) {
			Object fields = ((Map<String, Object>) schema).get("fields");
			if (fields instanceof List) {
				for (Object field : (List<?>) fields) {
					if (field instanceof Map) {
						String name = String.valueOf(((Map<String, Object>) field).getOrDefault("name", "unknown"));
						fieldNames.add(name);
					}
				}
			}
		}
		return fieldNames;
	}

	private NodeExecutionResult extractListResult(Map<String, Object> parsed, String listKey) {
		Object listData = parsed.get(listKey);
		if (listData instanceof List) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Object item : (List<?>) listData) {
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
		return NodeExecutionResult.error("Google BigQuery API error (HTTP " + response.statusCode() + "): " + body);
	}
}
