package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class SpreadsheetFileNodeTest {

    private SpreadsheetFileNode node;

    @BeforeEach
    void setUp() {
        node = new SpreadsheetFileNode();
    }

    // -- From JSON to CSV (fromJson) --

    @Test
    void fromJsonToCsvProducesDelimitedOutput() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice", "age", 30),
                Map.of("name", "Bob", "age", 25)
        );
        Map<String, Object> params = mutableMap(
                "operation", "fromJson",
                "format", "csv",
                "includeHeader", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        String data = (String) firstJson(result).get("data");
        assertThat(data).contains(",");
        assertThat(data).contains("name");
        assertThat(data).contains("Alice");
        assertThat(data).contains("Bob");
    }

    @Test
    void fromJsonToCsvWithoutHeader() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice", "age", 30)
        );
        Map<String, Object> params = mutableMap(
                "operation", "fromJson",
                "format", "csv",
                "includeHeader", false
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String data = (String) firstJson(result).get("data");
        String[] lines = data.trim().split("\n");
        // Without header, only data rows
        assertThat(lines.length).isEqualTo(1);
        assertThat(data).doesNotContain("name");
    }

    @Test
    void fromJsonToCsvIncludesMetadata() {
        List<Map<String, Object>> input = items(
                Map.of("a", "1", "b", "2"),
                Map.of("a", "3", "b", "4")
        );
        Map<String, Object> params = mutableMap(
                "operation", "fromJson",
                "format", "csv",
                "includeHeader", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("rowCount", 2);
        assertThat(firstJson(result)).containsEntry("columnCount", 2);
        assertThat(firstJson(result)).containsKey("columns");
        assertThat(firstJson(result)).containsEntry("format", "csv");
    }

    @Test
    void fromJsonToCsvWithSpecificColumns() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice", "age", 30, "email", "alice@test.com")
        );
        Map<String, Object> params = mutableMap(
                "operation", "fromJson",
                "format", "csv",
                "includeHeader", true,
                "columns", "name,email"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String data = (String) firstJson(result).get("data");
        assertThat(data).contains("name");
        assertThat(data).contains("email");
        assertThat(data).contains("Alice");
        assertThat(data).contains("alice@test.com");
        // The age column should not be present in the header
        assertThat(firstJson(result).get("columnCount")).isEqualTo(2);
    }

    // -- CSV to JSON conversion (toJson) --

    @Test
    void toJsonFromCsvWithHeaderRow() {
        Map<String, Object> params = mutableMap(
                "operation", "toJson",
                "format", "csv",
                "spreadsheetData", "name,age\nAlice,30\nBob,25",
                "headerRow", true
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).hasSize(2);
        assertThat(jsonAt(result, 0)).containsEntry("name", "Alice");
        assertThat(jsonAt(result, 0)).containsEntry("age", "30");
        assertThat(jsonAt(result, 1)).containsEntry("name", "Bob");
        assertThat(jsonAt(result, 1)).containsEntry("age", "25");
    }

    @Test
    void toJsonFromCsvWithoutHeaderRow() {
        Map<String, Object> params = mutableMap(
                "operation", "toJson",
                "format", "csv",
                "spreadsheetData", "Alice,30\nBob,25",
                "headerRow", false
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).hasSize(2);
        // Without header, column names are column_0, column_1, ...
        assertThat(jsonAt(result, 0)).containsEntry("column_0", "Alice");
        assertThat(jsonAt(result, 0)).containsEntry("column_1", "30");
    }

    @Test
    void toJsonFromCsvWithEmptyDataReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "operation", "toJson",
                "format", "csv",
                "spreadsheetData", "",
                "headerRow", true
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).isEmpty();
    }

    // -- Custom delimiter --

    @Test
    void toJsonWithCustomDelimiter() {
        Map<String, Object> params = mutableMap(
                "operation", "toJson",
                "format", "custom",
                "delimiter", "|",
                "spreadsheetData", "name|age\nAlice|30\nBob|25",
                "headerRow", true
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).hasSize(2);
        assertThat(jsonAt(result, 0)).containsEntry("name", "Alice");
        assertThat(jsonAt(result, 0)).containsEntry("age", "30");
    }

    @Test
    void fromJsonWithTsvFormat() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice", "age", 30)
        );
        Map<String, Object> params = mutableMap(
                "operation", "fromJson",
                "format", "tsv",
                "includeHeader", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String data = (String) firstJson(result).get("data");
        assertThat(data).contains("\t");
        assertThat(firstJson(result)).containsEntry("format", "tsv");
    }

    // -- Header row handling --

    @Test
    void toJsonHeaderRowOnlyReturnsSingleRow() {
        // Only a header row with no data rows - node parses it as a single data row
        Map<String, Object> params = mutableMap(
                "operation", "toJson",
                "format", "csv",
                "spreadsheetData", "name,age",
                "headerRow", true
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        // The node treats the single row as data with auto-generated column names
        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("column_0", "name");
        assertThat(firstJson(result)).containsEntry("column_1", "age");
    }

    // -- Empty input for fromJson --

    @Test
    void fromJsonWithEmptyInputReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "operation", "fromJson",
                "format", "csv",
                "includeHeader", true
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).isEmpty();
    }

    // -- Has correct inputs and outputs --

    @Test
    void hasOneInput() {
        assertThat(node.getInputs()).hasSize(1);
        assertThat(node.getInputs().get(0).getName()).isEqualTo("main");
    }

    @Test
    void hasOneOutput() {
        assertThat(node.getOutputs()).hasSize(1);
        assertThat(node.getOutputs().get(0).getName()).isEqualTo("main");
    }

    // -- Quoted fields in CSV --

    @Test
    void toJsonHandlesQuotedFields() {
        Map<String, Object> params = mutableMap(
                "operation", "toJson",
                "format", "csv",
                "spreadsheetData", "name,city\n\"Doe, Jane\",\"New York\"\nBob,Boston",
                "headerRow", true
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).hasSize(2);
        assertThat(jsonAt(result, 0)).containsEntry("name", "Doe, Jane");
        assertThat(jsonAt(result, 0)).containsEntry("city", "New York");
    }
}
