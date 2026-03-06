package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Grist — manage rows in Grist spreadsheet tables.
 */
@Node(
		type = "grist",
		displayName = "Grist",
		description = "Create, read, update, and delete rows in Grist tables",
		category = "Spreadsheets & Data Tables",
		icon = "grist",
		credentials = {"gristApi"}
)
public class GristNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");
		String baseUrl = context.getCredentialString("baseUrl", "https://docs.getgrist.com/api");

		String operation = context.getParameter("operation", "getAll");
		String docId = context.getParameter("docId", "");
		String tableId = context.getParameter("tableId", "");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + apiKey);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		String tableUrl = baseUrl + "/docs/" + encode(docId) + "/tables/" + encode(tableId);

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "create" -> {
						String fieldsJson = context.getParameter("fields", "{}");
						Object fields = parseJson(fieldsJson);
						Map<String, Object> body = new LinkedHashMap<>();
						body.put("records", List.of(Map.of("fields", fields)));
						HttpResponse<String> response = post(tableUrl + "/records", body, headers);
						yield parseResponse(response);
					}
					case "delete" -> {
						String rowId = context.getParameter("rowId", "");
						int rowIdInt = toInt(rowId, 0);
						Map<String, Object> body = Map.of("records", List.of(rowIdInt));
						HttpResponse<String> response = post(tableUrl + "/data/delete", body, headers);
						yield parseResponse(response);
					}
					case "getAll" -> {
						StringBuilder url = new StringBuilder(tableUrl + "/records");
						int limit = toInt(context.getParameters().get("limit"), 100);
						url.append("?limit=").append(limit);
						String sort = context.getParameter("sort", "");
						if (!sort.isEmpty()) url.append("&sort=").append(encode(sort));
						String filter = context.getParameter("filter", "");
						if (!filter.isEmpty()) url.append("&filter=").append(encode(filter));
						HttpResponse<String> response = get(url.toString(), headers);
						yield parseResponse(response);
					}
					case "update" -> {
						String rowId = context.getParameter("rowId", "");
						int rowIdInt = toInt(rowId, 0);
						String fieldsJson = context.getParameter("fields", "{}");
						Object fields = parseJson(fieldsJson);
						Map<String, Object> body = new LinkedHashMap<>();
						body.put("records", List.of(Map.of("id", rowIdInt, "fields", fields)));
						HttpResponse<String> response = patch(tableUrl + "/records", body, headers);
						yield parseResponse(response);
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
								ParameterOption.builder().name("Create Row").value("create").build(),
								ParameterOption.builder().name("Delete Row").value("delete").build(),
								ParameterOption.builder().name("Get Many Rows").value("getAll").build(),
								ParameterOption.builder().name("Update Row").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("docId").displayName("Document ID")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("The Grist document ID.").build(),
				NodeParameter.builder()
						.name("tableId").displayName("Table ID")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("The table ID within the document.").build(),
				NodeParameter.builder()
						.name("rowId").displayName("Row ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The row ID (numeric).").build(),
				NodeParameter.builder()
						.name("fields").displayName("Fields")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("Row field values as JSON object.").build(),
				NodeParameter.builder()
						.name("sort").displayName("Sort")
						.type(ParameterType.STRING).defaultValue("")
						.description("Column name to sort by (prefix with - for descending).").build(),
				NodeParameter.builder()
						.name("filter").displayName("Filter")
						.type(ParameterType.JSON).defaultValue("")
						.description("Filter JSON object (e.g., {\"column\": [\"value\"]}).").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max rows to return.").build()
		);
	}
}
