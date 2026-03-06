package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Notion Trigger Node -- polls Notion databases for new or updated pages.
 * Uses the Notion API to detect changes since the last poll.
 */
@Slf4j
@Node(
	type = "notionTrigger",
	displayName = "Notion Trigger",
	description = "Starts the workflow when pages are created or updated in a Notion database",
	category = "Project Management",
	icon = "notion",
	trigger = true,
	polling = true,
	credentials = {"notionApi"}
)
public class NotionTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.notion.com/v1";

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
				.name("event").displayName("Event")
				.type(ParameterType.OPTIONS).required(true).defaultValue("pageAddedToDatabase")
				.options(List.of(
					ParameterOption.builder().name("Page Added to Database").value("pageAddedToDatabase")
						.description("Trigger when a page is added to a database").build(),
					ParameterOption.builder().name("Page Updated in Database").value("pageUpdatedInDatabase")
						.description("Trigger when a page is updated in a database").build()
				)).build(),

			NodeParameter.builder()
				.name("databaseId").displayName("Database ID")
				.type(ParameterType.STRING).required(true)
				.description("The ID of the Notion database to poll.")
				.build(),

			NodeParameter.builder()
				.name("simple").displayName("Simplify Output")
				.type(ParameterType.BOOLEAN).defaultValue(true)
				.description("Whether to simplify the output to only include the page properties.")
				.build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		Map<String, Object> staticData = context.getStaticData();
		if (staticData == null) staticData = new LinkedHashMap<>();

		String event = context.getParameter("event", "pageAddedToDatabase");
		String databaseId = context.getParameter("databaseId", "");

		try {
			Map<String, String> headers = getAuthHeaders(credentials);

			// Build sort based on event type
			String sortProperty = "pageAddedToDatabase".equals(event) ? "created_time" : "last_edited_time";
			String lastTimestamp = staticData.containsKey("lastTimestamp")
				? String.valueOf(staticData.get("lastTimestamp"))
				: null;

			// Build query body
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("sorts", List.of(Map.of("timestamp", sortProperty, "direction", "descending")));
			body.put("page_size", 100);

			if (lastTimestamp != null) {
				body.put("filter", Map.of(
					"timestamp", sortProperty,
					"after", lastTimestamp,
					sortProperty, Map.of("after", lastTimestamp)
				));
			}

			HttpResponse<String> response = post(BASE_URL + "/databases/" + encode(databaseId) + "/query", body, headers);

			if (response.statusCode() >= 400) {
				String respBody = response.body() != null ? response.body() : "";
				if (respBody.length() > 300) respBody = respBody.substring(0, 300) + "...";
				return NodeExecutionResult.error("Notion API error (HTTP " + response.statusCode() + "): " + respBody);
			}

			Map<String, Object> parsed = parseResponse(response);
			Object resultsObj = parsed.get("results");

			// Update static data
			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
			String now = java.time.Instant.now().toString();
			newStaticData.put("lastTimestamp", now);

			List<Map<String, Object>> items = new ArrayList<>();
			if (resultsObj instanceof List) {
				for (Object page : (List<?>) resultsObj) {
					if (page instanceof Map) {
						Map<String, Object> pageMap = (Map<String, Object>) page;
						// Check if the page is newer than the last poll
						if (lastTimestamp == null || isNewer(pageMap, sortProperty, lastTimestamp)) {
							Map<String, Object> enriched = new LinkedHashMap<>(pageMap);
							enriched.put("_triggerEvent", event);
							enriched.put("_triggerTimestamp", System.currentTimeMillis());
							items.add(wrapInJson(enriched));
						}
					}
				}
			}

			if (items.isEmpty()) {
				return NodeExecutionResult.builder()
					.output(List.of(List.of()))
					.staticData(newStaticData)
					.build();
			}

			log.debug("Notion trigger: found {} new/updated pages", items.size());
			return NodeExecutionResult.builder()
				.output(List.of(items))
				.staticData(newStaticData)
				.build();

		} catch (Exception e) {
			return handleError(context, "Notion Trigger error: " + e.getMessage(), e);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		String apiKey = String.valueOf(credentials.getOrDefault("apiKey", credentials.getOrDefault("accessToken", "")));
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + apiKey);
		headers.put("Content-Type", "application/json");
		headers.put("Notion-Version", "2022-06-28");
		return headers;
	}

	private boolean isNewer(Map<String, Object> pageMap, String sortProperty, String lastTimestamp) {
		Object timestamp = pageMap.get(sortProperty);
		if (timestamp == null) return true;
		return String.valueOf(timestamp).compareTo(lastTimestamp) > 0;
	}
}
