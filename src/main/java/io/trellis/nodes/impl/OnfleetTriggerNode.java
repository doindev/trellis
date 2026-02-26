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
 * Onfleet Trigger Node -- polls for new or completed tasks
 * via the Onfleet API.
 */
@Slf4j
@Node(
	type = "onfleetTrigger",
	displayName = "Onfleet Trigger",
	description = "Polls Onfleet for new or completed tasks.",
	category = "Miscellaneous",
	icon = "onfleet",
	trigger = true,
	polling = true,
	credentials = {"onfleetApi"}
)
public class OnfleetTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://onfleet.com/api/v2";

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
				.type(ParameterType.OPTIONS).required(true).defaultValue("newTask")
				.options(List.of(
					ParameterOption.builder().name("New Tasks").value("newTask")
						.description("Trigger when new tasks are created").build(),
					ParameterOption.builder().name("Completed Tasks").value("completedTask")
						.description("Trigger when tasks are completed").build()
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

		String triggerResource = context.getParameter("triggerResource", "newTask");

		try {
			Map<String, String> headers = getAuthHeaders(credentials);

			long now = System.currentTimeMillis();
			long lastPoll = staticData.containsKey("lastPollTimestamp")
				? ((Number) staticData.get("lastPollTimestamp")).longValue()
				: now - 300000;

			// Fetch tasks
			String url = BASE_URL + "/tasks";
			if ("completedTask".equals(triggerResource)) {
				url = buildUrl(url, Map.of("state", "3")); // State 3 = completed
			}

			HttpResponse<String> response = get(url, headers);

			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
			newStaticData.put("lastPollTimestamp", now);

			if (response.statusCode() >= 400) {
				String body = response.body() != null ? response.body() : "";
				if (body.length() > 300) body = body.substring(0, 300) + "...";
				return NodeExecutionResult.error("Onfleet Trigger error (HTTP " + response.statusCode() + "): " + body);
			}

			Set<String> seenIds = staticData.containsKey("seenIds")
				? new HashSet<>((List<String>) staticData.get("seenIds"))
				: new HashSet<>();

			List<Map<String, Object>> items = new ArrayList<>();
			Set<String> currentIds = new HashSet<>();
			String body = response.body();

			if (body != null && body.trim().startsWith("[")) {
				for (Map<String, Object> task : parseArrayResponse(response)) {
					String id = String.valueOf(task.getOrDefault("id", ""));
					currentIds.add(id);

					if (!seenIds.contains(id)) {
						Map<String, Object> enriched = new LinkedHashMap<>(task);
						enriched.put("_triggerTimestamp", System.currentTimeMillis());
						enriched.put("_triggerType", triggerResource);
						items.add(wrapInJson(enriched));
					}
				}
			}

			newStaticData.put("seenIds", new ArrayList<>(currentIds));

			if (items.isEmpty()) {
				return NodeExecutionResult.builder()
					.output(List.of(List.of()))
					.staticData(newStaticData)
					.build();
			}

			log.debug("Onfleet Trigger: found {} {} tasks", items.size(), triggerResource);
			return NodeExecutionResult.builder()
				.output(List.of(items))
				.staticData(newStaticData)
				.build();

		} catch (Exception e) {
			return handleError(context, "Onfleet Trigger error: " + e.getMessage(), e);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		String apiKey = String.valueOf(credentials.getOrDefault("apiKey", ""));
		String auth = Base64.getEncoder().encodeToString((apiKey + ":").getBytes());
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Authorization", "Basic " + auth);
		return headers;
	}
}
