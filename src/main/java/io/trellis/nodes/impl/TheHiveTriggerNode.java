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

/**
 * TheHive Trigger Node -- polls for new alerts or cases
 * via TheHive v4 REST API.
 */
@Slf4j
@Node(
	type = "theHiveTrigger",
	displayName = "TheHive Trigger",
	description = "Polls TheHive for new alerts or cases.",
	category = "Miscellaneous",
	icon = "theHive",
	trigger = true,
	polling = true,
	credentials = {"theHiveApi"}
)
public class TheHiveTriggerNode extends AbstractApiNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of();
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
				.type(ParameterType.OPTIONS).required(true).defaultValue("alert")
				.options(List.of(
					ParameterOption.builder().name("New Alerts").value("alert")
						.description("Trigger when new alerts are created").build(),
					ParameterOption.builder().name("New Cases").value("case")
						.description("Trigger when new cases are created").build()
				)).build(),

			NodeParameter.builder()
				.name("limit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(50)
				.description("Maximum number of items to return per poll.").build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		Map<String, Object> staticData = context.getStaticData();
		if (staticData == null) staticData = new LinkedHashMap<>();

		String triggerResource = context.getParameter("triggerResource", "alert");
		int limit = toInt(context.getParameter("limit", 50), 50);

		try {
			String baseUrl = getBaseUrl(credentials);
			Map<String, String> headers = getAuthHeaders(credentials);

			long now = System.currentTimeMillis();
			long lastPoll = staticData.containsKey("lastPollTimestamp")
				? ((Number) staticData.get("lastPollTimestamp")).longValue()
				: now - 300000;

			String endpoint = "alert".equals(triggerResource) ? "/api/alert" : "/api/case";
			String url = buildUrl(baseUrl + endpoint, Map.of("range", "0-" + limit));

			HttpResponse<String> response = get(url, headers);

			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
			newStaticData.put("lastPollTimestamp", now);

			if (response.statusCode() >= 400) {
				String body = response.body() != null ? response.body() : "";
				if (body.length() > 300) body = body.substring(0, 300) + "...";
				return NodeExecutionResult.error("TheHive Trigger error (HTTP " + response.statusCode() + "): " + body);
			}

			List<Map<String, Object>> items = new ArrayList<>();
			String body = response.body();
			if (body != null && body.trim().startsWith("[")) {
				for (Map<String, Object> item : parseArrayResponse(response)) {
					Object createdAt = item.get("createdAt");
					long itemTime = 0;
					if (createdAt instanceof Number) {
						itemTime = ((Number) createdAt).longValue();
					}
					if (itemTime >= lastPoll) {
						Map<String, Object> enriched = new LinkedHashMap<>(item);
						enriched.put("_triggerTimestamp", System.currentTimeMillis());
						items.add(wrapInJson(enriched));
					}
				}
			}

			if (items.isEmpty()) {
				return NodeExecutionResult.builder()
					.output(List.of(List.of()))
					.staticData(newStaticData)
					.build();
			}

			log.debug("TheHive Trigger: found {} new {}", items.size(), triggerResource);
			return NodeExecutionResult.builder()
				.output(List.of(items))
				.staticData(newStaticData)
				.build();

		} catch (Exception e) {
			return handleError(context, "TheHive Trigger error: " + e.getMessage(), e);
		}
	}

	// ========================= Helpers =========================

	@Override
	protected String getBaseUrl(Map<String, Object> credentials) {
		String url = String.valueOf(credentials.getOrDefault("url", "http://localhost:9000"));
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Authorization", "Bearer " + credentials.getOrDefault("apiKey", ""));
		return headers;
	}
}
