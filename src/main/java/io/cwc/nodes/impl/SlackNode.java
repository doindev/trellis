package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Slack — interact with the Slack Web API to manage channels,
 * messages, files, reactions, stars and users.
 */
@Node(
		type = "slack",
		displayName = "Slack",
		description = "Interact with the Slack API",
		category = "Communication / Chat & Messaging",
		icon = "slack",
		credentials = {"slackApi"}
)
public class SlackNode extends AbstractApiNode {

	private static final String BASE_URL = "https://slack.com/api";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String token = context.getCredentialString("accessToken", "");
		String resource = context.getParameter("resource", "message");
		String operation = context.getParameter("operation", "send");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + token);
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "channel" -> handleChannel(context, operation, headers);
					case "message" -> handleMessage(context, operation, headers);
					case "file" -> handleFile(context, operation, headers);
					case "reaction" -> handleReaction(context, operation, headers);
					case "star" -> handleStar(context, operation, headers);
					case "user" -> handleUser(context, operation, headers);
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
			case "archive" -> {
				String channelId = context.getParameter("channelId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("channel", channelId);
				HttpResponse<String> response = post(BASE_URL + "/conversations.archive", body, headers);
				yield parseResponse(response);
			}
			case "close" -> {
				String channelId = context.getParameter("channelId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("channel", channelId);
				HttpResponse<String> response = post(BASE_URL + "/conversations.close", body, headers);
				yield parseResponse(response);
			}
			case "create" -> {
				String channelName = context.getParameter("channelName", "");
				boolean isPrivate = toBoolean(context.getParameters().get("isPrivate"), false);
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", channelName);
				body.put("is_private", isPrivate);
				HttpResponse<String> response = post(BASE_URL + "/conversations.create", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String channelId = context.getParameter("channelId", "");
				HttpResponse<String> response = get(BASE_URL + "/conversations.info?channel=" + encode(channelId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				String cursor = context.getParameter("cursor", "");
				String types = context.getParameter("channelTypes", "public_channel");
				String url = BASE_URL + "/conversations.list?limit=" + limit + "&types=" + encode(types);
				if (!cursor.isBlank()) url += "&cursor=" + encode(cursor);
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "history" -> {
				String channelId = context.getParameter("channelId", "");
				int limit = toInt(context.getParameters().get("limit"), 100);
				String oldest = context.getParameter("oldest", "");
				String latest = context.getParameter("latest", "");
				String url = BASE_URL + "/conversations.history?channel=" + encode(channelId) + "&limit=" + limit;
				if (!oldest.isBlank()) url += "&oldest=" + encode(oldest);
				if (!latest.isBlank()) url += "&latest=" + encode(latest);
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "invite" -> {
				String channelId = context.getParameter("channelId", "");
				String userIds = context.getParameter("userIds", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("channel", channelId);
				body.put("users", userIds);
				HttpResponse<String> response = post(BASE_URL + "/conversations.invite", body, headers);
				yield parseResponse(response);
			}
			case "join" -> {
				String channelId = context.getParameter("channelId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("channel", channelId);
				HttpResponse<String> response = post(BASE_URL + "/conversations.join", body, headers);
				yield parseResponse(response);
			}
			case "kick" -> {
				String channelId = context.getParameter("channelId", "");
				String userId = context.getParameter("userId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("channel", channelId);
				body.put("user", userId);
				HttpResponse<String> response = post(BASE_URL + "/conversations.kick", body, headers);
				yield parseResponse(response);
			}
			case "leave" -> {
				String channelId = context.getParameter("channelId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("channel", channelId);
				HttpResponse<String> response = post(BASE_URL + "/conversations.leave", body, headers);
				yield parseResponse(response);
			}
			case "member" -> {
				String channelId = context.getParameter("channelId", "");
				int limit = toInt(context.getParameters().get("limit"), 100);
				String cursor = context.getParameter("cursor", "");
				String url = BASE_URL + "/conversations.members?channel=" + encode(channelId) + "&limit=" + limit;
				if (!cursor.isBlank()) url += "&cursor=" + encode(cursor);
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "open" -> {
				String userIds = context.getParameter("userIds", "");
				String channelId = context.getParameter("channelId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				if (!channelId.isBlank()) body.put("channel", channelId);
				if (!userIds.isBlank()) body.put("users", userIds);
				body.put("return_im", true);
				HttpResponse<String> response = post(BASE_URL + "/conversations.open", body, headers);
				yield parseResponse(response);
			}
			case "rename" -> {
				String channelId = context.getParameter("channelId", "");
				String channelName = context.getParameter("channelName", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("channel", channelId);
				body.put("name", channelName);
				HttpResponse<String> response = post(BASE_URL + "/conversations.rename", body, headers);
				yield parseResponse(response);
			}
			case "replies" -> {
				String channelId = context.getParameter("channelId", "");
				String ts = context.getParameter("threadTs", "");
				int limit = toInt(context.getParameters().get("limit"), 100);
				String url = BASE_URL + "/conversations.replies?channel=" + encode(channelId) + "&ts=" + encode(ts) + "&limit=" + limit;
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "setPurpose" -> {
				String channelId = context.getParameter("channelId", "");
				String purpose = context.getParameter("purpose", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("channel", channelId);
				body.put("purpose", purpose);
				HttpResponse<String> response = post(BASE_URL + "/conversations.setPurpose", body, headers);
				yield parseResponse(response);
			}
			case "setTopic" -> {
				String channelId = context.getParameter("channelId", "");
				String topic = context.getParameter("topic", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("channel", channelId);
				body.put("topic", topic);
				HttpResponse<String> response = post(BASE_URL + "/conversations.setTopic", body, headers);
				yield parseResponse(response);
			}
			case "unarchive" -> {
				String channelId = context.getParameter("channelId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("channel", channelId);
				HttpResponse<String> response = post(BASE_URL + "/conversations.unarchive", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown channel operation: " + operation);
		};
	}

	// ========================= Message =========================

	private Map<String, Object> handleMessage(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "send" -> {
				String channelId = context.getParameter("channelId", "");
				String text = context.getParameter("text", "");
				String blocksJson = context.getParameter("blocks", "");
				String threadTs = context.getParameter("threadTs", "");
				boolean unfurlLinks = toBoolean(context.getParameters().get("unfurlLinks"), true);
				boolean unfurlMedia = toBoolean(context.getParameters().get("unfurlMedia"), true);

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("channel", channelId);
				body.put("text", text);
				body.put("unfurl_links", unfurlLinks);
				body.put("unfurl_media", unfurlMedia);
				if (!blocksJson.isBlank()) body.put("blocks", parseJsonArray(blocksJson));
				if (!threadTs.isBlank()) body.put("thread_ts", threadTs);

				HttpResponse<String> response = post(BASE_URL + "/chat.postMessage", body, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String channelId = context.getParameter("channelId", "");
				String ts = context.getParameter("messageTs", "");
				String text = context.getParameter("text", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("channel", channelId);
				body.put("ts", ts);
				body.put("text", text);

				HttpResponse<String> response = post(BASE_URL + "/chat.update", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String channelId = context.getParameter("channelId", "");
				String ts = context.getParameter("messageTs", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("channel", channelId);
				body.put("ts", ts);

				HttpResponse<String> response = post(BASE_URL + "/chat.delete", body, headers);
				yield parseResponse(response);
			}
			case "getPermalink" -> {
				String channelId = context.getParameter("channelId", "");
				String ts = context.getParameter("messageTs", "");
				String url = BASE_URL + "/chat.getPermalink?channel=" + encode(channelId) + "&message_ts=" + encode(ts);
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "search" -> {
				String query = context.getParameter("query", "");
				int count = toInt(context.getParameters().get("limit"), 20);
				String sortBy = context.getParameter("sortBy", "timestamp");
				String sortDir = context.getParameter("sortDir", "desc");
				String url = BASE_URL + "/search.messages?query=" + encode(query) + "&count=" + count
						+ "&sort=" + encode(sortBy) + "&sort_dir=" + encode(sortDir);
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown message operation: " + operation);
		};
	}

	// ========================= File =========================

	private Map<String, Object> handleFile(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "get" -> {
				String fileId = context.getParameter("fileId", "");
				HttpResponse<String> response = get(BASE_URL + "/files.info?file=" + encode(fileId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int count = toInt(context.getParameters().get("limit"), 100);
				String channelId = context.getParameter("channelId", "");
				String userId = context.getParameter("userId", "");
				String url = BASE_URL + "/files.list?count=" + count;
				if (!channelId.isBlank()) url += "&channel=" + encode(channelId);
				if (!userId.isBlank()) url += "&user=" + encode(userId);
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "upload" -> {
				String channelId = context.getParameter("channelId", "");
				String fileName = context.getParameter("fileName", "");
				String fileContent = context.getParameter("fileContent", "");
				String title = context.getParameter("title", "");
				String initialComment = context.getParameter("initialComment", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("channels", channelId);
				body.put("content", fileContent);
				if (!fileName.isBlank()) body.put("filename", fileName);
				if (!title.isBlank()) body.put("title", title);
				if (!initialComment.isBlank()) body.put("initial_comment", initialComment);

				HttpResponse<String> response = post(BASE_URL + "/files.upload", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown file operation: " + operation);
		};
	}

	// ========================= Reaction =========================

	private Map<String, Object> handleReaction(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "add" -> {
				String channelId = context.getParameter("channelId", "");
				String ts = context.getParameter("messageTs", "");
				String emojiName = context.getParameter("emojiName", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("channel", channelId);
				body.put("timestamp", ts);
				body.put("name", emojiName);
				HttpResponse<String> response = post(BASE_URL + "/reactions.add", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String channelId = context.getParameter("channelId", "");
				String ts = context.getParameter("messageTs", "");
				String url = BASE_URL + "/reactions.get?channel=" + encode(channelId) + "&timestamp=" + encode(ts);
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "remove" -> {
				String channelId = context.getParameter("channelId", "");
				String ts = context.getParameter("messageTs", "");
				String emojiName = context.getParameter("emojiName", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("channel", channelId);
				body.put("timestamp", ts);
				body.put("name", emojiName);
				HttpResponse<String> response = post(BASE_URL + "/reactions.remove", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown reaction operation: " + operation);
		};
	}

	// ========================= Star =========================

	private Map<String, Object> handleStar(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "add" -> {
				String channelId = context.getParameter("channelId", "");
				String ts = context.getParameter("messageTs", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("channel", channelId);
				body.put("timestamp", ts);
				HttpResponse<String> response = post(BASE_URL + "/stars.add", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String channelId = context.getParameter("channelId", "");
				String ts = context.getParameter("messageTs", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("channel", channelId);
				body.put("timestamp", ts);
				HttpResponse<String> response = post(BASE_URL + "/stars.remove", body, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				String cursor = context.getParameter("cursor", "");
				String url = BASE_URL + "/stars.list?limit=" + limit;
				if (!cursor.isBlank()) url += "&cursor=" + encode(cursor);
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown star operation: " + operation);
		};
	}

	// ========================= User =========================

	private Map<String, Object> handleUser(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "get" -> {
				String userId = context.getParameter("userId", "");
				HttpResponse<String> response = get(BASE_URL + "/users.info?user=" + encode(userId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				String cursor = context.getParameter("cursor", "");
				String url = BASE_URL + "/users.list?limit=" + limit;
				if (!cursor.isBlank()) url += "&cursor=" + encode(cursor);
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "getPresence" -> {
				String userId = context.getParameter("userId", "");
				HttpResponse<String> response = get(BASE_URL + "/users.getPresence?user=" + encode(userId), headers);
				yield parseResponse(response);
			}
			case "getStatus" -> {
				String userId = context.getParameter("userId", "");
				HttpResponse<String> response = get(BASE_URL + "/users.profile.get?user=" + encode(userId), headers);
				yield parseResponse(response);
			}
			case "updateProfile" -> {
				String statusText = context.getParameter("statusText", "");
				String statusEmoji = context.getParameter("statusEmoji", "");
				String displayNameValue = context.getParameter("displayName", "");

				Map<String, Object> profile = new LinkedHashMap<>();
				if (!statusText.isBlank()) profile.put("status_text", statusText);
				if (!statusEmoji.isBlank()) profile.put("status_emoji", statusEmoji);
				if (!displayNameValue.isBlank()) profile.put("display_name", displayNameValue);

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("profile", profile);

				HttpResponse<String> response = post(BASE_URL + "/users.profile.set", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown user operation: " + operation);
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
								ParameterOption.builder().name("File").value("file").build(),
								ParameterOption.builder().name("Reaction").value("reaction").build(),
								ParameterOption.builder().name("Star").value("star").build(),
								ParameterOption.builder().name("User").value("user").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("send")
						.options(List.of(
								ParameterOption.builder().name("Archive").value("archive").build(),
								ParameterOption.builder().name("Close").value("close").build(),
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("History").value("history").build(),
								ParameterOption.builder().name("Invite").value("invite").build(),
								ParameterOption.builder().name("Join").value("join").build(),
								ParameterOption.builder().name("Kick").value("kick").build(),
								ParameterOption.builder().name("Leave").value("leave").build(),
								ParameterOption.builder().name("Member").value("member").build(),
								ParameterOption.builder().name("Open").value("open").build(),
								ParameterOption.builder().name("Rename").value("rename").build(),
								ParameterOption.builder().name("Replies").value("replies").build(),
								ParameterOption.builder().name("Set Purpose").value("setPurpose").build(),
								ParameterOption.builder().name("Set Topic").value("setTopic").build(),
								ParameterOption.builder().name("Unarchive").value("unarchive").build(),
								ParameterOption.builder().name("Send").value("send").build(),
								ParameterOption.builder().name("Update").value("update").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get Permalink").value("getPermalink").build(),
								ParameterOption.builder().name("Search").value("search").build(),
								ParameterOption.builder().name("Upload").value("upload").build(),
								ParameterOption.builder().name("Add").value("add").build(),
								ParameterOption.builder().name("Remove").value("remove").build(),
								ParameterOption.builder().name("Get Presence").value("getPresence").build(),
								ParameterOption.builder().name("Get Status").value("getStatus").build(),
								ParameterOption.builder().name("Update Profile").value("updateProfile").build()
						)).build(),
				NodeParameter.builder()
						.name("channelId").displayName("Channel ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the channel.").build(),
				NodeParameter.builder()
						.name("channelName").displayName("Channel Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name for the new channel.").build(),
				NodeParameter.builder()
						.name("isPrivate").displayName("Private Channel")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether the channel should be private.").build(),
				NodeParameter.builder()
						.name("channelTypes").displayName("Channel Types")
						.type(ParameterType.STRING).defaultValue("public_channel")
						.description("Comma-separated channel types: public_channel, private_channel, mpim, im.").build(),
				NodeParameter.builder()
						.name("text").displayName("Text")
						.type(ParameterType.STRING).defaultValue("")
						.description("The message text.").build(),
				NodeParameter.builder()
						.name("blocks").displayName("Blocks")
						.type(ParameterType.JSON).defaultValue("")
						.description("JSON array of Block Kit blocks.").build(),
				NodeParameter.builder()
						.name("threadTs").displayName("Thread Timestamp")
						.type(ParameterType.STRING).defaultValue("")
						.description("Timestamp of the parent message for threading.").build(),
				NodeParameter.builder()
						.name("messageTs").displayName("Message Timestamp")
						.type(ParameterType.STRING).defaultValue("")
						.description("Timestamp of the message.").build(),
				NodeParameter.builder()
						.name("unfurlLinks").displayName("Unfurl Links")
						.type(ParameterType.BOOLEAN).defaultValue(true)
						.description("Enable unfurling of primarily text-based content.").build(),
				NodeParameter.builder()
						.name("unfurlMedia").displayName("Unfurl Media")
						.type(ParameterType.BOOLEAN).defaultValue(true)
						.description("Enable unfurling of media content.").build(),
				NodeParameter.builder()
						.name("query").displayName("Query")
						.type(ParameterType.STRING).defaultValue("")
						.description("Search query text.").build(),
				NodeParameter.builder()
						.name("sortBy").displayName("Sort By")
						.type(ParameterType.OPTIONS).defaultValue("timestamp")
						.options(List.of(
								ParameterOption.builder().name("Timestamp").value("timestamp").build(),
								ParameterOption.builder().name("Score").value("score").build()
						)).build(),
				NodeParameter.builder()
						.name("sortDir").displayName("Sort Direction")
						.type(ParameterType.OPTIONS).defaultValue("desc")
						.options(List.of(
								ParameterOption.builder().name("Descending").value("desc").build(),
								ParameterOption.builder().name("Ascending").value("asc").build()
						)).build(),
				NodeParameter.builder()
						.name("userId").displayName("User ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the user.").build(),
				NodeParameter.builder()
						.name("userIds").displayName("User IDs")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated list of user IDs.").build(),
				NodeParameter.builder()
						.name("emojiName").displayName("Emoji Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The name of the emoji (without colons).").build(),
				NodeParameter.builder()
						.name("fileId").displayName("File ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the file.").build(),
				NodeParameter.builder()
						.name("fileName").displayName("File Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The file name.").build(),
				NodeParameter.builder()
						.name("fileContent").displayName("File Content")
						.type(ParameterType.STRING).defaultValue("")
						.description("The file content as a string.").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("")
						.description("Title of the file.").build(),
				NodeParameter.builder()
						.name("initialComment").displayName("Initial Comment")
						.type(ParameterType.STRING).defaultValue("")
						.description("Initial comment to add to the file.").build(),
				NodeParameter.builder()
						.name("purpose").displayName("Purpose")
						.type(ParameterType.STRING).defaultValue("")
						.description("The channel purpose.").build(),
				NodeParameter.builder()
						.name("topic").displayName("Topic")
						.type(ParameterType.STRING).defaultValue("")
						.description("The channel topic.").build(),
				NodeParameter.builder()
						.name("oldest").displayName("Oldest")
						.type(ParameterType.STRING).defaultValue("")
						.description("Start of time range (inclusive) as a message timestamp.").build(),
				NodeParameter.builder()
						.name("latest").displayName("Latest")
						.type(ParameterType.STRING).defaultValue("")
						.description("End of time range (inclusive) as a message timestamp.").build(),
				NodeParameter.builder()
						.name("statusText").displayName("Status Text")
						.type(ParameterType.STRING).defaultValue("")
						.description("User status text.").build(),
				NodeParameter.builder()
						.name("statusEmoji").displayName("Status Emoji")
						.type(ParameterType.STRING).defaultValue("")
						.description("User status emoji (e.g., :house_with_garden:).").build(),
				NodeParameter.builder()
						.name("displayName").displayName("Display Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("User display name.").build(),
				NodeParameter.builder()
						.name("cursor").displayName("Cursor")
						.type(ParameterType.STRING).defaultValue("")
						.description("Pagination cursor for next page of results.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Maximum number of results to return.").build()
		);
	}
}
