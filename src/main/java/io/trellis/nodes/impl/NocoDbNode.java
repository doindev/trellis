package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * NocoDB — manage rows in NocoDB open-source database tables.
 * Implements NocoDB v2 API (latest).
 */
@Node(
		type = "nocoDb",
		displayName = "NocoDB",
		description = "Manage rows in NocoDB database tables",
		category = "Data & Storage / Databases",
		icon = "nocoDb",
		credentials = {"nocoDbApi"}
)
public class NocoDbNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String baseUrl = (String) credentials.getOrDefault("host", "");
		if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		String apiToken = (String) credentials.getOrDefault("apiToken", "");

		String operation = context.getParameter("operation", "getAll");
		String tableId = context.getParameter("tableId", "");

		Map<String, String> headers = new HashMap<>();
		headers.put("xc-token", apiToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		String apiBase = baseUrl + "/api/v2/tables/" + tableId + "/records";

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "get" -> {
						String id = context.getParameter("rowId", "");
						HttpResponse<String> response = get(apiBase + "/" + id, headers);
						yield parseResponse(response);
					}
					case "getAll" -> {
						int limit = toInt(context.getParameters().get("limit"), 50);
						StringBuilder params = new StringBuilder();
						params.append("limit=").append(limit);
						String where = context.getParameter("where", "");
						if (!where.isEmpty()) params.append("&where=").append(encode(where));
						String fields = context.getParameter("fields", "");
						if (!fields.isEmpty()) params.append("&fields=").append(encode(fields));
						String sortField = context.getParameter("sortField", "");
						if (!sortField.isEmpty()) {
							String sortDir = context.getParameter("sortDirection", "asc");
							params.append("&sort=").append("-".equals(sortDir) || "desc".equals(sortDir) ? "-" : "").append(encode(sortField));
						}
						String viewId = context.getParameter("viewId", "");
						if (!viewId.isEmpty()) params.append("&viewId=").append(encode(viewId));
						HttpResponse<String> response = get(apiBase + "?" + params, headers);
						yield parseResponse(response);
					}
					case "create" -> {
						String fieldsJson = context.getParameter("rowData", "");
						Map<String, Object> body = fieldsJson.isEmpty() ? new LinkedHashMap<>() : parseJson(fieldsJson);
						HttpResponse<String> response = post(apiBase, body, headers);
						yield parseResponse(response);
					}
					case "update" -> {
						String id = context.getParameter("rowId", "");
						String fieldsJson = context.getParameter("rowData", "");
						Map<String, Object> body = fieldsJson.isEmpty() ? new LinkedHashMap<>() : parseJson(fieldsJson);
						body.put("Id", toInt(id, 0));
						HttpResponse<String> response = patch(apiBase, body, headers);
						yield parseResponse(response);
					}
					case "delete" -> {
						String id = context.getParameter("rowId", "");
						Map<String, Object> body = Map.of("Id", toInt(id, 0));
						HttpResponse<String> response = delete(apiBase, headers);
						yield Map.<String, Object>of("success", true, "rowId", id);
					}
					default -> throw new IllegalArgumentException("Unknown operation: " + operation);
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

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("tableId").displayName("Table ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("ID of the NocoDB table.").build(),
				NodeParameter.builder()
						.name("rowId").displayName("Row ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("ID of the row.").build(),
				NodeParameter.builder()
						.name("rowData").displayName("Row Data (JSON)")
						.type(ParameterType.STRING).defaultValue("")
						.description("JSON object of field name/value pairs.").build(),
				NodeParameter.builder()
						.name("where").displayName("Where")
						.type(ParameterType.STRING).defaultValue("")
						.description("Filter formula for getAll.").build(),
				NodeParameter.builder()
						.name("fields").displayName("Fields")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated field names to return.").build(),
				NodeParameter.builder()
						.name("sortField").displayName("Sort Field")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("sortDirection").displayName("Sort Direction")
						.type(ParameterType.OPTIONS).defaultValue("asc")
						.options(List.of(
								ParameterOption.builder().name("Ascending").value("asc").build(),
								ParameterOption.builder().name("Descending").value("desc").build()
						)).build(),
				NodeParameter.builder()
						.name("viewId").displayName("View ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(50)
						.description("Max rows to return.").build()
		);
	}
}
