package io.cwc.nodes.impl;

import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Google Sheets Trigger — polls for new or updated rows in a Google Sheet.
 */
@Slf4j
@Node(
		type = "googleSheetsTrigger",
		displayName = "Google Sheets Trigger",
		description = "Polls for new or updated rows in a Google Sheet",
		category = "Google",
		icon = "googleSheets",
		trigger = true,
		polling = true,
		credentials = {"googleSheetsOAuth2Api"}
)
public class GoogleSheetsTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://sheets.googleapis.com/v4";

	@Override
	public List<NodeInput> getInputs() {
		return List.of(); // trigger node has no inputs
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
				.name("spreadsheetId").displayName("Spreadsheet ID")
				.type(ParameterType.STRING).required(true)
				.description("The ID of the Google Spreadsheet (from the URL).")
				.placeHolder("1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgVE2upms")
				.build());

		params.add(NodeParameter.builder()
				.name("sheetName").displayName("Sheet Name")
				.type(ParameterType.STRING).required(true).defaultValue("Sheet1")
				.description("The name of the sheet to monitor.")
				.placeHolder("Sheet1")
				.build());

		params.add(NodeParameter.builder()
				.name("triggerOn").displayName("Trigger On")
				.type(ParameterType.OPTIONS).required(true).defaultValue("newRows")
				.options(List.of(
						ParameterOption.builder().name("New Rows").value("newRows")
								.description("Trigger when new rows are added").build(),
						ParameterOption.builder().name("Row Changes").value("rowChanges")
								.description("Trigger when any row data changes").build()
				)).build());

		params.add(NodeParameter.builder()
				.name("range").displayName("Range")
				.type(ParameterType.STRING).defaultValue("A:Z")
				.description("The range of cells to monitor (e.g., A:Z, A1:F100).")
				.placeHolder("A:Z")
				.build());

		params.add(NodeParameter.builder()
				.name("includeHeaders").displayName("Include Headers")
				.type(ParameterType.BOOLEAN).defaultValue(true)
				.description("Use the first row as header names for the output.")
				.build());

		return params;
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		Map<String, Object> staticData = context.getStaticData();
		if (staticData == null) staticData = new LinkedHashMap<>();

		try {
			String accessToken = (String) credentials.getOrDefault("accessToken", "");
			Map<String, String> headers = getAuthHeaders(accessToken);

			String spreadsheetId = context.getParameter("spreadsheetId", "");
			String sheetName = context.getParameter("sheetName", "Sheet1");
			String triggerOn = context.getParameter("triggerOn", "newRows");
			String range = context.getParameter("range", "A:Z");
			boolean includeHeaders = toBoolean(context.getParameter("includeHeaders", true), true);

			String fullRange = sheetName + "!" + range;
			String url = BASE_URL + "/spreadsheets/" + encode(spreadsheetId)
					+ "/values/" + encode(fullRange) + "?valueRenderOption=FORMATTED_VALUE";

			HttpResponse<String> response = get(url, headers);
			Map<String, Object> result = parseResponse(response);

			Object valuesObj = result.get("values");
			if (!(valuesObj instanceof List)) {
				Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
				return NodeExecutionResult.builder()
						.output(List.of(List.of()))
						.staticData(newStaticData)
						.build();
			}

			List<List<Object>> allRows = (List<List<Object>>) valuesObj;
			if (allRows.isEmpty()) {
				Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
				return NodeExecutionResult.builder()
						.output(List.of(List.of()))
						.staticData(newStaticData)
						.build();
			}

			List<String> headerNames = new ArrayList<>();
			int dataStartIndex = 0;
			if (includeHeaders && !allRows.isEmpty()) {
				List<Object> headerRow = allRows.get(0);
				for (Object h : headerRow) {
					headerNames.add(h != null ? h.toString() : "");
				}
				dataStartIndex = 1;
			}

			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
			List<Map<String, Object>> outputItems = new ArrayList<>();

			if ("newRows".equals(triggerOn)) {
				int lastRowCount = staticData.containsKey("lastRowCount")
						? ((Number) staticData.get("lastRowCount")).intValue()
						: allRows.size(); // First run: treat all existing as seen

				// Only process if there are new rows
				if (allRows.size() > lastRowCount) {
					for (int i = Math.max(lastRowCount, dataStartIndex); i < allRows.size(); i++) {
						Map<String, Object> rowData = buildRowData(allRows.get(i), headerNames, i);
						rowData.put("_triggerTimestamp", System.currentTimeMillis());
						outputItems.add(wrapInJson(rowData));
					}
				}

				newStaticData.put("lastRowCount", allRows.size());

			} else {
				// rowChanges: compare with previous snapshot
				String currentHash = computeDataHash(allRows);
				String lastHash = (String) staticData.getOrDefault("dataHash", "");

				if (!currentHash.equals(lastHash) && !lastHash.isEmpty()) {
					// Data changed, return all current rows
					for (int i = dataStartIndex; i < allRows.size(); i++) {
						Map<String, Object> rowData = buildRowData(allRows.get(i), headerNames, i);
						rowData.put("_triggerTimestamp", System.currentTimeMillis());
						outputItems.add(wrapInJson(rowData));
					}
				}

				newStaticData.put("dataHash", currentHash);
			}

			if (outputItems.isEmpty()) {
				return NodeExecutionResult.builder()
						.output(List.of(List.of()))
						.staticData(newStaticData)
						.build();
			}

			log.debug("Google Sheets trigger: found {} new/changed rows", outputItems.size());
			return NodeExecutionResult.builder()
					.output(List.of(outputItems))
					.staticData(newStaticData)
					.build();

		} catch (Exception e) {
			return handleError(context, "Google Sheets Trigger error: " + e.getMessage(), e);
		}
	}

	private Map<String, Object> buildRowData(List<Object> row, List<String> headers, int rowIndex) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("_rowIndex", rowIndex);

		for (int col = 0; col < row.size(); col++) {
			String key;
			if (col < headers.size() && !headers.get(col).isEmpty()) {
				key = headers.get(col);
			} else {
				key = "column_" + col;
			}
			data.put(key, row.get(col));
		}
		return data;
	}

	private String computeDataHash(List<List<Object>> data) {
		int hash = 0;
		for (List<Object> row : data) {
			hash = 31 * hash + row.toString().hashCode();
		}
		return String.valueOf(hash);
	}

	private Map<String, String> getAuthHeaders(String accessToken) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}
}
