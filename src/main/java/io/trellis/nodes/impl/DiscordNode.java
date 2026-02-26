package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Discord — interact with the Discord API to manage channels,
 * messages and server members/roles.
 */
@Node(
		type = "discord",
		displayName = "Discord",
		description = "Interact with the Discord API",
		category = "Communication / Chat & Messaging",
		icon = "discord",
		credentials = {"discordBotApi"}
)
public class DiscordNode extends AbstractApiNode {

	private static final String BASE_URL = "https://discord.com/api/v10";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String botToken = context.getCredentialString("botToken", "");
		String resource = context.getParameter("resource", "message");
		String operation = context.getParameter("operation", "send");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bot " + botToken);
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "channel" -> handleChannel(context, operation, headers);
					case "message" -> handleMessage(context, operation, headers);
					case "member" -> handleMember(context, operation, headers);
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

	// ========================= Channel =========================

	private Map<String, Object> handleChannel(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "get" -> {
				String channelId = context.getParameter("channelId", "");
				HttpResponse<String> response = get(BASE_URL + "/channels/" + encode(channelId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				String guildId = context.getParameter("guildId", "");
				HttpResponse<String> response = get(BASE_URL + "/guilds/" + encode(guildId) + "/channels", headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("channels", parseArrayResponse(response));
				yield result;
			}
			default -> throw new IllegalArgumentException("Unknown channel operation: " + operation);
		};
	}

	// ========================= Message =========================

	private Map<String, Object> handleMessage(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String channelId = context.getParameter("channelId", "");

		return switch (operation) {
			case "send" -> {
				String content = context.getParameter("content", "");
				boolean tts = toBoolean(context.getParameters().get("tts"), false);
				String embedJson = context.getParameter("embeds", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("content", content);
				if (tts) body.put("tts", true);
				if (!embedJson.isBlank()) {
					body.put("embeds", parseJsonArray(embedJson));
				}

				HttpResponse<String> response = post(BASE_URL + "/channels/" + encode(channelId) + "/messages", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String messageId = context.getParameter("messageId", "");
				HttpResponse<String> response = get(BASE_URL + "/channels/" + encode(channelId) + "/messages/" + encode(messageId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 50);
				String before = context.getParameter("before", "");
				String after = context.getParameter("after", "");
				String around = context.getParameter("around", "");

				String url = BASE_URL + "/channels/" + encode(channelId) + "/messages?limit=" + limit;
				if (!before.isBlank()) url += "&before=" + encode(before);
				if (!after.isBlank()) url += "&after=" + encode(after);
				if (!around.isBlank()) url += "&around=" + encode(around);

				HttpResponse<String> response = get(url, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("messages", parseArrayResponse(response));
				yield result;
			}
			case "delete" -> {
				String messageId = context.getParameter("messageId", "");
				HttpResponse<String> response = delete(BASE_URL + "/channels/" + encode(channelId) + "/messages/" + encode(messageId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("messageId", messageId);
				yield result;
			}
			default -> throw new IllegalArgumentException("Unknown message operation: " + operation);
		};
	}

	// ========================= Member =========================

	private Map<String, Object> handleMember(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String guildId = context.getParameter("guildId", "");

		return switch (operation) {
			case "get" -> {
				String userId = context.getParameter("userId", "");
				HttpResponse<String> response = get(BASE_URL + "/guilds/" + encode(guildId) + "/members/" + encode(userId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				String afterMember = context.getParameter("afterMember", "");
				String url = BASE_URL + "/guilds/" + encode(guildId) + "/members?limit=" + limit;
				if (!afterMember.isBlank()) url += "&after=" + encode(afterMember);
				HttpResponse<String> response = get(url, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("members", parseArrayResponse(response));
				yield result;
			}
			case "roleAdd" -> {
				String userId = context.getParameter("userId", "");
				String roleId = context.getParameter("roleId", "");
				HttpResponse<String> response = put(BASE_URL + "/guilds/" + encode(guildId) + "/members/" + encode(userId) + "/roles/" + encode(roleId), "{}", headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("userId", userId);
				result.put("roleId", roleId);
				yield result;
			}
			case "roleRemove" -> {
				String userId = context.getParameter("userId", "");
				String roleId = context.getParameter("roleId", "");
				HttpResponse<String> response = delete(BASE_URL + "/guilds/" + encode(guildId) + "/members/" + encode(userId) + "/roles/" + encode(roleId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("userId", userId);
				result.put("roleId", roleId);
				yield result;
			}
			default -> throw new IllegalArgumentException("Unknown member operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS)
						.defaultValue("message")
						.options(List.of(
								ParameterOption.builder().name("Channel").value("channel").build(),
								ParameterOption.builder().name("Message").value("message").build(),
								ParameterOption.builder().name("Member").value("member").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("send")
						.options(List.of(
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Send").value("send").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Role Add").value("roleAdd").build(),
								ParameterOption.builder().name("Role Remove").value("roleRemove").build()
						)).build(),
				NodeParameter.builder()
						.name("guildId").displayName("Guild ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the Discord server (guild).").build(),
				NodeParameter.builder()
						.name("channelId").displayName("Channel ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the channel.").build(),
				NodeParameter.builder()
						.name("messageId").displayName("Message ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the message.").build(),
				NodeParameter.builder()
						.name("userId").displayName("User ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the user.").build(),
				NodeParameter.builder()
						.name("roleId").displayName("Role ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the role.").build(),
				NodeParameter.builder()
						.name("content").displayName("Content")
						.type(ParameterType.STRING).defaultValue("")
						.description("The message content to send.").build(),
				NodeParameter.builder()
						.name("tts").displayName("Text-to-Speech")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether this is a TTS message.").build(),
				NodeParameter.builder()
						.name("embeds").displayName("Embeds")
						.type(ParameterType.JSON).defaultValue("")
						.description("JSON array of embed objects to include with the message.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(50)
						.description("Maximum number of results to return.").build(),
				NodeParameter.builder()
						.name("before").displayName("Before")
						.type(ParameterType.STRING).defaultValue("")
						.description("Get messages before this message ID.").build(),
				NodeParameter.builder()
						.name("after").displayName("After")
						.type(ParameterType.STRING).defaultValue("")
						.description("Get messages after this message ID.").build(),
				NodeParameter.builder()
						.name("around").displayName("Around")
						.type(ParameterType.STRING).defaultValue("")
						.description("Get messages around this message ID.").build(),
				NodeParameter.builder()
						.name("afterMember").displayName("After Member")
						.type(ParameterType.STRING).defaultValue("")
						.description("Get members after this user ID for pagination.").build()
		);
	}
}
