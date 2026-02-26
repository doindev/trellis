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
	type = "clockifyTrigger",
	displayName = "Clockify Trigger",
	description = "Triggers when Clockify events occur such as new time entries, projects, or tasks.",
	category = "Miscellaneous",
	icon = "clockify",
	trigger = true,
	credentials = {"clockifyApi"}
)
public class ClockifyTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.clockify.me/api/v1";

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
				.name("workspaceId").displayName("Workspace ID")
				.type(ParameterType.STRING).required(true)
				.description("The ID of the workspace to monitor.")
				.build(),

			NodeParameter.builder()
				.name("triggerResource").displayName("Trigger On")
				.type(ParameterType.OPTIONS).required(true).defaultValue("timeEntry")
				.options(List.of(
					ParameterOption.builder().name("New Time Entry").value("timeEntry")
						.description("Trigger when a new time entry is created").build(),
					ParameterOption.builder().name("Timer Started").value("timerStarted")
						.description("Trigger when a timer is started").build(),
					ParameterOption.builder().name("Timer Stopped").value("timerStopped")
						.description("Trigger when a timer is stopped").build()
				)).build(),

			NodeParameter.builder()
				.name("userId").displayName("User ID")
				.type(ParameterType.STRING)
				.description("Filter time entries by user. Leave empty for the authenticated user.")
				.build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		Map<String, Object> staticData = context.getStaticData();
		if (staticData == null) staticData = new LinkedHashMap<>();

		String workspaceId = context.getParameter("workspaceId", "");
		String triggerResource = context.getParameter("triggerResource", "timeEntry");

		try {
			Map<String, String> headers = getAuthHeaders(credentials);

			// Get user ID
			String userId = context.getParameter("userId", "");
			if (userId.isEmpty()) {
				HttpResponse<String> userResponse = get(BASE_URL + "/user", headers);
				Map<String, Object> user = parseResponse(userResponse);
				userId = String.valueOf(user.get("id"));
			}

			String url = BASE_URL + "/workspaces/" + encode(workspaceId) + "/user/" + encode(userId) + "/time-entries";
			HttpResponse<String> response = get(url, headers);

			if (response.statusCode() >= 400) {
				String body = response.body() != null ? response.body() : "";
				if (body.length() > 300) body = body.substring(0, 300) + "...";
				return NodeExecutionResult.error("Clockify trigger poll failed (HTTP " + response.statusCode() + "): " + body);
			}

			List<Map<String, Object>> entries = parseArrayResponse(response);

			String lastSeenId = (String) staticData.getOrDefault("lastSeenId", "");
			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);

			List<Map<String, Object>> newItems = new ArrayList<>();

			for (Map<String, Object> entry : entries) {
				String entryId = String.valueOf(entry.getOrDefault("id", ""));

				if (entryId.equals(lastSeenId)) {
					break;
				}

				// Filter based on trigger type
				boolean include = switch (triggerResource) {
					case "timerStarted" -> entry.get("timeInterval") != null &&
						((Map<String, Object>) entry.get("timeInterval")).get("end") == null;
					case "timerStopped" -> entry.get("timeInterval") != null &&
						((Map<String, Object>) entry.get("timeInterval")).get("end") != null;
					default -> true;
				};

				if (include) {
					Map<String, Object> item = new LinkedHashMap<>(entry);
					item.put("_triggerTimestamp", System.currentTimeMillis());
					newItems.add(wrapInJson(item));
				}
			}

			// Update last seen ID
			if (!entries.isEmpty()) {
				newStaticData.put("lastSeenId", String.valueOf(entries.get(0).getOrDefault("id", "")));
			}

			if (newItems.isEmpty()) {
				return NodeExecutionResult.builder()
					.output(List.of(List.of()))
					.staticData(newStaticData)
					.build();
			}

			log.debug("Clockify trigger: found {} new items", newItems.size());
			return NodeExecutionResult.builder()
				.output(List.of(newItems))
				.staticData(newStaticData)
				.build();

		} catch (Exception e) {
			return handleError(context, "Clockify Trigger error: " + e.getMessage(), e);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("X-Api-Key", String.valueOf(credentials.getOrDefault("apiKey", "")));
		headers.put("Content-Type", "application/json");
		return headers;
	}
}
