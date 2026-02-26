package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Rocket.Chat — post messages to channels in a Rocket.Chat server.
 */
@Node(
		type = "rocketchat",
		displayName = "Rocket.Chat",
		description = "Post messages to Rocket.Chat",
		category = "Communication",
		icon = "rocketchat",
		credentials = {"rocketchatApi"}
)
public class RocketChatNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String domain = context.getCredentialString("domain", "");
		String userId = context.getCredentialString("userId", "");
		String authToken = context.getCredentialString("authToken", "");
		String resource = context.getParameter("resource", "chat");
		String operation = context.getParameter("operation", "postMessage");

		// Strip trailing slash
		if (domain.endsWith("/")) {
			domain = domain.substring(0, domain.length() - 1);
		}
		String baseUrl = domain + "/api/v1";

		Map<String, String> headers = new HashMap<>();
		headers.put("X-Auth-Token", authToken);
		headers.put("X-User-Id", userId);
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "chat" -> handleChat(context, operation, baseUrl, headers);
					default -> throw new IllegalArgumentException("Unknown resource: " + resource);
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

	// ========================= Chat =========================

	private Map<String, Object> handleChat(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		if ("postMessage".equals(operation)) {
			String channel = context.getParameter("channel", "");
			String text = context.getParameter("text", "");
			String alias = context.getParameter("alias", "");
			String emoji = context.getParameter("emoji", "");
			String avatar = context.getParameter("avatar", "");
			String attachmentsJson = context.getParameter("attachments", "");

			Map<String, Object> body = new LinkedHashMap<>();
			body.put("channel", channel);
			body.put("text", text);
			if (!alias.isBlank()) body.put("alias", alias);
			if (!emoji.isBlank()) body.put("emoji", emoji);
			if (!avatar.isBlank()) body.put("avatar", avatar);

			// Parse attachments if provided
			if (!attachmentsJson.isBlank()) {
				try {
					List<Map<String, Object>> attachments = parseJsonArray(attachmentsJson);
					if (!attachments.isEmpty()) {
						body.put("attachments", attachments);
					}
				} catch (Exception ignored) {
					// If parsing fails, send without attachments
				}
			}

			HttpResponse<String> response = post(baseUrl + "/chat.postMessage", body, headers);
			return parseResponse(response);
		}
		throw new IllegalArgumentException("Unknown chat operation: " + operation);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS)
						.defaultValue("chat")
						.options(List.of(
								ParameterOption.builder().name("Chat").value("chat").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("postMessage")
						.options(List.of(
								ParameterOption.builder().name("Post Message").value("postMessage").build()
						)).build(),
				NodeParameter.builder()
						.name("channel").displayName("Channel")
						.type(ParameterType.STRING).defaultValue("")
						.description("The channel to send the message to (e.g. #general or @username).").build(),
				NodeParameter.builder()
						.name("text").displayName("Text")
						.type(ParameterType.STRING).defaultValue("")
						.description("The message text.").build(),
				NodeParameter.builder()
						.name("alias").displayName("Alias")
						.type(ParameterType.STRING).defaultValue("")
						.description("Custom alias for the message sender.").build(),
				NodeParameter.builder()
						.name("emoji").displayName("Emoji")
						.type(ParameterType.STRING).defaultValue("")
						.description("Emoji to use as the avatar (e.g. :ghost:).").build(),
				NodeParameter.builder()
						.name("avatar").displayName("Avatar URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("URL of a custom avatar image.").build(),
				NodeParameter.builder()
						.name("attachments").displayName("Attachments")
						.type(ParameterType.JSON).defaultValue("")
						.description("JSON array of attachment objects.").build()
		);
	}
}
