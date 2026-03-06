package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Google Calendar — manage calendars and events via the Google Calendar API.
 */
@Node(
		type = "googleCalendar",
		displayName = "Google Calendar",
		description = "Manage Google Calendar events",
		category = "Google",
		icon = "googleCalendar",
		credentials = {"googleCalendarOAuth2Api"}
)
public class GoogleCalendarNode extends AbstractApiNode {

	private static final String BASE_URL = "https://www.googleapis.com/calendar/v3";

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

		params.add(NodeParameter.builder()
				.name("resource").displayName("Resource")
				.type(ParameterType.OPTIONS).required(true).defaultValue("event")
				.noDataExpression(true)
				.options(List.of(
						ParameterOption.builder().name("Calendar").value("calendar")
								.description("Get calendars").build(),
						ParameterOption.builder().name("Event").value("event")
								.description("Manage calendar events").build()
				)).build());

		// Calendar operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("calendar"))))
				.options(List.of(
						ParameterOption.builder().name("Get All").value("getAll")
								.description("Get all calendars").build()
				)).build());

		// Event operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("event"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").description("Create an event").build(),
						ParameterOption.builder().name("Delete").value("delete").description("Delete an event").build(),
						ParameterOption.builder().name("Get").value("get").description("Get an event").build(),
						ParameterOption.builder().name("Get All").value("getAll").description("Get all events").build(),
						ParameterOption.builder().name("Update").value("update").description("Update an event").build()
				)).build());

		// Calendar ID (used by event operations)
		params.add(NodeParameter.builder()
				.name("calendarId").displayName("Calendar ID")
				.type(ParameterType.STRING).required(true).defaultValue("primary")
				.description("Calendar identifier. Use 'primary' for the main calendar.")
				.placeHolder("primary")
				.displayOptions(Map.of("show", Map.of("resource", List.of("event"))))
				.build());

		// Event > Create parameters
		params.add(NodeParameter.builder()
				.name("summary").displayName("Summary")
				.type(ParameterType.STRING).required(true)
				.description("Title of the event.")
				.placeHolder("Team Meeting")
				.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("startDateTime").displayName("Start Date Time")
				.type(ParameterType.STRING).required(true)
				.description("Start time in RFC 3339 format (e.g., 2024-01-15T09:00:00-05:00).")
				.placeHolder("2024-01-15T09:00:00-05:00")
				.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("endDateTime").displayName("End Date Time")
				.type(ParameterType.STRING).required(true)
				.description("End time in RFC 3339 format.")
				.placeHolder("2024-01-15T10:00:00-05:00")
				.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("useDefaultReminders").displayName("Use Default Reminders")
				.type(ParameterType.BOOLEAN).defaultValue(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("additionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("description").displayName("Description")
								.type(ParameterType.STRING)
								.typeOptions(Map.of("rows", 4))
								.build(),
						NodeParameter.builder().name("location").displayName("Location")
								.type(ParameterType.STRING)
								.placeHolder("Conference Room A")
								.build(),
						NodeParameter.builder().name("colorId").displayName("Color ID")
								.type(ParameterType.STRING)
								.description("Color ID (1-11) for the event.")
								.build(),
						NodeParameter.builder().name("attendees").displayName("Attendees")
								.type(ParameterType.STRING)
								.description("Comma-separated email addresses of attendees.")
								.placeHolder("user1@example.com,user2@example.com")
								.build(),
						NodeParameter.builder().name("recurrence").displayName("Recurrence Rule")
								.type(ParameterType.STRING)
								.description("RRULE string (e.g., RRULE:FREQ=WEEKLY;COUNT=10).")
								.build(),
						NodeParameter.builder().name("timeZone").displayName("Time Zone")
								.type(ParameterType.STRING)
								.defaultValue("UTC")
								.placeHolder("America/New_York")
								.build(),
						NodeParameter.builder().name("visibility").displayName("Visibility")
								.type(ParameterType.OPTIONS)
								.options(List.of(
										ParameterOption.builder().name("Default").value("default").build(),
										ParameterOption.builder().name("Public").value("public").build(),
										ParameterOption.builder().name("Private").value("private").build(),
										ParameterOption.builder().name("Confidential").value("confidential").build()
								)).build(),
						NodeParameter.builder().name("sendUpdates").displayName("Send Updates")
								.type(ParameterType.OPTIONS).defaultValue("none")
								.options(List.of(
										ParameterOption.builder().name("All").value("all").build(),
										ParameterOption.builder().name("External Only").value("externalOnly").build(),
										ParameterOption.builder().name("None").value("none").build()
								)).build(),
						NodeParameter.builder().name("conferenceDataEnabled").displayName("Add Conference")
								.type(ParameterType.BOOLEAN).defaultValue(false)
								.description("Add a Google Meet conference link.")
								.build()
				)).build());

		// Event > Get / Delete / Update: eventId
		params.add(NodeParameter.builder()
				.name("eventId").displayName("Event ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("get", "delete", "update"))))
				.build());

		// Event > Update: update fields
		params.add(NodeParameter.builder()
				.name("updateFields").displayName("Update Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("update"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("summary").displayName("Summary")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("description").displayName("Description")
								.type(ParameterType.STRING).typeOptions(Map.of("rows", 4)).build(),
						NodeParameter.builder().name("location").displayName("Location")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("startDateTime").displayName("Start Date Time")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("endDateTime").displayName("End Date Time")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("colorId").displayName("Color ID")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("attendees").displayName("Attendees")
								.type(ParameterType.STRING)
								.description("Comma-separated email addresses.").build(),
						NodeParameter.builder().name("visibility").displayName("Visibility")
								.type(ParameterType.OPTIONS)
								.options(List.of(
										ParameterOption.builder().name("Default").value("default").build(),
										ParameterOption.builder().name("Public").value("public").build(),
										ParameterOption.builder().name("Private").value("private").build(),
										ParameterOption.builder().name("Confidential").value("confidential").build()
								)).build(),
						NodeParameter.builder().name("status").displayName("Status")
								.type(ParameterType.OPTIONS)
								.options(List.of(
										ParameterOption.builder().name("Confirmed").value("confirmed").build(),
										ParameterOption.builder().name("Tentative").value("tentative").build(),
										ParameterOption.builder().name("Cancelled").value("cancelled").build()
								)).build()
				)).build());

		// Event > GetAll: filters
		params.add(NodeParameter.builder()
				.name("eventFilters").displayName("Filters")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("getAll"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("timeMin").displayName("Time Min")
								.type(ParameterType.STRING)
								.description("Lower bound for end time (RFC 3339 format).")
								.build(),
						NodeParameter.builder().name("timeMax").displayName("Time Max")
								.type(ParameterType.STRING)
								.description("Upper bound for start time (RFC 3339 format).")
								.build(),
						NodeParameter.builder().name("query").displayName("Search Query")
								.type(ParameterType.STRING)
								.description("Free text search terms.")
								.build(),
						NodeParameter.builder().name("singleEvents").displayName("Single Events")
								.type(ParameterType.BOOLEAN).defaultValue(true)
								.description("Expand recurring events into instances.")
								.build(),
						NodeParameter.builder().name("orderBy").displayName("Order By")
								.type(ParameterType.OPTIONS).defaultValue("startTime")
								.options(List.of(
										ParameterOption.builder().name("Start Time").value("startTime").build(),
										ParameterOption.builder().name("Updated").value("updated").build()
								)).build()
				)).build());

		params.add(NodeParameter.builder()
				.name("limit").displayName("Limit")
				.type(ParameterType.NUMBER).defaultValue(50)
				.description("Maximum number of events to return.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("getAll"))))
				.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "event");
		Map<String, Object> credentials = context.getCredentials();

		try {
			return switch (resource) {
				case "calendar" -> executeCalendar(context, credentials);
				case "event" -> executeEvent(context, credentials);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Google Calendar error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeCalendar(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		HttpResponse<String> response = get(BASE_URL + "/users/me/calendarList", headers);
		Map<String, Object> result = parseResponse(response);

		Object items = result.get("items");
		if (items instanceof List) {
			List<Map<String, Object>> calendarItems = new ArrayList<>();
			for (Object item : (List<?>) items) {
				if (item instanceof Map) {
					calendarItems.add(wrapInJson(item));
				}
			}
			return calendarItems.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(calendarItems);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private NodeExecutionResult executeEvent(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);
		String calendarId = context.getParameter("calendarId", "primary");
		String eventsUrl = BASE_URL + "/calendars/" + encode(calendarId) + "/events";

		switch (operation) {
			case "create": {
				String summary = context.getParameter("summary", "");
				String startDateTime = context.getParameter("startDateTime", "");
				String endDateTime = context.getParameter("endDateTime", "");
				boolean useDefaultReminders = toBoolean(context.getParameter("useDefaultReminders", true), true);
				Map<String, Object> additionalFields = context.getParameter("additionalFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("summary", summary);

				String timeZone = additionalFields.get("timeZone") != null
						? (String) additionalFields.get("timeZone") : "UTC";

				body.put("start", Map.of("dateTime", startDateTime, "timeZone", timeZone));
				body.put("end", Map.of("dateTime", endDateTime, "timeZone", timeZone));

				if (additionalFields.get("description") != null) {
					body.put("description", additionalFields.get("description"));
				}
				if (additionalFields.get("location") != null) {
					body.put("location", additionalFields.get("location"));
				}
				if (additionalFields.get("colorId") != null) {
					body.put("colorId", additionalFields.get("colorId"));
				}
				if (additionalFields.get("visibility") != null) {
					body.put("visibility", additionalFields.get("visibility"));
				}
				if (additionalFields.get("attendees") != null) {
					String attendeesStr = (String) additionalFields.get("attendees");
					List<Map<String, Object>> attendees = new ArrayList<>();
					for (String email : attendeesStr.split(",")) {
						attendees.add(Map.of("email", email.trim()));
					}
					body.put("attendees", attendees);
				}
				if (additionalFields.get("recurrence") != null) {
					body.put("recurrence", List.of(additionalFields.get("recurrence")));
				}
				if (!useDefaultReminders) {
					body.put("reminders", Map.of("useDefault", false));
				}
				if (toBoolean(additionalFields.get("conferenceDataEnabled"), false)) {
					body.put("conferenceData", Map.of(
							"createRequest", Map.of(
									"requestId", UUID.randomUUID().toString(),
									"conferenceSolutionKey", Map.of("type", "hangoutsMeet")
							)
					));
				}

				String url = eventsUrl;
				String sendUpdates = additionalFields.get("sendUpdates") != null
						? (String) additionalFields.get("sendUpdates") : "none";
				url += "?sendUpdates=" + sendUpdates;
				if (toBoolean(additionalFields.get("conferenceDataEnabled"), false)) {
					url += "&conferenceDataVersion=1";
				}

				HttpResponse<String> response = post(url, body, headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "delete": {
				String eventId = context.getParameter("eventId", "");
				HttpResponse<String> response = delete(eventsUrl + "/" + encode(eventId), headers);
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "deleted", eventId))));
				}
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "get": {
				String eventId = context.getParameter("eventId", "");
				HttpResponse<String> response = get(eventsUrl + "/" + encode(eventId), headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				Map<String, Object> filters = context.getParameter("eventFilters", Map.of());

				StringBuilder url = new StringBuilder(eventsUrl + "?maxResults=" + limit);
				if (filters.get("timeMin") != null) {
					url.append("&timeMin=").append(encode((String) filters.get("timeMin")));
				}
				if (filters.get("timeMax") != null) {
					url.append("&timeMax=").append(encode((String) filters.get("timeMax")));
				}
				if (filters.get("query") != null) {
					url.append("&q=").append(encode((String) filters.get("query")));
				}
				if (filters.get("singleEvents") != null) {
					url.append("&singleEvents=").append(toBoolean(filters.get("singleEvents"), true));
				}
				if (filters.get("orderBy") != null) {
					url.append("&orderBy=").append(encode((String) filters.get("orderBy")));
				}

				HttpResponse<String> response = get(url.toString(), headers);
				Map<String, Object> result = parseResponse(response);

				Object items = result.get("items");
				if (items instanceof List) {
					List<Map<String, Object>> eventItems = new ArrayList<>();
					for (Object item : (List<?>) items) {
						if (item instanceof Map) {
							eventItems.add(wrapInJson(item));
						}
					}
					return eventItems.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(eventItems);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "update": {
				String eventId = context.getParameter("eventId", "");
				Map<String, Object> updateFields = context.getParameter("updateFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				if (updateFields.get("summary") != null) {
					body.put("summary", updateFields.get("summary"));
				}
				if (updateFields.get("description") != null) {
					body.put("description", updateFields.get("description"));
				}
				if (updateFields.get("location") != null) {
					body.put("location", updateFields.get("location"));
				}
				if (updateFields.get("colorId") != null) {
					body.put("colorId", updateFields.get("colorId"));
				}
				if (updateFields.get("visibility") != null) {
					body.put("visibility", updateFields.get("visibility"));
				}
				if (updateFields.get("status") != null) {
					body.put("status", updateFields.get("status"));
				}
				if (updateFields.get("startDateTime") != null) {
					body.put("start", Map.of("dateTime", updateFields.get("startDateTime")));
				}
				if (updateFields.get("endDateTime") != null) {
					body.put("end", Map.of("dateTime", updateFields.get("endDateTime")));
				}
				if (updateFields.get("attendees") != null) {
					String attendeesStr = (String) updateFields.get("attendees");
					List<Map<String, Object>> attendees = new ArrayList<>();
					for (String email : attendeesStr.split(",")) {
						attendees.add(Map.of("email", email.trim()));
					}
					body.put("attendees", attendees);
				}

				HttpResponse<String> response = patch(eventsUrl + "/" + encode(eventId), body, headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			default:
				return NodeExecutionResult.error("Unknown event operation: " + operation);
		}
	}

	private Map<String, String> getAuthHeaders(String accessToken) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}
}
