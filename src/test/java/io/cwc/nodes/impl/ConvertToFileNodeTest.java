package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.cwc.nodes.core.NodeExecutionResult;

class ConvertToFileNodeTest {

    private ConvertToFileNode node;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        node = new ConvertToFileNode();
    }

    // -- Helper to extract binary info from result --

    @SuppressWarnings("unchecked")
    private Map<String, Object> getBinaryData(NodeExecutionResult result, String field) {
        Map<String, Object> json = firstJson(result);
        Map<String, Object> binary = (Map<String, Object>) json.get("binary");
        return (Map<String, Object>) binary.get(field);
    }

    private String decodeBase64Data(Map<String, Object> binaryInfo) {
        return new String(Base64.getDecoder().decode((String) binaryInfo.get("data")), StandardCharsets.UTF_8);
    }

    // -- Convert to CSV --

    @Test
    void convertToCsvProducesValidCsvData() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice", "age", 30),
                Map.of("name", "Bob", "age", 25)
        );
        Map<String, Object> params = mutableMap(
                "operation", "csv",
                "outputField", "data",
                "delimiter", ",",
                "headerRow", true,
                "fileName", "test.csv"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        Map<String, Object> binaryInfo = getBinaryData(result, "data");
        assertThat(binaryInfo.get("mimeType")).isEqualTo("text/csv");
        assertThat(binaryInfo.get("fileName")).isEqualTo("test.csv");

        String csvContent = decodeBase64Data(binaryInfo);
        assertThat(csvContent).contains("name");
        assertThat(csvContent).contains("age");
        assertThat(csvContent).contains("Alice");
        assertThat(csvContent).contains("Bob");
    }

    @Test
    void convertToCsvWithHeaderRow() {
        List<Map<String, Object>> input = items(Map.of("col1", "val1", "col2", "val2"));
        Map<String, Object> params = mutableMap(
                "operation", "csv",
                "outputField", "data",
                "delimiter", ",",
                "headerRow", true,
                "fileName", "data.csv"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String csvContent = decodeBase64Data(getBinaryData(result, "data"));
        String[] lines = csvContent.trim().split("\n");
        assertThat(lines.length).isGreaterThanOrEqualTo(2); // header + 1 data row
    }

    @Test
    void convertToCsvWithoutHeaderRow() {
        List<Map<String, Object>> input = items(Map.of("col1", "val1", "col2", "val2"));
        Map<String, Object> params = mutableMap(
                "operation", "csv",
                "outputField", "data",
                "delimiter", ",",
                "headerRow", false,
                "fileName", "data.csv"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String csvContent = decodeBase64Data(getBinaryData(result, "data"));
        String[] lines = csvContent.trim().split("\n");
        assertThat(lines.length).isEqualTo(1); // only data row, no header
    }

    // -- Custom delimiter for CSV --

    @Test
    void convertToCsvWithCustomDelimiter() {
        List<Map<String, Object>> input = items(Map.of("a", "1", "b", "2"));
        Map<String, Object> params = mutableMap(
                "operation", "csv",
                "outputField", "data",
                "delimiter", ";",
                "headerRow", true,
                "fileName", "semi.csv"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String csvContent = decodeBase64Data(getBinaryData(result, "data"));
        assertThat(csvContent).contains(";");
    }

    @Test
    void convertToCsvWithTabDelimiter() {
        List<Map<String, Object>> input = items(Map.of("x", "1", "y", "2"));
        Map<String, Object> params = mutableMap(
                "operation", "csv",
                "outputField", "data",
                "delimiter", "\t",
                "headerRow", true,
                "fileName", "tab.tsv"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String csvContent = decodeBase64Data(getBinaryData(result, "data"));
        assertThat(csvContent).contains("\t");
    }

    // -- Convert to JSON file --

    @Test
    void convertToJsonFileProducesValidJson() throws Exception {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice", "age", 30),
                Map.of("name", "Bob", "age", 25)
        );
        Map<String, Object> params = mutableMap(
                "operation", "toJson",
                "outputField", "data",
                "fileName", "data.json"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        Map<String, Object> binaryInfo = getBinaryData(result, "data");
        assertThat(binaryInfo.get("mimeType")).isEqualTo("application/json");
        assertThat(binaryInfo.get("fileName")).isEqualTo("data.json");

        String jsonContent = decodeBase64Data(binaryInfo);
        List<?> parsed = MAPPER.readValue(jsonContent, List.class);
        assertThat(parsed).hasSize(2);
    }

    @Test
    void convertToJsonFileSingleItem() throws Exception {
        List<Map<String, Object>> input = items(Map.of("key", "value"));
        Map<String, Object> params = mutableMap(
                "operation", "toJson",
                "outputField", "data",
                "fileName", "single.json"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String jsonContent = decodeBase64Data(getBinaryData(result, "data"));
        List<?> parsed = MAPPER.readValue(jsonContent, List.class);
        assertThat(parsed).hasSize(1);
    }

    // -- Convert to text --

    @Test
    void convertToTextFromSourceProperty() {
        List<Map<String, Object>> input = items(Map.of("text", "Hello, World!"));
        Map<String, Object> params = mutableMap(
                "operation", "toText",
                "outputField", "data",
                "sourceProperty", "text",
                "fileName", "output.txt"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        Map<String, Object> binaryInfo = getBinaryData(result, "data");
        assertThat(binaryInfo.get("mimeType")).isEqualTo("text/plain");
        assertThat(binaryInfo.get("fileName")).isEqualTo("output.txt");

        String textContent = decodeBase64Data(binaryInfo);
        assertThat(textContent).isEqualTo("Hello, World!");
    }

    @Test
    void convertToTextWithEmptyValue() {
        List<Map<String, Object>> input = items(Map.of("other", "data"));
        Map<String, Object> params = mutableMap(
                "operation", "toText",
                "outputField", "data",
                "sourceProperty", "nonexistent",
                "fileName", "empty.txt"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String textContent = decodeBase64Data(getBinaryData(result, "data"));
        assertThat(textContent).isEmpty();
    }

    // -- Custom output field name --

    @Test
    void customOutputFieldName() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice"));
        Map<String, Object> params = mutableMap(
                "operation", "toJson",
                "outputField", "myFile",
                "fileName", "data.json"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        Map<String, Object> binaryInfo = getBinaryData(result, "myFile");
        assertThat(binaryInfo).isNotNull();
        assertThat(binaryInfo.get("mimeType")).isEqualTo("application/json");
    }

    // -- Empty input --

    @Test
    void convertToCsvWithEmptyInputProducesHeaderOnlyFile() {
        List<Map<String, Object>> input = List.of();
        Map<String, Object> params = mutableMap(
                "operation", "csv",
                "outputField", "data",
                "delimiter", ",",
                "headerRow", true,
                "fileName", "empty.csv"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        // With empty items the CSV has no keys and no data
        assertThat(output(result)).hasSize(1);
    }

    // -- File size is populated --

    @Test
    void fileSizeIsPopulatedInBinaryInfo() {
        List<Map<String, Object>> input = items(Map.of("name", "test"));
        Map<String, Object> params = mutableMap(
                "operation", "toJson",
                "outputField", "data",
                "fileName", "data.json"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        Map<String, Object> binaryInfo = getBinaryData(result, "data");
        assertThat(binaryInfo.get("fileSize")).isNotNull();
        assertThat((int) binaryInfo.get("fileSize")).isGreaterThan(0);
    }

    // -- CSV quoting for special characters --

    @Test
    void csvQuotesFieldsContainingDelimiter() {
        List<Map<String, Object>> input = items(Map.of("name", "Doe, Jane", "age", 25));
        Map<String, Object> params = mutableMap(
                "operation", "csv",
                "outputField", "data",
                "delimiter", ",",
                "headerRow", true,
                "fileName", "quoted.csv"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String csvContent = decodeBase64Data(getBinaryData(result, "data"));
        assertThat(csvContent).contains("\"Doe, Jane\"");
    }
}
