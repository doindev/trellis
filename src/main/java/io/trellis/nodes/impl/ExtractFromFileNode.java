package io.trellis.nodes.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import org.apache.poi.ss.usermodel.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Extract from File — converts binary file data to JSON output
 * (CSV, JSON, XLSX, XLS, text, base64).
 */
@Node(
		type = "extractFromFile",
		displayName = "Extract from File",
		description = "Convert binary data to JSON (CSV, JSON, spreadsheet, text)",
		category = "File Operations",
		icon = "file-import"
)
public class ExtractFromFileNode extends AbstractNode {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String operation = context.getParameter("operation", "fromJson");
		String inputField = context.getParameter("binaryPropertyName", "data");

		try {
			byte[] data = getBinaryData(context, inputField);

			List<Map<String, Object>> results = switch (operation) {
				case "csv" -> handleCsv(context, data);
				case "fromJson" -> handleFromJson(data);
				case "xlsx", "xls" -> handleSpreadsheet(context, data);
				case "text" -> handleText(data);
				case "binaryToProperty" -> handleBinaryToProperty(data);
				default -> throw new IllegalArgumentException("Unknown operation: " + operation);
			};

			return NodeExecutionResult.success(results);
		} catch (Exception e) {
			if (context.isContinueOnFail()) {
				return handleError(context, e.getMessage(), e);
			}
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private byte[] getBinaryData(NodeExecutionContext context, String field) {
		Map<String, Object> firstItem = context.getInputData().get(0);
		Map<String, Object> binaryData = (Map<String, Object>) firstItem.get("binary");
		if (binaryData == null) {
			binaryData = (Map<String, Object>) unwrapJson(firstItem).get("binary");
		}
		if (binaryData == null) {
			throw new IllegalStateException("No binary data found");
		}

		Map<String, Object> fileData = (Map<String, Object>) binaryData.get(field);
		if (fileData == null) {
			throw new IllegalStateException("No binary data found in field: " + field);
		}

		return Base64.getDecoder().decode((String) fileData.get("data"));
	}

	private List<Map<String, Object>> handleCsv(NodeExecutionContext context, byte[] data) {
		String delimiter = context.getParameter("delimiter", ",");
		boolean headerRow = toBoolean(context.getParameters().get("headerRow"), true);

		String content = new String(data, StandardCharsets.UTF_8);
		String[] lines = content.split("\n");

		List<Map<String, Object>> results = new ArrayList<>();
		String[] headers = null;

		int startIndex = 0;
		if (headerRow && lines.length > 0) {
			headers = parseCsvLine(lines[0], delimiter);
			startIndex = 1;
		}

		for (int i = startIndex; i < lines.length; i++) {
			String line = lines[i].trim();
			if (line.isEmpty()) continue;

			String[] values = parseCsvLine(line, delimiter);
			Map<String, Object> row = new LinkedHashMap<>();

			for (int j = 0; j < values.length; j++) {
				String key = headers != null && j < headers.length ? headers[j].trim() : "column_" + j;
				row.put(key, values[j].trim());
			}
			results.add(wrapInJson(row));
		}

		return results;
	}

	private String[] parseCsvLine(String line, String delimiter) {
		// Simple CSV parser handling quoted fields
		List<String> fields = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inQuotes = false;

		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '"') {
				if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					current.append('"');
					i++;
				} else {
					inQuotes = !inQuotes;
				}
			} else if (c == delimiter.charAt(0) && !inQuotes) {
				fields.add(current.toString());
				current = new StringBuilder();
			} else {
				current.append(c);
			}
		}
		fields.add(current.toString());

		return fields.toArray(new String[0]);
	}

	private List<Map<String, Object>> handleFromJson(byte[] data) throws Exception {
		String content = new String(data, StandardCharsets.UTF_8);
		// Try to strip BOM
		if (content.startsWith("\uFEFF")) {
			content = content.substring(1);
		}

		Object parsed = MAPPER.readValue(content, Object.class);

		List<Map<String, Object>> results = new ArrayList<>();
		if (parsed instanceof List<?> list) {
			for (Object item : list) {
				if (item instanceof Map) {
					@SuppressWarnings("unchecked")
					Map<String, Object> map = (Map<String, Object>) item;
					results.add(wrapInJson(map));
				}
			}
		} else if (parsed instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>) parsed;
			results.add(wrapInJson(map));
		}

		return results;
	}

	private List<Map<String, Object>> handleSpreadsheet(NodeExecutionContext context, byte[] data) throws Exception {
		String sheetName = context.getParameter("sheetName", "");
		boolean headerRow = toBoolean(context.getParameters().get("headerRow"), true);

		ByteArrayInputStream bis = new ByteArrayInputStream(data);
		Workbook workbook = WorkbookFactory.create(bis);

		Sheet sheet;
		if (sheetName != null && !sheetName.isBlank()) {
			sheet = workbook.getSheet(sheetName);
			if (sheet == null) {
				sheet = workbook.getSheetAt(0);
			}
		} else {
			sheet = workbook.getSheetAt(0);
		}

		List<Map<String, Object>> results = new ArrayList<>();
		List<String> headers = new ArrayList<>();

		int startRow = 0;
		if (headerRow) {
			Row headerRowData = sheet.getRow(0);
			if (headerRowData != null) {
				for (int c = 0; c < headerRowData.getLastCellNum(); c++) {
					Cell cell = headerRowData.getCell(c);
					headers.add(cell != null ? getCellValue(cell).toString() : "column_" + c);
				}
			}
			startRow = 1;
		}

		for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
			Row row = sheet.getRow(r);
			if (row == null) continue;

			Map<String, Object> rowData = new LinkedHashMap<>();
			int cols = headerRow ? headers.size() : row.getLastCellNum();
			for (int c = 0; c < cols; c++) {
				Cell cell = row.getCell(c);
				String key = headerRow && c < headers.size() ? headers.get(c) : "column_" + c;
				rowData.put(key, cell != null ? getCellValue(cell) : null);
			}
			results.add(wrapInJson(rowData));
		}

		workbook.close();
		return results;
	}

	private Object getCellValue(Cell cell) {
		return switch (cell.getCellType()) {
			case STRING -> cell.getStringCellValue();
			case NUMERIC -> DateUtil.isCellDateFormatted(cell)
					? cell.getLocalDateTimeCellValue().toString()
					: cell.getNumericCellValue();
			case BOOLEAN -> cell.getBooleanCellValue();
			case FORMULA -> cell.getStringCellValue();
			default -> "";
		};
	}

	private List<Map<String, Object>> handleText(byte[] data) {
		String content = new String(data, StandardCharsets.UTF_8);
		if (content.startsWith("\uFEFF")) {
			content = content.substring(1);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("text", content);
		return List.of(wrapInJson(result));
	}

	private List<Map<String, Object>> handleBinaryToProperty(byte[] data) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("data", Base64.getEncoder().encodeToString(data));
		return List.of(wrapInJson(result));
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("fromJson")
						.options(List.of(
								ParameterOption.builder().name("Extract From CSV").value("csv").build(),
								ParameterOption.builder().name("Extract From JSON").value("fromJson").build(),
								ParameterOption.builder().name("Extract From XLSX").value("xlsx").build(),
								ParameterOption.builder().name("Extract From XLS").value("xls").build(),
								ParameterOption.builder().name("Extract From Text").value("text").build(),
								ParameterOption.builder().name("Move File to Base64 String").value("binaryToProperty").build()
						)).build(),
				NodeParameter.builder()
						.name("binaryPropertyName").displayName("Input Binary Field")
						.type(ParameterType.STRING).defaultValue("data")
						.description("Name of the binary property to extract from.").build(),
				NodeParameter.builder()
						.name("delimiter").displayName("Delimiter")
						.type(ParameterType.STRING).defaultValue(",")
						.description("Delimiter for CSV parsing.").build(),
				NodeParameter.builder()
						.name("headerRow").displayName("Header Row")
						.type(ParameterType.BOOLEAN).defaultValue(true)
						.description("First row contains column headers.").build(),
				NodeParameter.builder()
						.name("sheetName").displayName("Sheet Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name of the sheet to read. Empty for first sheet.").build()
		);
	}
}
