package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Coda — manage documents, tables, rows, and formulas via the Coda API v1.
 */
@Node(
		type = "coda",
		displayName = "Coda",
		description = "Interact with the Coda API to manage docs, tables, and rows",
		category = "Data",
		icon = "coda",
		credentials = {"codaApi"}
)
public class CodaNode extends AbstractApiNode {

	private static final String BASE_URL = "https://coda.io/apis/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiToken = context.getCredentialString("apiToken", "");
		String resource = context.getParameter("resource", "doc");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + apiToken);
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "doc" -> handleDoc(context, operation, headers);
					case "table" -> handleTable(context, operation, headers);
					case "row" -> handleRow(context, operation, headers);
					case "formula" -> handleFormula(context, operation, headers);
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

	// ========================= Doc =========================

	private Map<String, Object> handleDoc(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "create" -> {
				String title = context.getParameter("title", "");
				String sourceDoc = context.getParameter("sourceDoc", "");
				String folderId = context.getParameter("folderId", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", title);
				if (!sourceDoc.isEmpty()) body.put("sourceDoc", sourceDoc);
				if (!folderId.isEmpty()) body.put("folderId", folderId);

				HttpResponse<String> response = post(BASE_URL + "/docs", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String docId = context.getParameter("docId", "");
				HttpResponse<String> response = delete(BASE_URL + "/docs/" + encode(docId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", true);
				result.put("statusCode", response.statusCode());
				yield result;
			}
			case "get" -> {
				String docId = context.getParameter("docId", "");
				HttpResponse<String> response = get(BASE_URL + "/docs/" + encode(docId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameter("limit", 25), 25);
				String query = context.getParameter("query", "");
				String url = BASE_URL + "/docs?limit=" + limit;
				if (!query.isEmpty()) url += "&query=" + encode(query);
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown doc operation: " + operation);
		};
	}

	// ========================= Table =========================

	private Map<String, Object> handleTable(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String docId = context.getParameter("docId", "");

		return switch (operation) {
			case "get" -> {
				String tableId = context.getParameter("tableId", "");
				HttpResponse<String> response = get(BASE_URL + "/docs/" + encode(docId) + "/tables/" + encode(tableId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameter("limit", 25), 25);
				String url = BASE_URL + "/docs/" + encode(docId) + "/tables?limit=" + limit;
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown table operation: " + operation);
		};
	}

	// ========================= Row =========================

	private Map<String, Object> handleRow(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String docId = context.getParameter("docId", "");
		String tableId = context.getParameter("tableId", "");

		return switch (operation) {
			case "create" -> {
				String cellsJson = context.getParameter("cells", "[]");
				boolean disableParsing = toBoolean(context.getParameters().get("disableParsing"), false);

				List<Map<String, Object>> cells = parseJsonArray(cellsJson);

				Map<String, Object> row = new LinkedHashMap<>();
				row.put("cells", cells);

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("rows", List.of(row));
				if (disableParsing) body.put("disableParsing", true);

				String url = BASE_URL + "/docs/" + encode(docId) + "/tables/" + encode(tableId) + "/rows";
				HttpResponse<String> response = post(url, body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String rowId = context.getParameter("rowId", "");
				String url = BASE_URL + "/docs/" + encode(docId) + "/tables/" + encode(tableId) + "/rows/" + encode(rowId);
				HttpResponse<String> response = delete(url, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", true);
				result.put("statusCode", response.statusCode());
				yield result;
			}
			case "get" -> {
				String rowId = context.getParameter("rowId", "");
				boolean useColumnNames = toBoolean(context.getParameters().get("useColumnNames"), true);
				String url = BASE_URL + "/docs/" + encode(docId) + "/tables/" + encode(tableId) + "/rows/" + encode(rowId);
				if (useColumnNames) url += "?useColumnNames=true";
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameter("limit", 25), 25);
				boolean useColumnNames = toBoolean(context.getParameters().get("useColumnNames"), true);
				String query = context.getParameter("query", "");
				String sortBy = context.getParameter("sortBy", "");

				StringBuilder url = new StringBuilder(BASE_URL + "/docs/" + encode(docId) + "/tables/" + encode(tableId) + "/rows");
				url.append("?limit=").append(limit);
				if (useColumnNames) url.append("&useColumnNames=true");
				if (!query.isEmpty()) url.append("&query=").append(encode(query));
				if (!sortBy.isEmpty()) url.append("&sortBy=").append(encode(sortBy));

				HttpResponse<String> response = get(url.toString(), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String rowId = context.getParameter("rowId", "");
				String cellsJson = context.getParameter("cells", "[]");
				boolean disableParsing = toBoolean(context.getParameters().get("disableParsing"), false);

				List<Map<String, Object>> cells = parseJsonArray(cellsJson);

				Map<String, Object> row = new LinkedHashMap<>();
				row.put("cells", cells);

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("row", row);
				if (disableParsing) body.put("disableParsing", true);

				String url = BASE_URL + "/docs/" + encode(docId) + "/tables/" + encode(tableId) + "/rows/" + encode(rowId);
				HttpResponse<String> response = put(url, body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown row operation: " + operation);
		};
	}

	// ========================= Formula =========================

	private Map<String, Object> handleFormula(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String docId = context.getParameter("docId", "");

		return switch (operation) {
			case "get" -> {
				String formulaId = context.getParameter("formulaId", "");
				HttpResponse<String> response = get(BASE_URL + "/docs/" + encode(docId) + "/formulas/" + encode(formulaId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameter("limit", 25), 25);
				String url = BASE_URL + "/docs/" + encode(docId) + "/formulas?limit=" + limit;
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown formula operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("doc")
						.options(List.of(
								ParameterOption.builder().name("Doc").value("doc").build(),
								ParameterOption.builder().name("Table").value("table").build(),
								ParameterOption.builder().name("Row").value("row").build(),
								ParameterOption.builder().name("Formula").value("formula").build()
						)).build(),
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
						.name("docId").displayName("Document ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the Coda document.").build(),
				NodeParameter.builder()
						.name("tableId").displayName("Table ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID or name of the table.").build(),
				NodeParameter.builder()
						.name("rowId").displayName("Row ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the row.").build(),
				NodeParameter.builder()
						.name("formulaId").displayName("Formula ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID or name of the formula.").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("")
						.description("Title for the new document.").build(),
				NodeParameter.builder()
						.name("sourceDoc").displayName("Source Document ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("ID of a document to copy to create the new document.").build(),
				NodeParameter.builder()
						.name("folderId").displayName("Folder ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the folder to create the document in.").build(),
				NodeParameter.builder()
						.name("cells").displayName("Cells (JSON)")
						.type(ParameterType.JSON).defaultValue("[]")
						.description("JSON array of cell objects: [{\"column\": \"columnId\", \"value\": \"cellValue\"}].").build(),
				NodeParameter.builder()
						.name("disableParsing").displayName("Disable Parsing")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("If true, the API will not attempt to parse the data in any way.").build(),
				NodeParameter.builder()
						.name("useColumnNames").displayName("Use Column Names")
						.type(ParameterType.BOOLEAN).defaultValue(true)
						.description("Use column names instead of column IDs in the response.").build(),
				NodeParameter.builder()
						.name("query").displayName("Query")
						.type(ParameterType.STRING).defaultValue("")
						.description("Search query to filter results.").build(),
				NodeParameter.builder()
						.name("sortBy").displayName("Sort By")
						.type(ParameterType.STRING).defaultValue("")
						.description("Specifies the sort order of rows returned.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(25)
						.description("Maximum number of items to return.").build()
		);
	}
}
