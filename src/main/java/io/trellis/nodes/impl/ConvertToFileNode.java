package io.trellis.nodes.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Convert to File — converts JSON data to binary file formats
 * (CSV, JSON, XLSX, XLS, text, base64).
 */
@Node(
		type = "convertToFile",
		displayName = "Convert to File",
		description = "Convert JSON data to binary data (CSV, JSON, spreadsheet, text)",
		category = "File Operations",
		icon = "file-export"
)
public class ConvertToFileNode extends AbstractNode {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String operation = context.getParameter("operation", "toJson");
		String outputField = context.getParameter("outputField", "data");

		try {
			Map<String, Object> binaryResult = switch (operation) {
				case "csv" -> handleCsv(context);
				case "toJson" -> handleToJson(context);
				case "xlsx" -> handleXlsx(context, true);
				case "xls" -> handleXlsx(context, false);
				case "toText" -> handleToText(context);
				case "toBinary" -> handleToBinary(context);
				default -> throw new IllegalArgumentException("Unknown operation: " + operation);
			};

			Map<String, Object> result = new LinkedHashMap<>();
			Map<String, Object> binary = new LinkedHashMap<>();
			binary.put(outputField, binaryResult);
			result.put("binary", binary);

			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			if (context.isContinueOnFail()) {
				return handleError(context, e.getMessage(), e);
			}
			throw new RuntimeException(e);
		}
	}

	private Map<String, Object> handleCsv(NodeExecutionContext context) throws Exception {
		List<Map<String, Object>> inputData = context.getInputData();
		String delimiter = context.getParameter("delimiter", ",");
		boolean headerRow = toBoolean(context.getParameters().get("headerRow"), true);
		String fileName = context.getParameter("fileName", "data.csv");

		List<Map<String, Object>> items = unwrapJsonList(inputData);

		Set<String> allKeys = new LinkedHashSet<>();
		for (Map<String, Object> item : items) {
			allKeys.addAll(item.keySet());
		}

		StringBuilder sb = new StringBuilder();
		if (headerRow) {
			sb.append(String.join(delimiter, allKeys)).append("\n");
		}
		for (Map<String, Object> item : items) {
			List<String> values = new ArrayList<>();
			for (String key : allKeys) {
				Object val = item.getOrDefault(key, "");
				String str = val != null ? val.toString() : "";
				if (str.contains(delimiter) || str.contains("\"") || str.contains("\n")) {
					str = "\"" + str.replace("\"", "\"\"") + "\"";
				}
				values.add(str);
			}
			sb.append(String.join(delimiter, values)).append("\n");
		}

		byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
		return createFileInfo(data, "text/csv", fileName);
	}

	private Map<String, Object> handleToJson(NodeExecutionContext context) throws Exception {
		List<Map<String, Object>> inputData = context.getInputData();
		String fileName = context.getParameter("fileName", "data.json");

		List<Map<String, Object>> items = unwrapJsonList(inputData);
		byte[] data = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(items);
		return createFileInfo(data, "application/json", fileName);
	}

	private Map<String, Object> handleXlsx(NodeExecutionContext context, boolean xlsx) throws Exception {
		List<Map<String, Object>> inputData = context.getInputData();
		String sheetName = context.getParameter("sheetName", "Sheet1");
		String fileName = context.getParameter("fileName", xlsx ? "data.xlsx" : "data.xls");

		List<Map<String, Object>> items = unwrapJsonList(inputData);

		Set<String> allKeys = new LinkedHashSet<>();
		for (Map<String, Object> item : items) {
			allKeys.addAll(item.keySet());
		}
		List<String> headers = new ArrayList<>(allKeys);

		Workbook workbook = xlsx ? new XSSFWorkbook() : new HSSFWorkbook();
		Sheet sheet = workbook.createSheet(sheetName);

		Row headerRow = sheet.createRow(0);
		for (int i = 0; i < headers.size(); i++) {
			headerRow.createCell(i).setCellValue(headers.get(i));
		}

		for (int r = 0; r < items.size(); r++) {
			Row row = sheet.createRow(r + 1);
			Map<String, Object> item = items.get(r);
			for (int c = 0; c < headers.size(); c++) {
				Object val = item.get(headers.get(c));
				Cell cell = row.createCell(c);
				if (val instanceof Number num) {
					cell.setCellValue(num.doubleValue());
				} else if (val instanceof Boolean bool) {
					cell.setCellValue(bool);
				} else {
					cell.setCellValue(val != null ? val.toString() : "");
				}
			}
		}

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		workbook.write(bos);
		workbook.close();

		String mimeType = xlsx
				? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
				: "application/vnd.ms-excel";
		return createFileInfo(bos.toByteArray(), mimeType, fileName);
	}

	private Map<String, Object> handleToText(NodeExecutionContext context) throws Exception {
		String sourceProperty = context.getParameter("sourceProperty", "text");
		String fileName = context.getParameter("fileName", "data.txt");

		Map<String, Object> firstItem = unwrapJson(context.getInputData().get(0));
		Object value = getNestedValue(firstItem, sourceProperty);
		String text = value != null ? value.toString() : "";

		byte[] data = text.getBytes(StandardCharsets.UTF_8);
		return createFileInfo(data, "text/plain", fileName);
	}

	private Map<String, Object> handleToBinary(NodeExecutionContext context) throws Exception {
		String inputField = context.getParameter("sourceProperty", "data");
		String mimeType = context.getParameter("mimeType", "application/octet-stream");
		String fileName = context.getParameter("fileName", "file");

		Map<String, Object> firstItem = unwrapJson(context.getInputData().get(0));
		Object value = getNestedValue(firstItem, inputField);
		String base64 = value != null ? value.toString() : "";

		byte[] data = Base64.getDecoder().decode(base64);
		return createFileInfo(data, mimeType, fileName);
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> unwrapJsonList(List<Map<String, Object>> items) {
		List<Map<String, Object>> result = new ArrayList<>();
		for (Map<String, Object> item : items) {
			result.add(unwrapJson(item));
		}
		return result;
	}

	private Map<String, Object> createFileInfo(byte[] data, String mimeType, String fileName) {
		Map<String, Object> fileInfo = new LinkedHashMap<>();
		fileInfo.put("data", Base64.getEncoder().encodeToString(data));
		fileInfo.put("mimeType", mimeType);
		fileInfo.put("fileName", fileName);
		fileInfo.put("fileSize", data.length);
		return fileInfo;
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("toJson")
						.options(List.of(
								ParameterOption.builder().name("Convert to CSV").value("csv").build(),
								ParameterOption.builder().name("Convert to JSON").value("toJson").build(),
								ParameterOption.builder().name("Convert to XLSX").value("xlsx").build(),
								ParameterOption.builder().name("Convert to XLS").value("xls").build(),
								ParameterOption.builder().name("Convert to Text File").value("toText").build(),
								ParameterOption.builder().name("Move Base64 to File").value("toBinary").build()
						)).build(),
				NodeParameter.builder()
						.name("outputField").displayName("Output Binary Field")
						.type(ParameterType.STRING).defaultValue("data")
						.description("Name of the output binary property.").build(),
				NodeParameter.builder()
						.name("delimiter").displayName("Delimiter")
						.type(ParameterType.STRING).defaultValue(",")
						.description("Delimiter for CSV output.").build(),
				NodeParameter.builder()
						.name("headerRow").displayName("Include Header Row")
						.type(ParameterType.BOOLEAN).defaultValue(true)
						.description("Include column headers in CSV output.").build(),
				NodeParameter.builder()
						.name("sheetName").displayName("Sheet Name")
						.type(ParameterType.STRING).defaultValue("Sheet1")
						.description("Name of the spreadsheet sheet.").build(),
				NodeParameter.builder()
						.name("sourceProperty").displayName("Source Property")
						.type(ParameterType.STRING).defaultValue("text")
						.description("Property to convert to file (dot notation).").build(),
				NodeParameter.builder()
						.name("mimeType").displayName("MIME Type")
						.type(ParameterType.STRING).defaultValue("application/octet-stream")
						.description("MIME type for base64 conversion.").build(),
				NodeParameter.builder()
						.name("fileName").displayName("File Name")
						.type(ParameterType.STRING).defaultValue("data")
						.description("Output file name.").build()
		);
	}
}
