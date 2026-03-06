package io.cwc.nodes.impl;

import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Microsoft Teams Trigger — polls Microsoft Teams for new messages
 * in a channel or chat using the Microsoft Graph API. Stores the last
 * seen message ID in staticData to avoid duplicate processing.
 */
@Slf4j
@Node(
		type = "microsoftTeamsTrigger",
		displayName = "Microsoft Teams Trigger",
		description = "Polls Microsoft Teams for new messages in a channel or chat",
		category = "Communication / Chat & Messaging",
		icon = "microsoftTeams",
		credentials = {"microsoftTeamsOAuth2Api"},
		trigger = true,
		polling = true
)
public class MicrosoftTeamsTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://graph.microsoft.com/v1.0";

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
						.name("triggerOn").displayName("Trigger On")
						.type(ParameterType.OPTIONS).required(true).defaultValue("channelMessage")
						.options(List.of(
								ParameterOption.builder().name("New Channel Message").value("channelMessage")
										.description("Trigger when a new message is posted to a channel").build(),
								ParameterOption.builder().name("New Chat Message").value("chatMessage")
										.description("Trigger when a new message is received in a chat").build()
						)).build(),
				NodeParameter.builder()
						.name("teamId").displayName("Team ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the Microsoft Teams team (required for channel messages).")
						.displayOptions(Map.of("show", Map.of("triggerOn", List.of("channelMessage"))))
						.build(),
				NodeParameter.builder()
						.name("channelId").displayName("Channel ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the channel to poll.")
						.displayOptions(Map.of("show", Map.of("triggerOn", List.of("channelMessage"))))
						.build(),
				NodeParameter.builder()
						.name("chatId").displayName("Chat ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the chat to poll.")
						.displayOptions(Map.of("show", Map.of("triggerOn", List.of("chatMessage"))))
						.build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(50)
						.description("Maximum number of messages to return per poll.").build()
		);
	}

	@SuppressWarnings("unchecked")
	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> staticData = context.getStaticData();
		if (staticData == null) staticData = new LinkedHashMap<>();

		String token = context.getCredentialString("accessToken", "");
		String triggerOn = context.getParameter("triggerOn", "channelMessage");
		int limit = toInt(context.getParameters().get("limit"), 50);

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + token);
		headers.put("Content-Type", "application/json");

		try {
			String url;
			if ("channelMessage".equals(triggerOn)) {
				String teamId = context.getParameter("teamId", "");
				String channelId = context.getParameter("channelId", "");
				url = BASE_URL + "/teams/" + encode(teamId) + "/channels/" + encode(channelId) + "/messages?$top=" + limit;
			} else {
				String chatId = context.getParameter("chatId", "");
				url = BASE_URL + "/chats/" + encode(chatId) + "/messages?$top=" + limit;
			}

			HttpResponse<String> response = get(url, headers);
			if (response.statusCode() >= 400) {
				String body = response.body() != null ? response.body() : "";
				if (body.length() > 300) body = body.substring(0, 300) + "...";
				return NodeExecutionResult.error("Teams poll failed (HTTP " + response.statusCode() + "): " + body);
			}

			Map<String, Object> parsed = parseResponse(response);
			Object valueObj = parsed.get("value");

			String lastMessageId = staticData.containsKey("lastMessageId")
					? String.valueOf(staticData.get("lastMessageId"))
					: "";

			List<Map<String, Object>> items = new ArrayList<>();
			String newestMessageId = lastMessageId;
			String newestCreatedDateTime = staticData.containsKey("lastCreatedDateTime")
					? String.valueOf(staticData.get("lastCreatedDateTime"))
					: "";

			if (valueObj instanceof List) {
				List<?> messages = (List<?>) valueObj;
				for (Object msg : messages) {
					if (msg instanceof Map) {
						Map<String, Object> msgMap = (Map<String, Object>) msg;
						String msgId = String.valueOf(msgMap.getOrDefault("id", ""));
						String createdDateTime = String.valueOf(msgMap.getOrDefault("createdDateTime", ""));

						// Skip messages we have already seen
						if (!msgId.isEmpty() && !msgId.equals(lastMessageId)) {
							// Only include messages newer than our last seen
							if (newestCreatedDateTime.isEmpty() || createdDateTime.compareTo(newestCreatedDateTime) > 0) {
								Map<String, Object> item = new LinkedHashMap<>(msgMap);
								item.put("_triggerTimestamp", System.currentTimeMillis());
								items.add(wrapInJson(item));
							}
						}

						// Track the newest message
						if (newestCreatedDateTime.isEmpty() || createdDateTime.compareTo(newestCreatedDateTime) > 0) {
							newestMessageId = msgId;
							newestCreatedDateTime = createdDateTime;
						}
					}
				}
			}

			// Update static data
			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
			if (!newestMessageId.isEmpty()) {
				newStaticData.put("lastMessageId", newestMessageId);
			}
			if (!newestCreatedDateTime.isEmpty()) {
				newStaticData.put("lastCreatedDateTime", newestCreatedDateTime);
			}

			if (items.isEmpty()) {
				return NodeExecutionResult.builder()
						.output(List.of(List.of()))
						.staticData(newStaticData)
						.build();
			}

			log.debug("Teams trigger: found {} new messages", items.size());
			return NodeExecutionResult.builder()
					.output(List.of(items))
					.staticData(newStaticData)
					.build();

		} catch (Exception e) {
			return handleError(context, "Microsoft Teams Trigger error: " + e.getMessage(), e);
		}
	}
}
