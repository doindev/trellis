package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Twist — manage channels, threads, comments, and message conversations
 * in the Twist team communication platform.
 */
@Node(
		type = "twist",
		displayName = "Twist",
		description = "Manage channels, threads, comments and messages in Twist",
		category = "Communication / Chat & Messaging",
		icon = "twist",
		credentials = {"twistOAuth2Api"}
)
public class TwistNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.twist.com/api/v3";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessToken = context.getCredentialString("accessToken", "");
		String resource = context.getParameter("resource", "channel");
		String operation = context.getParameter("operation", "getMany");

		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Authorization", "Bearer " + accessToken);

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "channel" -> handleChannel(context, operation, headers);
					case "comment" -> handleComment(context, operation, headers);
					case "messageConversation" -> handleMessageConversation(context, operation, headers);
					case "thread" -> handleThread(context, operation, headers);
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

	private Map<String, Object> handleChannel(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "create" -> {
				String workspaceId = context.getParameter("workspaceId", "");
				String name = context.getParameter("name", "");
				String description = context.getParameter("description", "");
				boolean isPublic = toBoolean(context.getParameters().get("public"), false);

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("workspace_id", Integer.parseInt(workspaceId));
				body.put("name", name);
				if (!description.isBlank()) body.put("description", description);
				body.put("public", isPublic);

				HttpResponse<String> resp = post(BASE_URL + "/channels/add", body, headers);
				yield parseResponse(resp);
			}
			case "delete" -> {
				String channelId = context.getParameter("channelId", "");
				Map<String, Object> body = Map.of("id", Integer.parseInt(channelId));
				HttpResponse<String> resp = post(BASE_URL + "/channels/remove", body, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", resp.statusCode() >= 200 && resp.statusCode() < 300);
				result.put("channelId", channelId);
				yield result;
			}
			case "get" -> {
				String channelId = context.getParameter("channelId", "");
				HttpResponse<String> resp = get(BASE_URL + "/channels/getone?id=" + encode(channelId), headers);
				yield parseResponse(resp);
			}
			case "getMany" -> {
				String workspaceId = context.getParameter("workspaceId", "");
				HttpResponse<String> resp = get(BASE_URL + "/channels/get?workspace_id=" + encode(workspaceId), headers);
				yield parseResponse(resp);
			}
			case "update" -> {
				String channelId = context.getParameter("channelId", "");
				String name = context.getParameter("name", "");
				String description = context.getParameter("description", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("id", Integer.parseInt(channelId));
				if (!name.isBlank()) body.put("name", name);
				if (!description.isBlank()) body.put("description", description);

				HttpResponse<String> resp = post(BASE_URL + "/channels/update", body, headers);
				yield parseResponse(resp);
			}
			case "archive" -> {
				String channelId = context.getParameter("channelId", "");
				Map<String, Object> body = Map.of("id", Integer.parseInt(channelId));
				HttpResponse<String> resp = post(BASE_URL + "/channels/archive", body, headers);
				yield parseResponse(resp);
			}
			case "unarchive" -> {
				String channelId = context.getParameter("channelId", "");
				Map<String, Object> body = Map.of("id", Integer.parseInt(channelId));
				HttpResponse<String> resp = post(BASE_URL + "/channels/unarchive", body, headers);
				yield parseResponse(resp);
			}
			default -> throw new IllegalArgumentException("Unknown channel operation: " + operation);
		};
	}

	private Map<String, Object> handleComment(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "create" -> {
				String threadId = context.getParameter("threadId", "");
				String content = context.getParameter("content", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("thread_id", Integer.parseInt(threadId));
				body.put("content", content);

				HttpResponse<String> resp = post(BASE_URL + "/comments/add", body, headers);
				yield parseResponse(resp);
			}
			case "delete" -> {
				String commentId = context.getParameter("commentId", "");
				Map<String, Object> body = Map.of("id", Integer.parseInt(commentId));
				HttpResponse<String> resp = post(BASE_URL + "/comments/remove", body, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", resp.statusCode() >= 200 && resp.statusCode() < 300);
				result.put("commentId", commentId);
				yield result;
			}
			case "get" -> {
				String commentId = context.getParameter("commentId", "");
				HttpResponse<String> resp = get(BASE_URL + "/comments/getone?id=" + encode(commentId), headers);
				yield parseResponse(resp);
			}
			case "getMany" -> {
				String threadId = context.getParameter("threadId", "");
				HttpResponse<String> resp = get(BASE_URL + "/comments/get?thread_id=" + encode(threadId), headers);
				yield parseResponse(resp);
			}
			case "update" -> {
				String commentId = context.getParameter("commentId", "");
				String content = context.getParameter("content", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("id", Integer.parseInt(commentId));
				body.put("content", content);

				HttpResponse<String> resp = post(BASE_URL + "/comments/update", body, headers);
				yield parseResponse(resp);
			}
			default -> throw new IllegalArgumentException("Unknown comment operation: " + operation);
		};
	}

	private Map<String, Object> handleMessageConversation(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "create" -> {
				String conversationId = context.getParameter("conversationId", "");
				String content = context.getParameter("content", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("conversation_id", Integer.parseInt(conversationId));
				body.put("content", content);

				HttpResponse<String> resp = post(BASE_URL + "/conversation_messages/add", body, headers);
				yield parseResponse(resp);
			}
			case "delete" -> {
				String messageId = context.getParameter("messageId", "");
				Map<String, Object> body = Map.of("id", Integer.parseInt(messageId));
				HttpResponse<String> resp = post(BASE_URL + "/conversation_messages/remove", body, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", resp.statusCode() >= 200 && resp.statusCode() < 300);
				result.put("messageId", messageId);
				yield result;
			}
			case "get" -> {
				String messageId = context.getParameter("messageId", "");
				HttpResponse<String> resp = get(BASE_URL + "/conversation_messages/getone?id=" + encode(messageId), headers);
				yield parseResponse(resp);
			}
			case "getMany" -> {
				String conversationId = context.getParameter("conversationId", "");
				HttpResponse<String> resp = get(BASE_URL + "/conversation_messages/get?conversation_id=" + encode(conversationId), headers);
				yield parseResponse(resp);
			}
			case "update" -> {
				String messageId = context.getParameter("messageId", "");
				String content = context.getParameter("content", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("id", Integer.parseInt(messageId));
				body.put("content", content);

				HttpResponse<String> resp = post(BASE_URL + "/conversation_messages/update", body, headers);
				yield parseResponse(resp);
			}
			default -> throw new IllegalArgumentException("Unknown conversation message operation: " + operation);
		};
	}

	private Map<String, Object> handleThread(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "create" -> {
				String channelId = context.getParameter("channelId", "");
				String title = context.getParameter("title", "");
				String content = context.getParameter("content", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("channel_id", Integer.parseInt(channelId));
				body.put("title", title);
				body.put("content", content);

				HttpResponse<String> resp = post(BASE_URL + "/threads/add", body, headers);
				yield parseResponse(resp);
			}
			case "delete" -> {
				String threadId = context.getParameter("threadId", "");
				Map<String, Object> body = Map.of("id", Integer.parseInt(threadId));
				HttpResponse<String> resp = post(BASE_URL + "/threads/remove", body, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", resp.statusCode() >= 200 && resp.statusCode() < 300);
				result.put("threadId", threadId);
				yield result;
			}
			case "get" -> {
				String threadId = context.getParameter("threadId", "");
				HttpResponse<String> resp = get(BASE_URL + "/threads/getone?id=" + encode(threadId), headers);
				yield parseResponse(resp);
			}
			case "getMany" -> {
				String channelId = context.getParameter("channelId", "");
				HttpResponse<String> resp = get(BASE_URL + "/threads/get?channel_id=" + encode(channelId), headers);
				yield parseResponse(resp);
			}
			case "update" -> {
				String threadId = context.getParameter("threadId", "");
				String title = context.getParameter("title", "");
				String content = context.getParameter("content", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("id", Integer.parseInt(threadId));
				if (!title.isBlank()) body.put("title", title);
				if (!content.isBlank()) body.put("content", content);

				HttpResponse<String> resp = post(BASE_URL + "/threads/update", body, headers);
				yield parseResponse(resp);
			}
			default -> throw new IllegalArgumentException("Unknown thread operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS)
						.defaultValue("channel")
						.options(List.of(
								ParameterOption.builder().name("Channel").value("channel").build(),
								ParameterOption.builder().name("Comment").value("comment").build(),
								ParameterOption.builder().name("Message Conversation").value("messageConversation").build(),
								ParameterOption.builder().name("Thread").value("thread").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("getMany")
						.options(List.of(
								ParameterOption.builder().name("Archive").value("archive").build(),
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get Many").value("getMany").build(),
								ParameterOption.builder().name("Unarchive").value("unarchive").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("workspaceId").displayName("Workspace ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The workspace ID.").build(),
				NodeParameter.builder()
						.name("channelId").displayName("Channel ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The channel ID.").build(),
				NodeParameter.builder()
						.name("threadId").displayName("Thread ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The thread ID.").build(),
				NodeParameter.builder()
						.name("commentId").displayName("Comment ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The comment ID.").build(),
				NodeParameter.builder()
						.name("conversationId").displayName("Conversation ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The conversation ID.").build(),
				NodeParameter.builder()
						.name("messageId").displayName("Message ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The message ID.").build(),
				NodeParameter.builder()
						.name("name").displayName("Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name of the channel.").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("")
						.description("Title of the thread.").build(),
				NodeParameter.builder()
						.name("content").displayName("Content")
						.type(ParameterType.STRING).defaultValue("")
						.description("Content of the message/comment/thread.").build(),
				NodeParameter.builder()
						.name("description").displayName("Description")
						.type(ParameterType.STRING).defaultValue("")
						.description("Channel description.").build(),
				NodeParameter.builder()
						.name("public").displayName("Public")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether the channel is public.").build()
		);
	}
}
