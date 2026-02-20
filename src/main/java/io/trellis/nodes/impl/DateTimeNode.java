package io.trellis.nodes.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * Date & Time Node - manipulates dates and times.
 * Operations: add/subtract duration, extract parts, format, get current date,
 * calculate time between two dates, round date.
 */
@Slf4j
@Node(
	type = "dateTime",
	displayName = "Date & Time",
	description = "Manipulate dates and times: add, subtract, format, extract parts, calculate differences.",
	category = "Data Transformation",
	icon = "calendar-clock"
)
public class DateTimeNode extends AbstractNode {

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
		return List.of(
			NodeParameter.builder()
				.name("operation")
				.displayName("Operation")
				.type(ParameterType.OPTIONS)
				.defaultValue("getCurrentDate")
				.options(List.of(
					ParameterOption.builder().name("Get Current Date").value("getCurrentDate")
						.description("Get the current date and time").build(),
					ParameterOption.builder().name("Add to a Date").value("addToDate")
						.description("Add a duration to a date").build(),
					ParameterOption.builder().name("Subtract from a Date").value("subtractFromDate")
						.description("Subtract a duration from a date").build(),
					ParameterOption.builder().name("Format a Date").value("formatDate")
						.description("Format a date into a different string format").build(),
					ParameterOption.builder().name("Extract Date Part").value("extractDatePart")
						.description("Extract a specific part from a date (year, month, day, etc.)").build(),
					ParameterOption.builder().name("Get Time Between Dates").value("getTimeBetween")
						.description("Calculate the time difference between two dates").build(),
					ParameterOption.builder().name("Round a Date").value("roundDate")
						.description("Round a date to the nearest unit").build()
				))
				.build(),

			NodeParameter.builder()
				.name("date")
				.displayName("Date")
				.description("The date to operate on. Use an expression like ={{ $json.dateField }}.")
				.type(ParameterType.STRING)
				.displayOptions(Map.of("show", Map.of("operation",
					List.of("addToDate", "subtractFromDate", "formatDate", "extractDatePart", "roundDate"))))
				.build(),

			NodeParameter.builder()
				.name("date1")
				.displayName("Start Date")
				.description("The start date for comparison.")
				.type(ParameterType.STRING)
				.displayOptions(Map.of("show", Map.of("operation", List.of("getTimeBetween"))))
				.build(),

			NodeParameter.builder()
				.name("date2")
				.displayName("End Date")
				.description("The end date for comparison.")
				.type(ParameterType.STRING)
				.displayOptions(Map.of("show", Map.of("operation", List.of("getTimeBetween"))))
				.build(),

			NodeParameter.builder()
				.name("duration")
				.displayName("Duration")
				.description("The number of units to add or subtract.")
				.type(ParameterType.NUMBER)
				.defaultValue(0)
				.displayOptions(Map.of("show", Map.of("operation", List.of("addToDate", "subtractFromDate"))))
				.build(),

			NodeParameter.builder()
				.name("timeUnit")
				.displayName("Time Unit")
				.type(ParameterType.OPTIONS)
				.defaultValue("days")
				.displayOptions(Map.of("show", Map.of("operation",
					List.of("addToDate", "subtractFromDate", "getTimeBetween", "roundDate"))))
				.options(List.of(
					ParameterOption.builder().name("Seconds").value("seconds").build(),
					ParameterOption.builder().name("Minutes").value("minutes").build(),
					ParameterOption.builder().name("Hours").value("hours").build(),
					ParameterOption.builder().name("Days").value("days").build(),
					ParameterOption.builder().name("Weeks").value("weeks").build(),
					ParameterOption.builder().name("Months").value("months").build(),
					ParameterOption.builder().name("Years").value("years").build()
				))
				.build(),

			NodeParameter.builder()
				.name("datePart")
				.displayName("Date Part")
				.type(ParameterType.OPTIONS)
				.defaultValue("year")
				.displayOptions(Map.of("show", Map.of("operation", List.of("extractDatePart"))))
				.options(List.of(
					ParameterOption.builder().name("Year").value("year").build(),
					ParameterOption.builder().name("Month").value("month").build(),
					ParameterOption.builder().name("Week").value("week").build(),
					ParameterOption.builder().name("Day").value("day").build(),
					ParameterOption.builder().name("Hour").value("hour").build(),
					ParameterOption.builder().name("Minute").value("minute").build(),
					ParameterOption.builder().name("Second").value("second").build(),
					ParameterOption.builder().name("Day of Week").value("dayOfWeek").build()
				))
				.build(),

			NodeParameter.builder()
				.name("format")
				.displayName("Format")
				.description("The output date format (Java DateTimeFormatter pattern, e.g. yyyy-MM-dd HH:mm:ss).")
				.type(ParameterType.STRING)
				.defaultValue("yyyy-MM-dd'T'HH:mm:ssXXX")
				.displayOptions(Map.of("show", Map.of("operation", List.of("formatDate", "getCurrentDate"))))
				.build(),

			NodeParameter.builder()
				.name("outputFieldName")
				.displayName("Output Field Name")
				.description("The field name for the result.")
				.type(ParameterType.STRING)
				.defaultValue("date")
				.build(),

			NodeParameter.builder()
				.name("timezone")
				.displayName("Timezone")
				.description("Timezone to use (e.g. UTC, America/New_York, Europe/London).")
				.type(ParameterType.STRING)
				.defaultValue("UTC")
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			inputData = List.of(Map.of("json", Map.of()));
		}

		String operation = context.getParameter("operation", "getCurrentDate");
		String outputFieldName = context.getParameter("outputFieldName", "date");
		String timezone = context.getParameter("timezone", "UTC");
		ZoneId zoneId;
		try {
			zoneId = ZoneId.of(timezone);
		} catch (Exception e) {
			zoneId = ZoneOffset.UTC;
		}

		try {
			List<Map<String, Object>> result = new ArrayList<>();

			for (Map<String, Object> item : inputData) {
				Map<String, Object> json = new LinkedHashMap<>(unwrapJson(item));
				Object resultValue = null;

				switch (operation) {
					case "getCurrentDate": {
						String format = context.getParameter("format", "yyyy-MM-dd'T'HH:mm:ssXXX");
						ZonedDateTime now = ZonedDateTime.now(zoneId);
						resultValue = now.format(DateTimeFormatter.ofPattern(format));
						break;
					}

					case "addToDate": {
						ZonedDateTime date = parseDate(context.getParameter("date", ""), zoneId);
						int duration = toInt(context.getParameter("duration", 0), 0);
						String timeUnit = context.getParameter("timeUnit", "days");
						resultValue = addDuration(date, duration, timeUnit)
							.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
						break;
					}

					case "subtractFromDate": {
						ZonedDateTime date = parseDate(context.getParameter("date", ""), zoneId);
						int duration = toInt(context.getParameter("duration", 0), 0);
						String timeUnit = context.getParameter("timeUnit", "days");
						resultValue = addDuration(date, -duration, timeUnit)
							.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
						break;
					}

					case "formatDate": {
						ZonedDateTime date = parseDate(context.getParameter("date", ""), zoneId);
						String format = context.getParameter("format", "yyyy-MM-dd'T'HH:mm:ssXXX");
						resultValue = date.format(DateTimeFormatter.ofPattern(format));
						break;
					}

					case "extractDatePart": {
						ZonedDateTime date = parseDate(context.getParameter("date", ""), zoneId);
						String datePart = context.getParameter("datePart", "year");
						resultValue = extractPart(date, datePart);
						break;
					}

					case "getTimeBetween": {
						ZonedDateTime date1 = parseDate(context.getParameter("date1", ""), zoneId);
						ZonedDateTime date2 = parseDate(context.getParameter("date2", ""), zoneId);
						String timeUnit = context.getParameter("timeUnit", "days");
						resultValue = getTimeBetween(date1, date2, timeUnit);
						break;
					}

					case "roundDate": {
						ZonedDateTime date = parseDate(context.getParameter("date", ""), zoneId);
						String timeUnit = context.getParameter("timeUnit", "days");
						resultValue = roundDate(date, timeUnit)
							.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
						break;
					}
				}

				json.put(outputFieldName, resultValue);
				result.add(wrapInJson(json));
			}

			log.debug("DateTime: operation={}, outputField={}", operation, outputFieldName);
			return NodeExecutionResult.success(result);
		} catch (Exception e) {
			return handleError(context, "DateTime node error: " + e.getMessage(), e);
		}
	}

	private ZonedDateTime parseDate(String dateStr, ZoneId zoneId) {
		if (dateStr == null || dateStr.isBlank()) {
			return ZonedDateTime.now(zoneId);
		}
		dateStr = dateStr.trim();

		// Try epoch millis
		try {
			long millis = Long.parseLong(dateStr);
			return Instant.ofEpochMilli(millis).atZone(zoneId);
		} catch (NumberFormatException ignored) {}

		// Try ISO instant (2024-01-01T00:00:00Z)
		try {
			return Instant.parse(dateStr).atZone(zoneId);
		} catch (Exception ignored) {}

		// Try ZonedDateTime
		try {
			return ZonedDateTime.parse(dateStr);
		} catch (Exception ignored) {}

		// Try LocalDateTime
		try {
			return LocalDateTime.parse(dateStr).atZone(zoneId);
		} catch (Exception ignored) {}

		// Try LocalDate
		try {
			return LocalDate.parse(dateStr).atStartOfDay(zoneId);
		} catch (Exception ignored) {}

		// Try common formats
		String[] formats = {
			"yyyy-MM-dd HH:mm:ss", "MM/dd/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm:ss",
			"yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy"
		};
		for (String format : formats) {
			try {
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
				if (format.contains("HH")) {
					return LocalDateTime.parse(dateStr, formatter).atZone(zoneId);
				}
				return LocalDate.parse(dateStr, formatter).atStartOfDay(zoneId);
			} catch (Exception ignored) {}
		}

		throw new IllegalArgumentException("Cannot parse date: " + dateStr);
	}

	private ZonedDateTime addDuration(ZonedDateTime date, long amount, String unit) {
		switch (unit) {
			case "seconds": return date.plusSeconds(amount);
			case "minutes": return date.plusMinutes(amount);
			case "hours": return date.plusHours(amount);
			case "days": return date.plusDays(amount);
			case "weeks": return date.plusWeeks(amount);
			case "months": return date.plusMonths(amount);
			case "years": return date.plusYears(amount);
			default: return date.plusDays(amount);
		}
	}

	private Object extractPart(ZonedDateTime date, String part) {
		switch (part) {
			case "year": return date.getYear();
			case "month": return date.getMonthValue();
			case "week": return date.get(ChronoField.ALIGNED_WEEK_OF_YEAR);
			case "day": return date.getDayOfMonth();
			case "hour": return date.getHour();
			case "minute": return date.getMinute();
			case "second": return date.getSecond();
			case "dayOfWeek": return date.getDayOfWeek().getValue();
			default: return date.toString();
		}
	}

	private long getTimeBetween(ZonedDateTime date1, ZonedDateTime date2, String unit) {
		switch (unit) {
			case "seconds": return ChronoUnit.SECONDS.between(date1, date2);
			case "minutes": return ChronoUnit.MINUTES.between(date1, date2);
			case "hours": return ChronoUnit.HOURS.between(date1, date2);
			case "days": return ChronoUnit.DAYS.between(date1, date2);
			case "weeks": return ChronoUnit.WEEKS.between(date1, date2);
			case "months": return ChronoUnit.MONTHS.between(date1, date2);
			case "years": return ChronoUnit.YEARS.between(date1, date2);
			default: return ChronoUnit.DAYS.between(date1, date2);
		}
	}

	private ZonedDateTime roundDate(ZonedDateTime date, String unit) {
		switch (unit) {
			case "seconds": return date.truncatedTo(ChronoUnit.SECONDS);
			case "minutes": return date.truncatedTo(ChronoUnit.MINUTES);
			case "hours": return date.truncatedTo(ChronoUnit.HOURS);
			case "days": return date.truncatedTo(ChronoUnit.DAYS);
			case "weeks": return date.with(ChronoField.DAY_OF_WEEK, 1).truncatedTo(ChronoUnit.DAYS);
			case "months": return date.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
			case "years": return date.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS);
			default: return date.truncatedTo(ChronoUnit.DAYS);
		}
	}
}
