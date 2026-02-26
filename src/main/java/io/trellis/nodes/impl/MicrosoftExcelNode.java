package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Microsoft Excel 365 — manage workbooks, worksheets, and table data using
 * the Microsoft Graph API. Supports adding/deleting rows, lookups, and more.
 */
@Node(
		type = "microsoftExcel",
		displayName = "Microsoft Excel 365",
		description = "Manage workbooks, worksheets, and tables in Microsoft Excel 365",
		category = "Spreadsheets & Data Tables",
		icon = "microsoftExcel",
		credentials = {"microsoftExcelOAuth2Api"}
)
public class MicrosoftExcelNode extends AbstractApiNode {

	private static final String GRAPH_BASE = "https://graph.microsoft.com/v1.0/me/drive/items";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		String workbookId = context.getParameter("workbookId", "");

		String baseUrl = GRAPH_BASE + "/" + encode(workbookId) + "/workbook";

		String resource = context.getParameter("resource", "table");
		String operation = context.getParameter("operation", "getRows");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "table" -> handleTable(context, baseUrl, headers, operation);
					case "workbook" -> handleWorkbook(context, baseUrl, headers, operation);
					case "worksheet" -> handleWorksheet(context, baseUrl, headers, operation);
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

	// ---- Table ----

	private Map<String, Object> handleTable(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		String tableName = context.getParameter("tableName", "");
		String worksheetName = context.getParameter("worksheetName", "");
		String tableBase = baseUrl + "/worksheets/" + encode(worksheetName) + "/tables/" + encode(tableName);

		return switch (operation) {
			case "addRow" -> {
				String valuesJson = context.getParameter("values", "[[]]");
				List<Map<String, Object>> valuesRaw = parseJsonArray(valuesJson);
				// Microsoft Graph expects values as array of arrays
				Map<String, Object> body = Map.of("values", valuesRaw);
				HttpResponse<String> response = post(tableBase + "/rows", body, headers);
				yield parseResponse(response);
			}
			case "deleteRow" -> {
				String rowIndex = context.getParameter("rowIndex", "0");
				HttpResponse<String> response = delete(tableBase + "/rows/itemAt(index=" + encode(rowIndex) + ")", headers);
				yield parseResponse(response);
			}
			case "getColumns" -> {
				HttpResponse<String> response = get(tableBase + "/columns", headers);
				yield parseResponse(response);
			}
			case "getRows" -> {
				HttpResponse<String> response = get(tableBase + "/rows", headers);
				yield parseResponse(response);
			}
			case "lookup" -> {
				// Get all rows and filter by column value
				HttpResponse<String> response = get(tableBase + "/rows", headers);
				Map<String, Object> allRows = parseResponse(response);
				String lookupColumn = context.getParameter("lookupColumn", "0");
				String lookupValue = context.getParameter("lookupValue", "");

				@SuppressWarnings("unchecked")
				List<Map<String, Object>> rowValues = (List<Map<String, Object>>) allRows.getOrDefault("value", List.of());
				int colIndex = toInt(lookupColumn, 0);

				Map<String, Object> result = new LinkedHashMap<>();
				List<Object> matches = new ArrayList<>();
				for (Map<String, Object> row : rowValues) {
					@SuppressWarnings("unchecked")
					List<List<Object>> vals = (List<List<Object>>) row.get("values");
					if (vals != null && !vals.isEmpty()) {
						List<Object> rowData = vals.get(0);
						if (colIndex < rowData.size() && lookupValue.equals(String.valueOf(rowData.get(colIndex)))) {
							matches.add(row);
						}
					}
				}
				result.put("matches", matches);
				result.put("matchCount", matches.size());
				yield result;
			}
			default -> throw new IllegalArgumentException("Unknown table operation: " + operation);
		};
	}

	// ---- Workbook ----

	private Map<String, Object> handleWorkbook(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "addWorksheet" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String worksheetName = context.getParameter("newWorksheetName", "");
				if (!worksheetName.isEmpty()) body.put("name", worksheetName);
				HttpResponse<String> response = post(baseUrl + "/worksheets", body, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(baseUrl + "/worksheets", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown workbook operation: " + operation);
		};
	}

	// ---- Worksheet ----

	private Map<String, Object> handleWorksheet(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		String worksheetName = context.getParameter("worksheetName", "");

		return switch (operation) {
			case "getAll" -> {
				HttpResponse<String> response = get(baseUrl + "/worksheets", headers);
				yield parseResponse(response);
			}
			case "getContent" -> {
				String range = context.getParameter("range", "A1:Z100");
				HttpResponse<String> response = get(baseUrl + "/worksheets/" + encode(worksheetName) + "/range(address='" + encode(range) + "')", headers);
				yield parseResponse(response);
			}
			case "upsert" -> {
				String range = context.getParameter("range", "A1");
				String valuesJson = context.getParameter("values", "[[]]");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("values", parseJsonArray(valuesJson));
				HttpResponse<String> response = patch(baseUrl + "/worksheets/" + encode(worksheetName) + "/range(address='" + encode(range) + "')", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown worksheet operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("table")
						.options(List.of(
								ParameterOption.builder().name("Table").value("table").build(),
								ParameterOption.builder().name("Workbook").value("workbook").build(),
								ParameterOption.builder().name("Worksheet").value("worksheet").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getRows")
						.options(List.of(
								ParameterOption.builder().name("Add Row").value("addRow").build(),
								ParameterOption.builder().name("Add Worksheet").value("addWorksheet").build(),
								ParameterOption.builder().name("Delete Row").value("deleteRow").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Get Columns").value("getColumns").build(),
								ParameterOption.builder().name("Get Content").value("getContent").build(),
								ParameterOption.builder().name("Get Rows").value("getRows").build(),
								ParameterOption.builder().name("Lookup").value("lookup").build(),
								ParameterOption.builder().name("Upsert").value("upsert").build()
						)).build(),
				NodeParameter.builder()
						.name("workbookId").displayName("Workbook ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The OneDrive item ID of the Excel workbook.").build(),
				NodeParameter.builder()
						.name("worksheetName").displayName("Worksheet Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name of the worksheet tab.").build(),
				NodeParameter.builder()
						.name("newWorksheetName").displayName("New Worksheet Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name for the new worksheet.").build(),
				NodeParameter.builder()
						.name("tableName").displayName("Table Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name of the table within the worksheet.").build(),
				NodeParameter.builder()
						.name("range").displayName("Range")
						.type(ParameterType.STRING).defaultValue("")
						.description("Cell range in A1 notation (e.g. A1:D10).").build(),
				NodeParameter.builder()
						.name("values").displayName("Values (JSON)")
						.type(ParameterType.JSON).defaultValue("[[]]")
						.description("JSON array of arrays representing rows and cells.").build(),
				NodeParameter.builder()
						.name("rowIndex").displayName("Row Index")
						.type(ParameterType.STRING).defaultValue("0")
						.description("Zero-based index of the row to delete.").build(),
				NodeParameter.builder()
						.name("lookupColumn").displayName("Lookup Column Index")
						.type(ParameterType.STRING).defaultValue("0")
						.description("Zero-based column index to search in.").build(),
				NodeParameter.builder()
						.name("lookupValue").displayName("Lookup Value")
						.type(ParameterType.STRING).defaultValue("")
						.description("Value to search for in the lookup column.").build()
		);
	}
}
