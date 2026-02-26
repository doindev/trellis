package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Google Sheets — manage spreadsheets, sheets, and data using the Google Sheets API v4.
 * Supports appending, clearing, creating, deleting, reading, and updating data.
 */
@Node(
		type = "googleSheets",
		displayName = "Google Sheets",
		description = "Read, update, and append data in Google Sheets",
		category = "Spreadsheets & Data Tables",
		icon = "googleSheets",
		credentials = {"googleSheetsOAuth2Api"}
)
public class GoogleSheetsNode extends AbstractApiNode {

	private static final String BASE_URL = "https://sheets.googleapis.com/v4/spreadsheets";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");

		String resource = context.getParameter("resource", "sheet");
		String operation = context.getParameter("operation", "getData");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "sheet" -> handleSheet(context, headers, operation);
					case "spreadsheet" -> handleSpreadsheet(context, headers, operation);
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

	// ---- Sheet data operations ----

	private Map<String, Object> handleSheet(NodeExecutionContext context,
			Map<String, String> headers, String operation) throws Exception {
		String spreadsheetId = context.getParameter("spreadsheetId", "");
		String sheetName = context.getParameter("sheetName", "Sheet1");
		String range = context.getParameter("range", "");
		String fullRange = sheetName + (range.isEmpty() ? "" : "!" + range);

		return switch (operation) {
			case "appendData" -> {
				String dataJson = context.getParameter("data", "[[]]");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("values", parseJsonArray(dataJson));
				body.put("majorDimension", "ROWS");
				String url = BASE_URL + "/" + encode(spreadsheetId) + "/values/" + encode(fullRange) + ":append"
						+ "?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS";
				HttpResponse<String> response = post(url, body, headers);
				yield parseResponse(response);
			}
			case "clearData" -> {
				String url = BASE_URL + "/" + encode(spreadsheetId) + "/values/" + encode(fullRange) + ":clear";
				HttpResponse<String> response = post(url, Map.of(), headers);
				yield parseResponse(response);
			}
			case "getData" -> {
				String url = BASE_URL + "/" + encode(spreadsheetId) + "/values/" + encode(fullRange);
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String dataJson = context.getParameter("data", "[[]]");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("values", parseJsonArray(dataJson));
				body.put("majorDimension", "ROWS");
				String url = BASE_URL + "/" + encode(spreadsheetId) + "/values/" + encode(fullRange)
						+ "?valueInputOption=USER_ENTERED";
				HttpResponse<String> response = put(url, body, headers);
				yield parseResponse(response);
			}
			case "lookupValue" -> {
				// Read all data and filter by lookup column/value
				String url = BASE_URL + "/" + encode(spreadsheetId) + "/values/" + encode(fullRange);
				HttpResponse<String> response = get(url, headers);
				Map<String, Object> allData = parseResponse(response);
				String lookupColumn = context.getParameter("lookupColumn", "0");
				String lookupValue = context.getParameter("lookupValue", "");

				@SuppressWarnings("unchecked")
				List<List<Object>> values = (List<List<Object>>) allData.getOrDefault("values", List.of());
				int colIndex = toInt(lookupColumn, 0);

				Map<String, Object> result = new LinkedHashMap<>();
				List<List<Object>> matches = new ArrayList<>();
				for (List<Object> row : values) {
					if (colIndex < row.size() && lookupValue.equals(String.valueOf(row.get(colIndex)))) {
						matches.add(row);
					}
				}
				result.put("matches", matches);
				result.put("matchCount", matches.size());
				yield result;
			}
			case "delete" -> {
				// Delete rows from sheet using batchUpdate
				int sheetId = toInt(context.getParameter("sheetId", "0"), 0);
				int startIndex = toInt(context.getParameter("startIndex", "0"), 0);
				int endIndex = toInt(context.getParameter("endIndex", "1"), 1);
				Map<String, Object> deleteDimension = Map.of(
					"range", Map.of(
						"sheetId", sheetId,
						"dimension", "ROWS",
						"startIndex", startIndex,
						"endIndex", endIndex
					)
				);
				Map<String, Object> body = Map.of(
					"requests", List.of(Map.of("deleteDimension", deleteDimension))
				);
				String url = BASE_URL + "/" + encode(spreadsheetId) + ":batchUpdate";
				HttpResponse<String> response = post(url, body, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				// Get all sheet tabs in the spreadsheet
				String url = BASE_URL + "/" + encode(spreadsheetId) + "?fields=sheets.properties";
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "remove" -> {
				// Remove/delete an entire sheet tab
				int sheetId = toInt(context.getParameter("sheetId", "0"), 0);
				Map<String, Object> body = Map.of(
					"requests", List.of(Map.of("deleteSheet", Map.of("sheetId", sheetId)))
				);
				String url = BASE_URL + "/" + encode(spreadsheetId) + ":batchUpdate";
				HttpResponse<String> response = post(url, body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown sheet operation: " + operation);
		};
	}

	// ---- Spreadsheet operations ----

	private Map<String, Object> handleSpreadsheet(NodeExecutionContext context,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				Map<String, Object> properties = new LinkedHashMap<>();
				String title = context.getParameter("title", "");
				if (!title.isEmpty()) properties.put("title", title);
				body.put("properties", properties);
				HttpResponse<String> response = post(BASE_URL, body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown spreadsheet operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("sheet")
						.options(List.of(
								ParameterOption.builder().name("Sheet").value("sheet").build(),
								ParameterOption.builder().name("Spreadsheet").value("spreadsheet").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getData")
						.options(List.of(
								ParameterOption.builder().name("Append Data").value("appendData").build(),
								ParameterOption.builder().name("Clear Data").value("clearData").build(),
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get Data").value("getData").build(),
								ParameterOption.builder().name("Get All Sheets").value("getAll").build(),
								ParameterOption.builder().name("Lookup Value").value("lookupValue").build(),
								ParameterOption.builder().name("Remove Sheet").value("remove").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("spreadsheetId").displayName("Spreadsheet ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the Google Spreadsheet.").build(),
				NodeParameter.builder()
						.name("sheetName").displayName("Sheet Name")
						.type(ParameterType.STRING).defaultValue("Sheet1")
						.description("The name of the sheet tab.").build(),
				NodeParameter.builder()
						.name("sheetId").displayName("Sheet ID")
						.type(ParameterType.STRING).defaultValue("0")
						.description("The numeric ID of the sheet tab (for delete/remove operations).").build(),
				NodeParameter.builder()
						.name("range").displayName("Range")
						.type(ParameterType.STRING).defaultValue("")
						.description("Cell range in A1 notation (e.g. A1:D10). Leave empty for entire sheet.").build(),
				NodeParameter.builder()
						.name("data").displayName("Data (JSON)")
						.type(ParameterType.JSON).defaultValue("[[]]")
						.description("JSON array of arrays representing rows and cells.").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("")
						.description("Title for new spreadsheet.").build(),
				NodeParameter.builder()
						.name("lookupColumn").displayName("Lookup Column Index")
						.type(ParameterType.STRING).defaultValue("0")
						.description("Zero-based column index to search in.").build(),
				NodeParameter.builder()
						.name("lookupValue").displayName("Lookup Value")
						.type(ParameterType.STRING).defaultValue("")
						.description("Value to search for in the lookup column.").build(),
				NodeParameter.builder()
						.name("startIndex").displayName("Start Row Index")
						.type(ParameterType.STRING).defaultValue("0")
						.description("Zero-based start row index for deletion.").build(),
				NodeParameter.builder()
						.name("endIndex").displayName("End Row Index")
						.type(ParameterType.STRING).defaultValue("1")
						.description("Zero-based end row index (exclusive) for deletion.").build()
		);
	}
}
