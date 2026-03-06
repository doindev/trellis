package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Telegram — interact with the Telegram Bot API to send messages,
 * manage chats, handle files and callbacks.
 */
@Node(
		type = "telegram",
		displayName = "Telegram",
		description = "Interact with the Telegram Bot API",
		category = "Communication",
		icon = "telegram",
		credentials = {"telegramApi"}
)
public class TelegramNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessToken = context.getCredentialString("accessToken", "");
		String resource = context.getParameter("resource", "message");
		String operation = context.getParameter("operation", "sendMessage");

		String baseUrl = "https://api.telegram.org/bot" + accessToken;

		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "chat" -> handleChat(context, operation, baseUrl, headers);
					case "callback" -> handleCallback(context, operation, baseUrl, headers);
					case "file" -> handleFile(context, operation, baseUrl, headers);
					case "message" -> handleMessage(context, operation, baseUrl, headers);
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
		String chatId = context.getParameter("chatId", "");

		return switch (operation) {
			case "get" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("chat_id", chatId);
				HttpResponse<String> response = post(baseUrl + "/getChat", body, headers);
				yield parseResponse(response);
			}
			case "administrators" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("chat_id", chatId);
				HttpResponse<String> response = post(baseUrl + "/getChatAdministrators", body, headers);
				yield parseResponse(response);
			}
			case "leave" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("chat_id", chatId);
				HttpResponse<String> response = post(baseUrl + "/leaveChat", body, headers);
				yield parseResponse(response);
			}
			case "member" -> {
				String userId = context.getParameter("userId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("chat_id", chatId);
				body.put("user_id", userId);
				HttpResponse<String> response = post(baseUrl + "/getChatMember", body, headers);
				yield parseResponse(response);
			}
			case "setDescription" -> {
				String description = context.getParameter("description", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("chat_id", chatId);
				body.put("description", description);
				HttpResponse<String> response = post(baseUrl + "/setChatDescription", body, headers);
				yield parseResponse(response);
			}
			case "setTitle" -> {
				String title = context.getParameter("title", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("chat_id", chatId);
				body.put("title", title);
				HttpResponse<String> response = post(baseUrl + "/setChatTitle", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown chat operation: " + operation);
		};
	}

	// ========================= Callback =========================

	private Map<String, Object> handleCallback(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "answerQuery" -> {
				String callbackQueryId = context.getParameter("callbackQueryId", "");
				String text = context.getParameter("text", "");
				boolean showAlert = toBoolean(context.getParameters().get("showAlert"), false);

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("callback_query_id", callbackQueryId);
				if (!text.isBlank()) body.put("text", text);
				body.put("show_alert", showAlert);

				HttpResponse<String> response = post(baseUrl + "/answerCallbackQuery", body, headers);
				yield parseResponse(response);
			}
			case "answerInlineQuery" -> {
				String inlineQueryId = context.getParameter("inlineQueryId", "");
				String resultsJson = context.getParameter("results", "[]");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("inline_query_id", inlineQueryId);

				List<Map<String, Object>> inlineResults = parseJsonArray(resultsJson);
				body.put("results", inlineResults);

				HttpResponse<String> response = post(baseUrl + "/answerInlineQuery", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown callback operation: " + operation);
		};
	}

	// ========================= File =========================

	private Map<String, Object> handleFile(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		if ("get".equals(operation)) {
			String fileId = context.getParameter("fileId", "");
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("file_id", fileId);
			HttpResponse<String> response = post(baseUrl + "/getFile", body, headers);
			return parseResponse(response);
		}
		throw new IllegalArgumentException("Unknown file operation: " + operation);
	}

	// ========================= Message =========================

	private Map<String, Object> handleMessage(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		String chatId = context.getParameter("chatId", "");

		return switch (operation) {
			case "sendMessage" -> {
				String text = context.getParameter("text", "");
				String parseMode = context.getParameter("parseMode", "");
				boolean disableNotification = toBoolean(context.getParameters().get("disableNotification"), false);
				String replyToMessageId = context.getParameter("replyToMessageId", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("chat_id", chatId);
				body.put("text", text);
				if (!parseMode.isBlank()) body.put("parse_mode", parseMode);
				if (disableNotification) body.put("disable_notification", true);
				if (!replyToMessageId.isBlank()) body.put("reply_to_message_id", Long.parseLong(replyToMessageId));

				HttpResponse<String> response = post(baseUrl + "/sendMessage", body, headers);
				yield parseResponse(response);
			}
			case "editMessageText" -> {
				String messageId = context.getParameter("messageId", "");
				String text = context.getParameter("text", "");
				String parseMode = context.getParameter("parseMode", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("chat_id", chatId);
				body.put("message_id", Long.parseLong(messageId));
				body.put("text", text);
				if (!parseMode.isBlank()) body.put("parse_mode", parseMode);

				HttpResponse<String> response = post(baseUrl + "/editMessageText", body, headers);
				yield parseResponse(response);
			}
			case "deleteMessage" -> {
				String messageId = context.getParameter("messageId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("chat_id", chatId);
				body.put("message_id", Long.parseLong(messageId));
				HttpResponse<String> response = post(baseUrl + "/deleteMessage", body, headers);
				yield parseResponse(response);
			}
			case "pinChatMessage" -> {
				String messageId = context.getParameter("messageId", "");
				boolean disableNotification = toBoolean(context.getParameters().get("disableNotification"), false);
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("chat_id", chatId);
				body.put("message_id", Long.parseLong(messageId));
				if (disableNotification) body.put("disable_notification", true);
				HttpResponse<String> response = post(baseUrl + "/pinChatMessage", body, headers);
				yield parseResponse(response);
			}
			case "unpinChatMessage" -> {
				String messageId = context.getParameter("messageId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("chat_id", chatId);
				if (!messageId.isBlank()) body.put("message_id", Long.parseLong(messageId));
				HttpResponse<String> response = post(baseUrl + "/unpinChatMessage", body, headers);
				yield parseResponse(response);
			}
			case "sendPhoto" -> {
				String photo = context.getParameter("photo", "");
				String caption = context.getParameter("caption", "");
				String parseMode = context.getParameter("parseMode", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("chat_id", chatId);
				body.put("photo", photo);
				if (!caption.isBlank()) body.put("caption", caption);
				if (!parseMode.isBlank()) body.put("parse_mode", parseMode);
				HttpResponse<String> response = post(baseUrl + "/sendPhoto", body, headers);
				yield parseResponse(response);
			}
			case "sendAudio" -> {
				String audio = context.getParameter("audio", "");
				String caption = context.getParameter("caption", "");
				String parseMode = context.getParameter("parseMode", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("chat_id", chatId);
				body.put("audio", audio);
				if (!caption.isBlank()) body.put("caption", caption);
				if (!parseMode.isBlank()) body.put("parse_mode", parseMode);
				HttpResponse<String> response = post(baseUrl + "/sendAudio", body, headers);
				yield parseResponse(response);
			}
			case "sendDocument" -> {
				String document = context.getParameter("document", "");
				String caption = context.getParameter("caption", "");
				String parseMode = context.getParameter("parseMode", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("chat_id", chatId);
				body.put("document", document);
				if (!caption.isBlank()) body.put("caption", caption);
				if (!parseMode.isBlank()) body.put("parse_mode", parseMode);
				HttpResponse<String> response = post(baseUrl + "/sendDocument", body, headers);
				yield parseResponse(response);
			}
			case "sendVideo" -> {
				String video = context.getParameter("video", "");
				String caption = context.getParameter("caption", "");
				String parseMode = context.getParameter("parseMode", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("chat_id", chatId);
				body.put("video", video);
				if (!caption.isBlank()) body.put("caption", caption);
				if (!parseMode.isBlank()) body.put("parse_mode", parseMode);
				HttpResponse<String> response = post(baseUrl + "/sendVideo", body, headers);
				yield parseResponse(response);
			}
			case "sendAnimation" -> {
				String animation = context.getParameter("animation", "");
				String caption = context.getParameter("caption", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("chat_id", chatId);
				body.put("animation", animation);
				if (!caption.isBlank()) body.put("caption", caption);
				HttpResponse<String> response = post(baseUrl + "/sendAnimation", body, headers);
				yield parseResponse(response);
			}
			case "sendSticker" -> {
				String sticker = context.getParameter("sticker", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("chat_id", chatId);
				body.put("sticker", sticker);
				HttpResponse<String> response = post(baseUrl + "/sendSticker", body, headers);
				yield parseResponse(response);
			}
			case "sendLocation" -> {
				double latitude = toDouble(context.getParameters().get("latitude"), 0.0);
				double longitude = toDouble(context.getParameters().get("longitude"), 0.0);
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("chat_id", chatId);
				body.put("latitude", latitude);
				body.put("longitude", longitude);
				HttpResponse<String> response = post(baseUrl + "/sendLocation", body, headers);
				yield parseResponse(response);
			}
			case "sendChatAction" -> {
				String action = context.getParameter("action", "typing");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("chat_id", chatId);
				body.put("action", action);
				HttpResponse<String> response = post(baseUrl + "/sendChatAction", body, headers);
				yield parseResponse(response);
			}
			case "sendMediaGroup" -> {
				String mediaJson = context.getParameter("media", "[]");
				List<Map<String, Object>> media = parseJsonArray(mediaJson);
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("chat_id", chatId);
				body.put("media", media);
				HttpResponse<String> response = post(baseUrl + "/sendMediaGroup", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown message operation: " + operation);
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
								ParameterOption.builder().name("Chat").value("chat").build(),
								ParameterOption.builder().name("Callback").value("callback").build(),
								ParameterOption.builder().name("File").value("file").build(),
								ParameterOption.builder().name("Message").value("message").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("sendMessage")
						.options(List.of(
								ParameterOption.builder().name("Get Chat").value("get").build(),
								ParameterOption.builder().name("Get Administrators").value("administrators").build(),
								ParameterOption.builder().name("Leave Chat").value("leave").build(),
								ParameterOption.builder().name("Get Member").value("member").build(),
								ParameterOption.builder().name("Set Description").value("setDescription").build(),
								ParameterOption.builder().name("Set Title").value("setTitle").build(),
								ParameterOption.builder().name("Answer Callback Query").value("answerQuery").build(),
								ParameterOption.builder().name("Answer Inline Query").value("answerInlineQuery").build(),
								ParameterOption.builder().name("Get File").value("get").build(),
								ParameterOption.builder().name("Send Message").value("sendMessage").build(),
								ParameterOption.builder().name("Edit Message Text").value("editMessageText").build(),
								ParameterOption.builder().name("Delete Message").value("deleteMessage").build(),
								ParameterOption.builder().name("Pin Message").value("pinChatMessage").build(),
								ParameterOption.builder().name("Unpin Message").value("unpinChatMessage").build(),
								ParameterOption.builder().name("Send Photo").value("sendPhoto").build(),
								ParameterOption.builder().name("Send Audio").value("sendAudio").build(),
								ParameterOption.builder().name("Send Document").value("sendDocument").build(),
								ParameterOption.builder().name("Send Video").value("sendVideo").build(),
								ParameterOption.builder().name("Send Animation").value("sendAnimation").build(),
								ParameterOption.builder().name("Send Sticker").value("sendSticker").build(),
								ParameterOption.builder().name("Send Location").value("sendLocation").build(),
								ParameterOption.builder().name("Send Chat Action").value("sendChatAction").build(),
								ParameterOption.builder().name("Send Media Group").value("sendMediaGroup").build()
						)).build(),
				NodeParameter.builder()
						.name("chatId").displayName("Chat ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Unique identifier for the target chat or username of the target channel.").build(),
				NodeParameter.builder()
						.name("text").displayName("Text")
						.type(ParameterType.STRING).defaultValue("")
						.description("The message text to send.").build(),
				NodeParameter.builder()
						.name("parseMode").displayName("Parse Mode")
						.type(ParameterType.OPTIONS)
						.defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("None").value("").build(),
								ParameterOption.builder().name("HTML").value("HTML").build(),
								ParameterOption.builder().name("Markdown").value("Markdown").build(),
								ParameterOption.builder().name("MarkdownV2").value("MarkdownV2").build()
						)).build(),
				NodeParameter.builder()
						.name("disableNotification").displayName("Disable Notification")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Sends the message silently.").build(),
				NodeParameter.builder()
						.name("replyToMessageId").displayName("Reply To Message ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("If set, the message will be a reply to the specified message.").build(),
				NodeParameter.builder()
						.name("messageId").displayName("Message ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the message.").build(),
				NodeParameter.builder()
						.name("userId").displayName("User ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Unique identifier of the target user.").build(),
				NodeParameter.builder()
						.name("description").displayName("Description")
						.type(ParameterType.STRING).defaultValue("")
						.description("New chat description.").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("")
						.description("New chat title.").build(),
				NodeParameter.builder()
						.name("callbackQueryId").displayName("Callback Query ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Unique identifier for the callback query.").build(),
				NodeParameter.builder()
						.name("showAlert").displayName("Show Alert")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Show alert instead of a notification at the top of the chat screen.").build(),
				NodeParameter.builder()
						.name("inlineQueryId").displayName("Inline Query ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Unique identifier for the inline query.").build(),
				NodeParameter.builder()
						.name("results").displayName("Results")
						.type(ParameterType.JSON).defaultValue("[]")
						.description("JSON array of InlineQueryResult objects.").build(),
				NodeParameter.builder()
						.name("fileId").displayName("File ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("File identifier to get info about.").build(),
				NodeParameter.builder()
						.name("photo").displayName("Photo")
						.type(ParameterType.STRING).defaultValue("")
						.description("Photo to send (file_id, URL, or upload).").build(),
				NodeParameter.builder()
						.name("audio").displayName("Audio")
						.type(ParameterType.STRING).defaultValue("")
						.description("Audio file to send (file_id, URL, or upload).").build(),
				NodeParameter.builder()
						.name("document").displayName("Document")
						.type(ParameterType.STRING).defaultValue("")
						.description("Document to send (file_id, URL, or upload).").build(),
				NodeParameter.builder()
						.name("video").displayName("Video")
						.type(ParameterType.STRING).defaultValue("")
						.description("Video to send (file_id, URL, or upload).").build(),
				NodeParameter.builder()
						.name("animation").displayName("Animation")
						.type(ParameterType.STRING).defaultValue("")
						.description("Animation (GIF) to send (file_id, URL, or upload).").build(),
				NodeParameter.builder()
						.name("sticker").displayName("Sticker")
						.type(ParameterType.STRING).defaultValue("")
						.description("Sticker to send (file_id or URL).").build(),
				NodeParameter.builder()
						.name("caption").displayName("Caption")
						.type(ParameterType.STRING).defaultValue("")
						.description("Caption for the media.").build(),
				NodeParameter.builder()
						.name("latitude").displayName("Latitude")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Latitude of the location.").build(),
				NodeParameter.builder()
						.name("longitude").displayName("Longitude")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Longitude of the location.").build(),
				NodeParameter.builder()
						.name("action").displayName("Action")
						.type(ParameterType.OPTIONS)
						.defaultValue("typing")
						.options(List.of(
								ParameterOption.builder().name("Typing").value("typing").build(),
								ParameterOption.builder().name("Upload Photo").value("upload_photo").build(),
								ParameterOption.builder().name("Upload Video").value("upload_video").build(),
								ParameterOption.builder().name("Upload Document").value("upload_document").build(),
								ParameterOption.builder().name("Record Video").value("record_video").build(),
								ParameterOption.builder().name("Record Voice").value("record_voice").build(),
								ParameterOption.builder().name("Find Location").value("find_location").build()
						)).build(),
				NodeParameter.builder()
						.name("media").displayName("Media")
						.type(ParameterType.JSON).defaultValue("[]")
						.description("JSON array of InputMedia objects for media group.").build()
		);
	}
}
