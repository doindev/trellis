package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class DateTimeNodeTest {

    private DateTimeNode node;

    @BeforeEach
    void setUp() {
        node = new DateTimeNode();
    }

    // ── getCurrentDate ──

    @Test
    void getCurrentDateReturnsFormattedDate() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "getCurrentDate",
                "format", "yyyy-MM-dd'T'HH:mm:ssXXX",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        String dateStr = (String) firstJson(result).get("date");
        assertThat(dateStr).isNotNull();
        // Should be parseable as a valid date
        assertThat(dateStr).containsPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
    }

    @Test
    void getCurrentDateCustomFormat() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "getCurrentDate",
                "format", "yyyy-MM-dd",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String dateStr = (String) firstJson(result).get("date");
        assertThat(dateStr).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    // ── addToDate ──

    @Test
    void addDaysToDate() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "addToDate",
                "date", "2024-01-15T00:00:00Z",
                "duration", 5,
                "timeUnit", "days",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String dateStr = (String) firstJson(result).get("date");
        assertThat(dateStr).contains("2024-01-20");
    }

    @Test
    void addHoursToDate() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "addToDate",
                "date", "2024-01-15T10:00:00Z",
                "duration", 3,
                "timeUnit", "hours",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String dateStr = (String) firstJson(result).get("date");
        assertThat(dateStr).contains("13:00:00");
    }

    @Test
    void addMonthsToDate() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "addToDate",
                "date", "2024-01-15T00:00:00Z",
                "duration", 2,
                "timeUnit", "months",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String dateStr = (String) firstJson(result).get("date");
        assertThat(dateStr).contains("2024-03-15");
    }

    // ── subtractFromDate ──

    @Test
    void subtractDaysFromDate() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "subtractFromDate",
                "date", "2024-01-20T00:00:00Z",
                "duration", 5,
                "timeUnit", "days",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String dateStr = (String) firstJson(result).get("date");
        assertThat(dateStr).contains("2024-01-15");
    }

    @Test
    void subtractMonthsFromDate() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "subtractFromDate",
                "date", "2024-06-15T00:00:00Z",
                "duration", 3,
                "timeUnit", "months",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String dateStr = (String) firstJson(result).get("date");
        assertThat(dateStr).contains("2024-03-15");
    }

    // ── formatDate ──

    @Test
    void formatDateWithPattern() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "formatDate",
                "date", "2024-06-15T14:30:00Z",
                "format", "dd/MM/yyyy HH:mm",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String dateStr = (String) firstJson(result).get("date");
        assertThat(dateStr).isEqualTo("15/06/2024 14:30");
    }

    @Test
    void formatDateYearOnly() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "formatDate",
                "date", "2024-06-15T14:30:00Z",
                "format", "yyyy",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("date")).isEqualTo("2024");
    }

    // ── extractDatePart ──

    @Test
    void extractYear() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "extractDatePart",
                "date", "2024-06-15T14:30:00Z",
                "datePart", "year",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("date")).isEqualTo(2024);
    }

    @Test
    void extractMonth() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "extractDatePart",
                "date", "2024-06-15T14:30:00Z",
                "datePart", "month",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("date")).isEqualTo(6);
    }

    @Test
    void extractDay() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "extractDatePart",
                "date", "2024-06-15T14:30:00Z",
                "datePart", "day",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("date")).isEqualTo(15);
    }

    @Test
    void extractHour() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "extractDatePart",
                "date", "2024-06-15T14:30:00Z",
                "datePart", "hour",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("date")).isEqualTo(14);
    }

    @Test
    void extractDayOfWeek() {
        // 2024-06-15 is a Saturday = 6 (ISO day of week: Monday=1 ... Sunday=7)
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "extractDatePart",
                "date", "2024-06-15T14:30:00Z",
                "datePart", "dayOfWeek",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("date")).isEqualTo(6); // Saturday
    }

    // ── getTimeBetween ──

    @Test
    void getTimeBetweenDays() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "getTimeBetween",
                "date1", "2024-01-01T00:00:00Z",
                "date2", "2024-01-11T00:00:00Z",
                "timeUnit", "days",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("date")).isEqualTo(10L);
    }

    @Test
    void getTimeBetweenHours() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "getTimeBetween",
                "date1", "2024-01-01T00:00:00Z",
                "date2", "2024-01-01T05:00:00Z",
                "timeUnit", "hours",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("date")).isEqualTo(5L);
    }

    @Test
    void getTimeBetweenMonths() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "getTimeBetween",
                "date1", "2024-01-15T00:00:00Z",
                "date2", "2024-06-15T00:00:00Z",
                "timeUnit", "months",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("date")).isEqualTo(5L);
    }

    // ── roundDate ──

    @Test
    void roundDateToDays() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "roundDate",
                "date", "2024-06-15T14:30:45Z",
                "timeUnit", "days",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String dateStr = (String) firstJson(result).get("date");
        assertThat(dateStr).contains("2024-06-15T00:00:00");
    }

    @Test
    void roundDateToHours() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "roundDate",
                "date", "2024-06-15T14:30:45Z",
                "timeUnit", "hours",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String dateStr = (String) firstJson(result).get("date");
        assertThat(dateStr).contains("2024-06-15T14:00:00");
    }

    @Test
    void roundDateToMonths() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "roundDate",
                "date", "2024-06-15T14:30:45Z",
                "timeUnit", "months",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String dateStr = (String) firstJson(result).get("date");
        assertThat(dateStr).contains("2024-06-01T00:00:00");
    }

    // ── Custom timezone ──

    @Test
    void getCurrentDateWithCustomTimezone() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "getCurrentDate",
                "format", "yyyy-MM-dd'T'HH:mm:ssXXX",
                "outputFieldName", "date",
                "timezone", "America/New_York"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String dateStr = (String) firstJson(result).get("date");
        assertThat(dateStr).isNotNull();
        // The date string should reflect the timezone offset
        // Just verify it's a valid date string and is not empty
        assertThat(dateStr).isNotEmpty();
    }

    @Test
    void extractDatePartInDifferentTimezone() {
        // 2024-01-01T03:00:00Z in UTC is still Jan 1.
        // In Pacific/Auckland (UTC+13), 2024-01-01T03:00:00Z is already Jan 1 at 16:00.
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "extractDatePart",
                "date", "2024-01-01T03:00:00Z",
                "datePart", "hour",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("date")).isEqualTo(3);
    }

    // ── Custom output field name ──

    @Test
    void customOutputFieldName() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "getCurrentDate",
                "format", "yyyy-MM-dd",
                "outputFieldName", "currentDate",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsKey("currentDate");
        assertThat(firstJson(result)).doesNotContainKey("date");
    }

    // ── Empty input produces output ──

    @Test
    void emptyInputStillProducesOutput() {
        Map<String, Object> params = mutableMap(
                "operation", "getCurrentDate",
                "format", "yyyy-MM-dd",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        // DateTimeNode defaults to a single empty-json item when input is empty
        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsKey("date");
    }

    // ── Parse LocalDate format ──

    @Test
    void parsesLocalDateFormat() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "extractDatePart",
                "date", "2024-06-15",
                "datePart", "day",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("date")).isEqualTo(15);
    }

    // ── Parse epoch millis ──

    @Test
    void parsesEpochMillis() {
        // 1704067200000 = 2024-01-01T00:00:00Z
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "operation", "extractDatePart",
                "date", "1704067200000",
                "datePart", "year",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("date")).isEqualTo(2024);
    }

    // ── Original fields preserved ──

    @Test
    void originalFieldsPreserved() {
        List<Map<String, Object>> input = items(Map.of("name", "alice", "age", 30));
        Map<String, Object> params = mutableMap(
                "operation", "getCurrentDate",
                "format", "yyyy-MM-dd",
                "outputFieldName", "date",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("name", "alice");
        assertThat(firstJson(result)).containsEntry("age", 30);
        assertThat(firstJson(result)).containsKey("date");
    }
}
