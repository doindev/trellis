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
 * Gmail Trigger — polls Gmail for new emails matching specified
 * criteria (labels, search query). Stores the last checked message
 * ID in staticData to avoid processing duplicate messages.
 */
@Slf4j
@Node(
		type = "gmailTrigger",
		displayName = "Gmail Trigger",
		description = "Polls Gmail for new emails",
		category = "Communication / Email",
		icon = "gmail",
		credentials = {"gmailOAuth2Api"},
		trigger = true,
		polling = true
)
public class GmailTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://gmail.googleapis.com/gmail/v1/users/me";

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
						.name("label").displayName("Label")
						.type(ParameterType.OPTIONS).defaultValue("INBOX")
						.description("The Gmail label to poll for new messages.")
						.options(List.of(
								ParameterOption.builder().name("Inbox").value("INBOX").build(),
								ParameterOption.builder().name("Sent").value("SENT").build(),
								ParameterOption.builder().name("Starred").value("STARRED").build(),
								ParameterOption.builder().name("Important").value("IMPORTANT").build(),
								ParameterOption.builder().name("Unread").value("UNREAD").build(),
								ParameterOption.builder().name("Drafts").value("DRAFT").build(),
								ParameterOption.builder().name("Spam").value("SPAM").build(),
								ParameterOption.builder().name("Trash").value("TRASH").build()
						)).build(),
				NodeParameter.builder()
						.name("query").displayName("Query")
						.type(ParameterType.STRING).defaultValue("")
						.description("Gmail search query to filter messages (same format as Gmail search box).").build(),
				NodeParameter.builder()
						.name("fetchMessageDetails").displayName("Fetch Message Details")
						.type(ParameterType.BOOLEAN).defaultValue(true)
						.description("Whether to fetch the full message details for each new message.").build(),
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
		String label = context.getParameter("label", "INBOX");
		String query = context.getParameter("query", "");
		boolean fetchDetails = toBoolean(context.getParameters().get("fetchMessageDetails"), true);
		int limit = toInt(context.getParameters().get("limit"), 50);

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + token);
		headers.put("Content-Type", "application/json");

		try {
			// Build the messages.list URL
			String url = BASE_URL + "/messages?maxResults=" + limit + "&labelIds=" + encode(label);
			if (!query.isBlank()) {
				url += "&q=" + encode(query);
			}

			HttpResponse<String> response = get(url, headers);
			if (response.statusCode() >= 400) {
				String body = response.body() != null ? response.body() : "";
				if (body.length() > 300) body = body.substring(0, 300) + "...";
				return NodeExecutionResult.error("Gmail poll failed (HTTP " + response.statusCode() + "): " + body);
			}

			Map<String, Object> parsed = parseResponse(response);
			Object messagesObj = parsed.get("messages");

			// Get last known message ID from static data
			String lastMessageId = staticData.containsKey("lastMessageId")
					? String.valueOf(staticData.get("lastMessageId"))
					: "";

			List<Map<String, Object>> items = new ArrayList<>();
			String newestMessageId = "";

			if (messagesObj instanceof List) {
				List<?> messages = (List<?>) messagesObj;

				for (Object msg : messages) {
					if (msg instanceof Map) {
						Map<String, Object> msgMap = (Map<String, Object>) msg;
						String msgId = String.valueOf(msgMap.getOrDefault("id", ""));

						// The first message in the list is the newest
						if (newestMessageId.isEmpty()) {
							newestMessageId = msgId;
						}

						// Stop when we hit the last seen message
						if (msgId.equals(lastMessageId)) {
							break;
						}

						if (fetchDetails) {
							// Fetch the full message details
							HttpResponse<String> detailResponse = get(
									BASE_URL + "/messages/" + encode(msgId) + "?format=full", headers);
							if (detailResponse.statusCode() < 400) {
								Map<String, Object> detail = parseResponse(detailResponse);
								detail.put("_triggerTimestamp", System.currentTimeMillis());
								items.add(wrapInJson(detail));
							}
						} else {
							Map<String, Object> item = new LinkedHashMap<>(msgMap);
							item.put("_triggerTimestamp", System.currentTimeMillis());
							items.add(wrapInJson(item));
						}
					}
				}
			}

			// Update static data with the newest message ID
			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
			if (!newestMessageId.isEmpty()) {
				newStaticData.put("lastMessageId", newestMessageId);
			}

			if (items.isEmpty()) {
				return NodeExecutionResult.builder()
						.output(List.of(List.of()))
						.staticData(newStaticData)
						.build();
			}

			log.debug("Gmail trigger: found {} new messages", items.size());
			return NodeExecutionResult.builder()
					.output(List.of(items))
					.staticData(newStaticData)
					.build();

		} catch (Exception e) {
			return handleError(context, "Gmail Trigger error: " + e.getMessage(), e);
		}
	}
}
