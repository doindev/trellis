package io.trellis.nodes.impl;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Spreadsheet File Node -- converts between spreadsheet formats (CSV, TSV)
 * and JSON data. Supports reading delimited text into structured JSON items
 * and converting JSON items back to delimited text.
 */
@Slf4j
@Node(
	type = "spreadsheetFile",
	displayName = "Spreadsheet File",
	description = "Convert between spreadsheet formats (CSV, TSV) and JSON",
	category = "Data Transformation",
	icon = "spreadsheetFile"
)
public class SpreadsheetFileNode extends AbstractNode {

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("toJson")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("From Spreadsheet to JSON").value("toJson")
					.description("Convert CSV/TSV text to JSON items").build(),
				ParameterOption.builder().name("From JSON to Spreadsheet").value("fromJson")
					.description("Convert JSON items to CSV/TSV text").build()
			)).build());

		// Format selector
		params.add(NodeParameter.builder()
			.name("format").displayName("Format")
			.type(ParameterType.OPTIONS).required(true).defaultValue("csv")
			.options(List.of(
				ParameterOption.builder().name("CSV").value("csv").description("Comma-separated values").build(),
				ParameterOption.builder().name("TSV").value("tsv").description("Tab-separated values").build(),
				ParameterOption.builder().name("Custom Delimiter").value("custom").description("Use a custom delimiter").build()
			)).build());

		// Custom delimiter
		params.add(NodeParameter.builder()
			.name("delimiter").displayName("Delimiter").type(ParameterType.STRING)
			.defaultValue("|")
			.displayOptions(Map.of("show", Map.of("format", List.of("custom"))))
			.build());

		// To JSON parameters
		params.add(NodeParameter.builder()
			.name("spreadsheetData").displayName("Spreadsheet Data").type(ParameterType.STRING).required(true)
			.description("The CSV/TSV data as a string to convert to JSON.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("toJson"))))
			.build());

		params.add(NodeParameter.builder()
			.name("headerRow").displayName("Header Row").type(ParameterType.BOOLEAN)
			.defaultValue(true)
			.description("Whether the first row contains column headers.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("toJson"))))
			.build());

		// From JSON parameters
		params.add(NodeParameter.builder()
			.name("includeHeader").displayName("Include Header Row").type(ParameterType.BOOLEAN)
			.defaultValue(true)
			.description("Whether to include a header row in the output.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("fromJson"))))
			.build());

		params.add(NodeParameter.builder()
			.name("columns").displayName("Columns").type(ParameterType.STRING)
			.description("Comma-separated list of column names to include. Leave empty to include all fields.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("fromJson"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String operation = context.getParameter("operation", "toJson");

		try {
			String format = context.getParameter("format", "csv");
			String delimiter = getDelimiter(format, context);

			return switch (operation) {
				case "toJson" -> executeToJson(context, delimiter);
				case "fromJson" -> executeFromJson(context, delimiter);
				default -> NodeExecutionResult.error("Unknown operation: " + operation);
			};
		} catch (Exception e) {
			return handleError(context, "Spreadsheet File error: " + e.getMessage(), e);
		}
	}

	// ========================= To JSON =========================

	private NodeExecutionResult executeToJson(NodeExecutionContext context, String delimiter) throws Exception {
		String data = context.getParameter("spreadsheetData", "");
		boolean hasHeader = toBoolean(context.getParameter("headerRow", true), true);

		if (data.isBlank()) {
			return NodeExecutionResult.empty();
		}

		List<String[]> rows = parseCsv(data, delimiter);
		if (rows.isEmpty()) {
			return NodeExecutionResult.empty();
		}

		List<Map<String, Object>> results = new ArrayList<>();

		if (hasHeader && rows.size() > 1) {
			String[] headers = rows.get(0);
			for (int i = 1; i < rows.size(); i++) {
				String[] row = rows.get(i);
				Map<String, Object> item = new LinkedHashMap<>();
				for (int j = 0; j < headers.length; j++) {
					String key = headers[j].trim();
					String value = j < row.length ? row[j].trim() : "";
					item.put(key, value);
				}
				results.add(wrapInJson(item));
			}
		} else {
			// No header -- use column indices
			for (String[] row : rows) {
				Map<String, Object> item = new LinkedHashMap<>();
				for (int j = 0; j < row.length; j++) {
					item.put("column_" + j, row[j].trim());
				}
				results.add(wrapInJson(item));
			}
		}

		return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
	}

	// ========================= From JSON =========================

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeFromJson(NodeExecutionContext context, String delimiter) {
		boolean includeHeader = toBoolean(context.getParameter("includeHeader", true), true);
		String columnsStr = context.getParameter("columns", "");

		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.empty();
		}

		// Determine columns
		List<String> columns;
		if (columnsStr != null && !columnsStr.isBlank()) {
			columns = Arrays.asList(columnsStr.split("\\s*,\\s*"));
		} else {
			// Collect all unique keys from input data
			Set<String> allKeys = new LinkedHashSet<>();
			for (Map<String, Object> item : inputData) {
				Map<String, Object> json = unwrapJson(item);
				allKeys.addAll(json.keySet());
			}
			columns = new ArrayList<>(allKeys);
		}

		StringBuilder sb = new StringBuilder();

		// Header row
		if (includeHeader) {
			sb.append(joinRow(columns.toArray(new String[0]), delimiter));
			sb.append("\n");
		}

		// Data rows
		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = unwrapJson(item);
			String[] values = new String[columns.size()];
			for (int i = 0; i < columns.size(); i++) {
				Object val = json.get(columns.get(i));
				values[i] = val != null ? String.valueOf(val) : "";
			}
			sb.append(joinRow(values, delimiter));
			sb.append("\n");
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("data", sb.toString());
		result.put("format", delimiter.equals(",") ? "csv" : delimiter.equals("\t") ? "tsv" : "custom");
		result.put("rowCount", inputData.size());
		result.put("columnCount", columns.size());
		result.put("columns", columns);

		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	// ========================= CSV Parsing =========================

	private List<String[]> parseCsv(String data, String delimiter) throws Exception {
		List<String[]> rows = new ArrayList<>();
		BufferedReader reader = new BufferedReader(new StringReader(data));
		String line;

		while ((line = reader.readLine()) != null) {
			if (line.isBlank()) continue;

			List<String> fields = new ArrayList<>();
			boolean inQuotes = false;
			StringBuilder field = new StringBuilder();

			for (int i = 0; i < line.length(); i++) {
				char c = line.charAt(i);

				if (inQuotes) {
					if (c == '"') {
						// Check for escaped quote
						if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
							field.append('"');
							i++; // skip next quote
						} else {
							inQuotes = false;
						}
					} else {
						field.append(c);
					}
				} else {
					if (c == '"') {
						inQuotes = true;
					} else if (matchesDelimiter(line, i, delimiter)) {
						fields.add(field.toString());
						field = new StringBuilder();
						i += delimiter.length() - 1; // skip delimiter chars
					} else {
						field.append(c);
					}
				}
			}
			fields.add(field.toString());
			rows.add(fields.toArray(new String[0]));
		}

		return rows;
	}

	private boolean matchesDelimiter(String line, int pos, String delimiter) {
		if (pos + delimiter.length() > line.length()) return false;
		return line.substring(pos, pos + delimiter.length()).equals(delimiter);
	}

	private String joinRow(String[] values, String delimiter) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			if (i > 0) sb.append(delimiter);
			String val = values[i];
			// Quote if contains delimiter, quotes, or newlines
			if (val.contains(delimiter) || val.contains("\"") || val.contains("\n") || val.contains("\r")) {
				sb.append('"').append(val.replace("\"", "\"\"")).append('"');
			} else {
				sb.append(val);
			}
		}
		return sb.toString();
	}

	private String getDelimiter(String format, NodeExecutionContext context) {
		return switch (format) {
			case "csv" -> ",";
			case "tsv" -> "\t";
			case "custom" -> {
				String d = context.getParameter("delimiter", "|");
				yield d.isEmpty() ? "|" : d;
			}
			default -> ",";
		};
	}
}
