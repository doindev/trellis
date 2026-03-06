package io.cwc.nodes.impl;

import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Slack Trigger — polls Slack for new messages in a channel using
 * the conversations.history API endpoint. Stores the last seen
 * message timestamp in staticData to avoid duplicate processing.
 */
@Slf4j
@Node(
		type = "slackTrigger",
		displayName = "Slack Trigger",
		description = "Polls Slack for new messages in a channel",
		category = "Communication / Chat & Messaging",
		icon = "slack",
		credentials = {"slackApi"},
		trigger = true,
		polling = true
)
public class SlackTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://slack.com/api";

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
						.name("channelId").displayName("Channel ID")
						.type(ParameterType.STRING).required(true).defaultValue("")
						.description("The ID of the Slack channel to poll for new messages.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Maximum number of messages to return per poll.").build()
		);
	}

	@SuppressWarnings("unchecked")
	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> staticData = context.getStaticData();
		if (staticData == null) staticData = new LinkedHashMap<>();

		String token = context.getCredentialString("accessToken", "");
		String channelId = context.getParameter("channelId", "");
		int limit = toInt(context.getParameters().get("limit"), 100);

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + token);
		headers.put("Content-Type", "application/json");

		try {
			// Get the last message timestamp from static data
			String lastTs = staticData.containsKey("lastMessageTs")
					? String.valueOf(staticData.get("lastMessageTs"))
					: "";

			String url = BASE_URL + "/conversations.history?channel=" + encode(channelId) + "&limit=" + limit;
			if (!lastTs.isEmpty()) {
				url += "&oldest=" + encode(lastTs);
			}

			HttpResponse<String> response = get(url, headers);
			if (response.statusCode() >= 400) {
				String body = response.body() != null ? response.body() : "";
				if (body.length() > 300) body = body.substring(0, 300) + "...";
				return NodeExecutionResult.error("Slack poll failed (HTTP " + response.statusCode() + "): " + body);
			}

			Map<String, Object> parsed = parseResponse(response);
			Object messagesObj = parsed.get("messages");

			List<Map<String, Object>> items = new ArrayList<>();
			String newestTs = lastTs;

			if (messagesObj instanceof List) {
				List<?> messages = (List<?>) messagesObj;
				for (Object msg : messages) {
					if (msg instanceof Map) {
						Map<String, Object> msgMap = (Map<String, Object>) msg;
						String msgTs = String.valueOf(msgMap.getOrDefault("ts", ""));

						// Skip the exact message we already saw (oldest is exclusive, but be safe)
						if (!msgTs.isEmpty() && !msgTs.equals(lastTs)) {
							Map<String, Object> item = new LinkedHashMap<>(msgMap);
							item.put("_triggerTimestamp", System.currentTimeMillis());
							items.add(wrapInJson(item));

							// Track newest timestamp
							if (newestTs.isEmpty() || msgTs.compareTo(newestTs) > 0) {
								newestTs = msgTs;
							}
						}
					}
				}
			}

			// Update static data with the newest timestamp
			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
			if (!newestTs.isEmpty()) {
				newStaticData.put("lastMessageTs", newestTs);
			}

			if (items.isEmpty()) {
				return NodeExecutionResult.builder()
						.output(List.of(List.of()))
						.staticData(newStaticData)
						.build();
			}

			log.debug("Slack trigger: found {} new messages in channel {}", items.size(), channelId);
			return NodeExecutionResult.builder()
					.output(List.of(items))
					.staticData(newStaticData)
					.build();

		} catch (Exception e) {
			return handleError(context, "Slack Trigger error: " + e.getMessage(), e);
		}
	}
}
