package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;

/**
 * Google Calendar Trigger — polls for new or updated events in a Google Calendar.
 */
@Slf4j
@Node(
		type = "googleCalendarTrigger",
		displayName = "Google Calendar Trigger",
		description = "Polls for new or updated events in a Google Calendar",
		category = "Google",
		icon = "googleCalendar",
		trigger = true,
		polling = true,
		credentials = {"googleCalendarOAuth2Api"}
)
public class GoogleCalendarTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://www.googleapis.com/calendar/v3";

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
				.name("triggerOn").displayName("Trigger On")
				.type(ParameterType.OPTIONS).required(true).defaultValue("newEvents")
				.options(List.of(
						ParameterOption.builder().name("New Events").value("newEvents")
								.description("Trigger when new events are created").build(),
						ParameterOption.builder().name("Updated Events").value("updatedEvents")
								.description("Trigger when events are updated").build()
				)).build());

		params.add(NodeParameter.builder()
				.name("calendarId").displayName("Calendar ID")
				.type(ParameterType.STRING).required(true).defaultValue("primary")
				.description("Calendar identifier. Use 'primary' for the main calendar.")
				.placeHolder("primary")
				.build());

		params.add(NodeParameter.builder()
				.name("additionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.nestedParameters(List.of(
						NodeParameter.builder().name("query").displayName("Search Query")
								.type(ParameterType.STRING)
								.description("Free text search terms to filter events.")
								.build(),
						NodeParameter.builder().name("singleEvents").displayName("Single Events")
								.type(ParameterType.BOOLEAN).defaultValue(true)
								.description("Expand recurring events into instances.")
								.build()
				)).build());

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

			String calendarId = context.getParameter("calendarId", "primary");
			String triggerOn = context.getParameter("triggerOn", "newEvents");
			Map<String, Object> additionalFields = context.getParameter("additionalFields", Map.of());

			String now = Instant.now().toString();
			String lastPollTime = (String) staticData.getOrDefault("lastPollTime",
					Instant.now().minusSeconds(300).toString());

			String eventsUrl = BASE_URL + "/calendars/" + encode(calendarId) + "/events";

			StringBuilder url = new StringBuilder(eventsUrl);
			url.append("?maxResults=250");

			if ("updatedEvents".equals(triggerOn)) {
				url.append("&updatedMin=").append(encode(lastPollTime));
			} else {
				url.append("&timeMin=").append(encode(lastPollTime));
			}

			if (additionalFields.get("query") != null) {
				url.append("&q=").append(encode((String) additionalFields.get("query")));
			}
			if (additionalFields.get("singleEvents") != null) {
				url.append("&singleEvents=").append(toBoolean(additionalFields.get("singleEvents"), true));
			} else {
				url.append("&singleEvents=true");
			}
			url.append("&orderBy=startTime");

			HttpResponse<String> response = get(url.toString(), headers);
			Map<String, Object> result = parseResponse(response);

			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
			newStaticData.put("lastPollTime", now);

			Object items = result.get("items");
			if (items instanceof List) {
				List<Map<String, Object>> eventItems = new ArrayList<>();
				for (Object item : (List<?>) items) {
					if (item instanceof Map) {
						Map<String, Object> eventMap = new LinkedHashMap<>((Map<String, Object>) item);
						eventMap.put("_triggerTimestamp", System.currentTimeMillis());
						eventItems.add(wrapInJson(eventMap));
					}
				}

				if (eventItems.isEmpty()) {
					return NodeExecutionResult.builder()
							.output(List.of(List.of()))
							.staticData(newStaticData)
							.build();
				}

				log.debug("Google Calendar trigger: found {} events", eventItems.size());
				return NodeExecutionResult.builder()
						.output(List.of(eventItems))
						.staticData(newStaticData)
						.build();
			}

			return NodeExecutionResult.builder()
					.output(List.of(List.of()))
					.staticData(newStaticData)
					.build();
		} catch (Exception e) {
			return handleError(context, "Google Calendar Trigger error: " + e.getMessage(), e);
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
