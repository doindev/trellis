package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * SeaTable — manage rows, base metadata, and links using the SeaTable API.
 */
@Node(
		type = "seaTable",
		displayName = "SeaTable",
		description = "Manage rows and tables in SeaTable",
		category = "Spreadsheets & Data Tables",
		icon = "seaTable",
		credentials = {"seaTableApi"}
)
public class SeaTableNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String baseUrl = context.getCredentialString("domain", "https://cloud.seatable.io");
		if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		String apiToken = context.getCredentialString("token", "");

		String resource = context.getParameter("resource", "row");
		String operation = context.getParameter("operation", "getAll");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				// Step 1: Get base access token
				Map<String, String> authHeaders = new HashMap<>();
				authHeaders.put("Authorization", "Token " + apiToken);
				authHeaders.put("Accept", "application/json");
				HttpResponse<String> tokenResponse = get(baseUrl + "/api/v2.1/dtable/app-access-token/", authHeaders);
				Map<String, Object> tokenData = parseResponse(tokenResponse);
				String accessToken = String.valueOf(tokenData.getOrDefault("access_token", ""));
				String dtableUuid = String.valueOf(tokenData.getOrDefault("dtable_uuid", ""));

				// Step 2: Use base access token for API calls
				Map<String, String> headers = new HashMap<>();
				headers.put("Authorization", "Token " + accessToken);
				headers.put("Accept", "application/json");
				headers.put("Content-Type", "application/json");

				String apiBase = baseUrl + "/api-gateway/api/v2/dtables/" + dtableUuid;

				Map<String, Object> result = switch (resource) {
					case "row" -> handleRow(context, apiBase, headers, operation);
					case "base" -> handleBase(context, apiBase, headers, operation);
					default -> throw new IllegalArgumentException("Unknown resource: " + resource);
				};
				results.add(wrapInJson(result));
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					return handleError(context, e.getMessage(), e);
				}
				throw new RuntimeException(e);
			}
		}

		return NodeExecutionResult.success(results);
	}

	private Map<String, Object> handleRow(NodeExecutionContext context, String apiBase, Map<String, String> headers, String operation) throws Exception {
		String tableName = context.getParameter("tableName", "");
		return switch (operation) {
			case "create" -> {
				String fieldsJson = context.getParameter("fields", "{}");
				Object fields = parseJson(fieldsJson);
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("table_name", tableName);
				body.put("rows", List.of(fields));
				HttpResponse<String> response = post(apiBase + "/rows/", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String rowId = context.getParameter("rowId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("table_name", tableName);
				body.put("row_ids", List.of(rowId));
				HttpResponse<String> response = delete(apiBase + "/rows/", headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String rowId = context.getParameter("rowId", "");
				Map<String, Object> body = Map.of(
						"sql", "SELECT * FROM `" + tableName + "` WHERE _id = '" + rowId + "'",
						"convert_keys", true
				);
				HttpResponse<String> response = post(apiBase + "/sql/", body, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				StringBuilder url = new StringBuilder(apiBase + "/rows/?table_name=" + encode(tableName) + "&limit=" + limit);
				String viewName = context.getParameter("viewName", "");
				if (!viewName.isEmpty()) url.append("&view_name=").append(encode(viewName));
				HttpResponse<String> response = get(url.toString(), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String rowId = context.getParameter("rowId", "");
				String fieldsJson = context.getParameter("fields", "{}");
				Object fields = parseJson(fieldsJson);
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("table_name", tableName);
				body.put("updates", List.of(Map.of("row_id", rowId, "row", fields)));
				HttpResponse<String> response = put(apiBase + "/rows/", body, headers);
				yield parseResponse(response);
			}
			case "search" -> {
				String searchColumn = context.getParameter("searchColumn", "");
				String searchTerm = context.getParameter("searchTerm", "");
				Map<String, Object> body = Map.of(
						"sql", "SELECT * FROM `" + tableName + "` WHERE `" + searchColumn + "` LIKE '%" + searchTerm + "%'",
						"convert_keys", true
				);
				HttpResponse<String> response = post(apiBase + "/sql/", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown row operation: " + operation);
		};
	}

	private Map<String, Object> handleBase(NodeExecutionContext context, String apiBase, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "metadata" -> {
				HttpResponse<String> response = get(apiBase + "/metadata/", headers);
				yield parseResponse(response);
			}
			case "snapshot" -> {
				Map<String, Object> body = Map.of("dtable_name", "snapshot");
				HttpResponse<String> response = post(apiBase + "/snapshot/", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown base operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("row")
						.options(List.of(
								ParameterOption.builder().name("Base").value("base").build(),
								ParameterOption.builder().name("Row").value("row").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Metadata").value("metadata").build(),
								ParameterOption.builder().name("Search").value("search").build(),
								ParameterOption.builder().name("Snapshot").value("snapshot").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("tableName").displayName("Table Name")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("The SeaTable table name.").build(),
				NodeParameter.builder()
						.name("rowId").displayName("Row ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("viewName").displayName("View Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Optional view name filter.").build(),
				NodeParameter.builder()
						.name("fields").displayName("Fields")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("Row field values as JSON object.").build(),
				NodeParameter.builder()
						.name("searchColumn").displayName("Search Column")
						.type(ParameterType.STRING).defaultValue("")
						.description("Column name to search in.").build(),
				NodeParameter.builder()
						.name("searchTerm").displayName("Search Term")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max rows to return.").build()
		);
	}
}
