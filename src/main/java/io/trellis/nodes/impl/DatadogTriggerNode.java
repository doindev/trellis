package io.trellis.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Node(
	type = "datadogTrigger",
	displayName = "Datadog Trigger",
	description = "Polls Datadog for new events or monitor state changes.",
	category = "Analytics",
	icon = "datadog",
	trigger = true,
	polling = true,
	credentials = {"datadogApi"}
)
public class DatadogTriggerNode extends AbstractApiNode {

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
		return List.of(
			NodeParameter.builder()
				.name("triggerResource").displayName("Trigger On")
				.type(ParameterType.OPTIONS).required(true).defaultValue("event")
				.options(List.of(
					ParameterOption.builder().name("New Events").value("event")
						.description("Trigger when new events are created").build(),
					ParameterOption.builder().name("Monitor State Changes").value("monitor")
						.description("Trigger when a monitor changes state").build()
				)).build(),

			// Event filters
			NodeParameter.builder()
				.name("eventSources").displayName("Event Sources")
				.type(ParameterType.STRING)
				.description("Comma-separated source names to filter events.")
				.placeHolder("nagios,chef")
				.displayOptions(Map.of("show", Map.of("triggerResource", List.of("event"))))
				.build(),

			NodeParameter.builder()
				.name("eventTags").displayName("Event Tags")
				.type(ParameterType.STRING)
				.description("Comma-separated tags to filter events.")
				.placeHolder("env:production")
				.displayOptions(Map.of("show", Map.of("triggerResource", List.of("event"))))
				.build(),

			// Monitor filters
			NodeParameter.builder()
				.name("monitorTags").displayName("Monitor Tags")
				.type(ParameterType.STRING)
				.description("Comma-separated tags to filter monitors.")
				.placeHolder("team:backend")
				.displayOptions(Map.of("show", Map.of("triggerResource", List.of("monitor"))))
				.build(),

			NodeParameter.builder()
				.name("monitorStatuses").displayName("Trigger on Statuses")
				.type(ParameterType.MULTI_OPTIONS)
				.description("Only trigger when a monitor transitions to one of these states.")
				.displayOptions(Map.of("show", Map.of("triggerResource", List.of("monitor"))))
				.options(List.of(
					ParameterOption.builder().name("Alert").value("Alert").build(),
					ParameterOption.builder().name("Warn").value("Warn").build(),
					ParameterOption.builder().name("No Data").value("No Data").build(),
					ParameterOption.builder().name("OK").value("OK").build()
				)).build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		Map<String, Object> staticData = context.getStaticData();
		if (staticData == null) staticData = new LinkedHashMap<>();

		String triggerResource = context.getParameter("triggerResource", "event");

		try {
			String baseUrl = getBaseApiUrl(credentials);
			Map<String, String> headers = getAuthHeaders(credentials);

			if ("event".equals(triggerResource)) {
				return pollEvents(context, baseUrl, headers, staticData);
			} else {
				return pollMonitors(context, baseUrl, headers, staticData);
			}
		} catch (Exception e) {
			return handleError(context, "Datadog Trigger error: " + e.getMessage(), e);
		}
	}

	// ========================= Event Polling =========================

	@SuppressWarnings("unchecked")
	private NodeExecutionResult pollEvents(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, Map<String, Object> staticData) throws Exception {

		long now = System.currentTimeMillis() / 1000;
		long lastPoll = staticData.containsKey("lastPollTimestamp")
			? ((Number) staticData.get("lastPollTimestamp")).longValue()
			: now - 300; // default: look back 5 minutes on first poll

		String url = baseUrl + "/api/v1/events?start=" + lastPoll + "&end=" + now;

		String sources = context.getParameter("eventSources", "");
		String tags = context.getParameter("eventTags", "");
		if (!sources.isEmpty()) url += "&sources=" + encode(sources);
		if (!tags.isEmpty()) url += "&tags=" + encode(tags);

		HttpResponse<String> response = get(url, headers);
		if (response.statusCode() >= 400) {
			String body = response.body() != null ? response.body() : "";
			if (body.length() > 300) body = body.substring(0, 300) + "...";
			return NodeExecutionResult.error("Datadog events poll failed (HTTP " + response.statusCode() + "): " + body);
		}

		Map<String, Object> parsed = parseResponse(response);
		Object eventsObj = parsed.get("events");

		// Update static data with new poll timestamp
		Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
		newStaticData.put("lastPollTimestamp", now);

		List<Map<String, Object>> items = new ArrayList<>();
		if (eventsObj instanceof List) {
			for (Object event : (List<?>) eventsObj) {
				if (event instanceof Map) {
					Map<String, Object> eventMap = new LinkedHashMap<>((Map<String, Object>) event);
					eventMap.put("_triggerTimestamp", System.currentTimeMillis());
					items.add(wrapInJson(eventMap));
				}
			}
		}

		if (items.isEmpty()) {
			return NodeExecutionResult.builder()
				.output(List.of(List.of()))
				.staticData(newStaticData)
				.build();
		}

		log.debug("Datadog trigger: found {} new events", items.size());
		return NodeExecutionResult.builder()
			.output(List.of(items))
			.staticData(newStaticData)
			.build();
	}

	// ========================= Monitor Polling =========================

	@SuppressWarnings("unchecked")
	private NodeExecutionResult pollMonitors(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, Map<String, Object> staticData) throws Exception {

		String url = baseUrl + "/api/v1/monitor";
		String monitorTags = context.getParameter("monitorTags", "");
		if (!monitorTags.isEmpty()) url += "?monitor_tags=" + encode(monitorTags);

		HttpResponse<String> response = get(url, headers);
		if (response.statusCode() >= 400) {
			String body = response.body() != null ? response.body() : "";
			if (body.length() > 300) body = body.substring(0, 300) + "...";
			return NodeExecutionResult.error("Datadog monitor poll failed (HTTP " + response.statusCode() + "): " + body);
		}

		List<Map<String, Object>> monitors = parseArrayResponse(response);

		// Get last known states from static data
		Map<String, String> lastStates = staticData.containsKey("monitorStates")
			? (Map<String, String>) staticData.get("monitorStates")
			: new LinkedHashMap<>();

		// Get status filter
		List<String> statusFilter = context.getParameter("monitorStatuses", List.of());

		Map<String, String> currentStates = new LinkedHashMap<>();
		List<Map<String, Object>> changedMonitors = new ArrayList<>();

		for (Map<String, Object> monitor : monitors) {
			String id = String.valueOf(monitor.getOrDefault("id", ""));
			String overallState = String.valueOf(monitor.getOrDefault("overall_state", ""));
			currentStates.put(id, overallState);

			String previousState = lastStates.get(id);
			if (previousState != null && !previousState.equals(overallState)) {
				// State changed
				if (statusFilter.isEmpty() || statusFilter.contains(overallState)) {
					Map<String, Object> item = new LinkedHashMap<>(monitor);
					item.put("_previousState", previousState);
					item.put("_triggerTimestamp", System.currentTimeMillis());
					changedMonitors.add(wrapInJson(item));
				}
			}
		}

		// Update static data
		Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
		newStaticData.put("monitorStates", currentStates);

		if (changedMonitors.isEmpty()) {
			return NodeExecutionResult.builder()
				.output(List.of(List.of()))
				.staticData(newStaticData)
				.build();
		}

		log.debug("Datadog trigger: {} monitors changed state", changedMonitors.size());
		return NodeExecutionResult.builder()
			.output(List.of(changedMonitors))
			.staticData(newStaticData)
			.build();
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("DD-API-KEY", String.valueOf(credentials.getOrDefault("apiKey", "")));
		headers.put("DD-APPLICATION-KEY", String.valueOf(credentials.getOrDefault("applicationKey", "")));
		headers.put("Content-Type", "application/json");
		return headers;
	}

	private String getBaseApiUrl(Map<String, Object> credentials) {
		String site = String.valueOf(credentials.getOrDefault("site", "datadoghq.com"));
		return "https://api." + site;
	}
}
