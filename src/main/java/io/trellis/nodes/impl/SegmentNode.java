package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Segment — send analytics data to Segment using the HTTP Tracking API v1.
 * Supports identify, track, group, and page operations.
 */
@Node(
		type = "segment",
		displayName = "Segment",
		description = "Send analytics data to Segment",
		category = "Marketing",
		icon = "segment",
		credentials = {"segmentApi"},
		searchOnly = true
)
public class SegmentNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.segment.io/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String writeKey = context.getCredentialString("writeKey", "");
		String operation = context.getParameter("operation", "track");

		// Segment uses Basic auth with write key as username and empty password
		String credentials = Base64.getEncoder().encodeToString((writeKey + ":").getBytes());

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Basic " + credentials);
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "identify" -> handleIdentify(context, headers);
					case "track" -> handleTrack(context, headers);
					case "group" -> handleGroup(context, headers);
					case "page" -> handlePage(context, headers);
					default -> throw new IllegalArgumentException("Unknown operation: " + operation);
				};
				results.add(wrapInJson(result));
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					return handleError(context, e.getMessage(), e);
				}
				throw new RuntimeException(e);
			}
		}

		return NodeExecutionResult.success(results);
	}

	// ========================= Identify =========================

	private Map<String, Object> handleIdentify(NodeExecutionContext context,
			Map<String, String> headers) throws Exception {
		String userId = context.getParameter("userId", "");
		String anonymousId = context.getParameter("anonymousId", "");
		String traitsJson = context.getParameter("traits", "{}");
		String contextJson = context.getParameter("contextData", "{}");

		Map<String, Object> body = new LinkedHashMap<>();
		if (!userId.isEmpty()) body.put("userId", userId);
		if (!anonymousId.isEmpty()) body.put("anonymousId", anonymousId);

		Map<String, Object> traits = parseJson(traitsJson);
		if (!traits.isEmpty()) body.put("traits", traits);

		Map<String, Object> contextData = parseJson(contextJson);
		if (!contextData.isEmpty()) body.put("context", contextData);

		body.put("timestamp", java.time.Instant.now().toString());

		HttpResponse<String> response = post(BASE_URL + "/identify", body, headers);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
		result.put("statusCode", response.statusCode());
		result.put("operation", "identify");
		if (!userId.isEmpty()) result.put("userId", userId);
		return result;
	}

	// ========================= Track =========================

	private Map<String, Object> handleTrack(NodeExecutionContext context,
			Map<String, String> headers) throws Exception {
		String userId = context.getParameter("userId", "");
		String anonymousId = context.getParameter("anonymousId", "");
		String event = context.getParameter("event", "");
		String propertiesJson = context.getParameter("properties", "{}");
		String contextJson = context.getParameter("contextData", "{}");

		Map<String, Object> body = new LinkedHashMap<>();
		if (!userId.isEmpty()) body.put("userId", userId);
		if (!anonymousId.isEmpty()) body.put("anonymousId", anonymousId);
		body.put("event", event);

		Map<String, Object> properties = parseJson(propertiesJson);
		if (!properties.isEmpty()) body.put("properties", properties);

		Map<String, Object> contextData = parseJson(contextJson);
		if (!contextData.isEmpty()) body.put("context", contextData);

		body.put("timestamp", java.time.Instant.now().toString());

		HttpResponse<String> response = post(BASE_URL + "/track", body, headers);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
		result.put("statusCode", response.statusCode());
		result.put("operation", "track");
		result.put("event", event);
		return result;
	}

	// ========================= Group =========================

	private Map<String, Object> handleGroup(NodeExecutionContext context,
			Map<String, String> headers) throws Exception {
		String userId = context.getParameter("userId", "");
		String anonymousId = context.getParameter("anonymousId", "");
		String groupId = context.getParameter("groupId", "");
		String traitsJson = context.getParameter("traits", "{}");
		String contextJson = context.getParameter("contextData", "{}");

		Map<String, Object> body = new LinkedHashMap<>();
		if (!userId.isEmpty()) body.put("userId", userId);
		if (!anonymousId.isEmpty()) body.put("anonymousId", anonymousId);
		body.put("groupId", groupId);

		Map<String, Object> traits = parseJson(traitsJson);
		if (!traits.isEmpty()) body.put("traits", traits);

		Map<String, Object> contextData = parseJson(contextJson);
		if (!contextData.isEmpty()) body.put("context", contextData);

		body.put("timestamp", java.time.Instant.now().toString());

		HttpResponse<String> response = post(BASE_URL + "/group", body, headers);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
		result.put("statusCode", response.statusCode());
		result.put("operation", "group");
		result.put("groupId", groupId);
		return result;
	}

	// ========================= Page =========================

	private Map<String, Object> handlePage(NodeExecutionContext context,
			Map<String, String> headers) throws Exception {
		String userId = context.getParameter("userId", "");
		String anonymousId = context.getParameter("anonymousId", "");
		String name = context.getParameter("name", "");
		String pageCategory = context.getParameter("pageCategory", "");
		String propertiesJson = context.getParameter("properties", "{}");
		String contextJson = context.getParameter("contextData", "{}");

		Map<String, Object> body = new LinkedHashMap<>();
		if (!userId.isEmpty()) body.put("userId", userId);
		if (!anonymousId.isEmpty()) body.put("anonymousId", anonymousId);
		if (!name.isEmpty()) body.put("name", name);
		if (!pageCategory.isEmpty()) body.put("category", pageCategory);

		Map<String, Object> properties = parseJson(propertiesJson);
		if (!properties.isEmpty()) body.put("properties", properties);

		Map<String, Object> contextData = parseJson(contextJson);
		if (!contextData.isEmpty()) body.put("context", contextData);

		body.put("timestamp", java.time.Instant.now().toString());

		HttpResponse<String> response = post(BASE_URL + "/page", body, headers);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
		result.put("statusCode", response.statusCode());
		result.put("operation", "page");
		if (!name.isEmpty()) result.put("name", name);
		return result;
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("track")
						.options(List.of(
								ParameterOption.builder().name("Identify").value("identify")
										.description("Associate a user with their traits").build(),
								ParameterOption.builder().name("Track").value("track")
										.description("Record an event a user has performed").build(),
								ParameterOption.builder().name("Group").value("group")
										.description("Associate a user with a group/organization").build(),
								ParameterOption.builder().name("Page").value("page")
										.description("Record a page view").build()
						)).build(),
				NodeParameter.builder()
						.name("userId").displayName("User ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The unique identifier of the user.").build(),
				NodeParameter.builder()
						.name("anonymousId").displayName("Anonymous ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("A pseudo-unique substitute for a User ID when one is not available.").build(),
				NodeParameter.builder()
						.name("event").displayName("Event Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The name of the event being tracked.")
						.displayOptions(Map.of("show", Map.of("operation", List.of("track")))).build(),
				NodeParameter.builder()
						.name("groupId").displayName("Group ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The unique ID of the group/organization.")
						.displayOptions(Map.of("show", Map.of("operation", List.of("group")))).build(),
				NodeParameter.builder()
						.name("name").displayName("Page Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The name of the page.")
						.displayOptions(Map.of("show", Map.of("operation", List.of("page")))).build(),
				NodeParameter.builder()
						.name("pageCategory").displayName("Page Category")
						.type(ParameterType.STRING).defaultValue("")
						.description("The category of the page.")
						.displayOptions(Map.of("show", Map.of("operation", List.of("page")))).build(),
				NodeParameter.builder()
						.name("traits").displayName("Traits (JSON)")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("JSON object of user/group traits (e.g., name, email, plan).")
						.displayOptions(Map.of("show", Map.of("operation", List.of("identify", "group")))).build(),
				NodeParameter.builder()
						.name("properties").displayName("Properties (JSON)")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("JSON object of event/page properties.")
						.displayOptions(Map.of("show", Map.of("operation", List.of("track", "page")))).build(),
				NodeParameter.builder()
						.name("contextData").displayName("Context (JSON)")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("JSON object with additional context (e.g., IP, userAgent, locale).").build()
		);
	}
}
