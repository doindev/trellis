package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Cisco Webex Trigger — starts the workflow when events occur in Webex
 * (new messages, room memberships, meetings, etc.) via webhook subscriptions.
 */
@Slf4j
@Node(
		type = "ciscoWebexTrigger",
		displayName = "Webex by Cisco Trigger",
		description = "Starts the workflow when a Webex event occurs",
		category = "Communication",
		icon = "ciscoWebex",
		trigger = true,
		credentials = {"ciscoWebexOAuth2Api"}
)
public class CiscoWebexTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://webexapis.com/v1";

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
						.type(ParameterType.OPTIONS).required(true).defaultValue("message")
						.options(List.of(
								ParameterOption.builder().name("New Message").value("message")
										.description("Trigger when a new message is posted").build(),
								ParameterOption.builder().name("Room Membership").value("membership")
										.description("Trigger on room membership changes").build(),
								ParameterOption.builder().name("Room Event").value("room")
										.description("Trigger on room created/updated").build(),
								ParameterOption.builder().name("Meeting Event").value("meeting")
										.description("Trigger on meeting started/ended").build(),
								ParameterOption.builder().name("Attachment Action").value("attachmentActions")
										.description("Trigger when an attachment action is submitted").build()
						)).build(),
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("created")
						.options(List.of(
								ParameterOption.builder().name("Created").value("created").build(),
								ParameterOption.builder().name("Updated").value("updated").build(),
								ParameterOption.builder().name("Deleted").value("deleted").build(),
								ParameterOption.builder().name("Started").value("started").build(),
								ParameterOption.builder().name("Ended").value("ended").build()
						)).build(),
				NodeParameter.builder()
						.name("roomId").displayName("Room ID (Filter)")
						.type(ParameterType.STRING).defaultValue("")
						.description("Only trigger for events in this room. Leave empty for all rooms.").build(),
				NodeParameter.builder()
						.name("webhookUrl").displayName("Webhook URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("The URL Webex will send events to (your workflow webhook URL).").build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessToken = context.getCredentialString("accessToken", "");
		String triggerResource = context.getParameter("triggerResource", "message");
		String event = context.getParameter("event", "created");
		String webhookUrl = context.getParameter("webhookUrl", "");
		String roomId = context.getParameter("roomId", "");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");

		try {
			// Build the resource string for the Webex webhook API
			String resource = switch (triggerResource) {
				case "message" -> "messages";
				case "membership" -> "memberships";
				case "room" -> "rooms";
				case "meeting" -> "meetings";
				case "attachmentActions" -> "attachmentActions";
				default -> "messages";
			};

			// Create or verify webhook subscription
			Map<String, Object> webhookBody = new LinkedHashMap<>();
			webhookBody.put("name", "Trellis Trigger - " + resource + ":" + event);
			webhookBody.put("targetUrl", webhookUrl);
			webhookBody.put("resource", resource);
			webhookBody.put("event", event);
			if (!roomId.isEmpty()) {
				webhookBody.put("filter", "roomId=" + roomId);
			}

			HttpResponse<String> response = post(BASE_URL + "/webhooks", webhookBody, headers);
			Map<String, Object> parsed = parseResponse(response);

			Map<String, Object> result = new LinkedHashMap<>(parsed);
			result.put("_triggerTimestamp", System.currentTimeMillis());

			log.debug("Cisco Webex trigger webhook registered for resource={}, event={}", resource, event);

			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			return handleError(context, "Cisco Webex Trigger error: " + e.getMessage(), e);
		}
	}
}
