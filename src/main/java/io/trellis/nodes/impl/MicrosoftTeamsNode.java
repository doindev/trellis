package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Microsoft Teams — interact with the Microsoft Graph API to manage
 * teams channels, channel messages, chats, chat messages, and tasks.
 */
@Node(
		type = "microsoftTeams",
		displayName = "Microsoft Teams",
		description = "Interact with the Microsoft Teams API via Microsoft Graph",
		category = "Communication / Chat & Messaging",
		icon = "microsoftTeams",
		credentials = {"microsoftTeamsOAuth2Api"}
)
public class MicrosoftTeamsNode extends AbstractApiNode {

	private static final String BASE_URL = "https://graph.microsoft.com/v1.0";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String token = context.getCredentialString("accessToken", "");
		String resource = context.getParameter("resource", "channelMessage");
		String operation = context.getParameter("operation", "create");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + token);
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "channel" -> handleChannel(context, operation, headers);
					case "channelMessage" -> handleChannelMessage(context, operation, headers);
					case "chat" -> handleChat(context, operation, headers);
					case "chatMessage" -> handleChatMessage(context, operation, headers);
					case "task" -> handleTask(context, operation, headers);
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
		String teamId = context.getParameter("teamId", "");

		return switch (operation) {
			case "create" -> {
				String displayName = context.getParameter("displayName", "");
				String description = context.getParameter("description", "");
				String membershipType = context.getParameter("membershipType", "standard");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("displayName", displayName);
				if (!description.isBlank()) body.put("description", description);
				body.put("membershipType", membershipType);

				HttpResponse<String> response = post(BASE_URL + "/teams/" + encode(teamId) + "/channels", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String channelId = context.getParameter("channelId", "");
				HttpResponse<String> response = delete(BASE_URL + "/teams/" + encode(teamId) + "/channels/" + encode(channelId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("channelId", channelId);
				yield result;
			}
			case "get" -> {
				String channelId = context.getParameter("channelId", "");
				HttpResponse<String> response = get(BASE_URL + "/teams/" + encode(teamId) + "/channels/" + encode(channelId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/teams/" + encode(teamId) + "/channels", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String channelId = context.getParameter("channelId", "");
				String displayName = context.getParameter("displayName", "");
				String description = context.getParameter("description", "");

				Map<String, Object> body = new LinkedHashMap<>();
				if (!displayName.isBlank()) body.put("displayName", displayName);
				if (!description.isBlank()) body.put("description", description);

				HttpResponse<String> response = patch(BASE_URL + "/teams/" + encode(teamId) + "/channels/" + encode(channelId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown channel operation: " + operation);
		};
	}

	// ========================= Channel Message =========================

	private Map<String, Object> handleChannelMessage(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String teamId = context.getParameter("teamId", "");
		String channelId = context.getParameter("channelId", "");

		return switch (operation) {
			case "create" -> {
				String messageContent = context.getParameter("messageContent", "");
				String contentType = context.getParameter("contentType", "text");

				Map<String, Object> bodyContent = new LinkedHashMap<>();
				bodyContent.put("contentType", contentType);
				bodyContent.put("content", messageContent);

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("body", bodyContent);

				HttpResponse<String> response = post(BASE_URL + "/teams/" + encode(teamId) + "/channels/"
						+ encode(channelId) + "/messages", body, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int top = toInt(context.getParameters().get("limit"), 50);
				HttpResponse<String> response = get(BASE_URL + "/teams/" + encode(teamId) + "/channels/"
						+ encode(channelId) + "/messages?$top=" + top, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown channelMessage operation: " + operation);
		};
	}

	// ========================= Chat =========================

	private Map<String, Object> handleChat(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "create" -> {
				String chatType = context.getParameter("chatType", "oneOnOne");
				String membersJson = context.getParameter("members", "[]");

				List<Map<String, Object>> membersList = parseJsonArray(membersJson);
				List<Map<String, Object>> formattedMembers = new ArrayList<>();
				for (Map<String, Object> member : membersList) {
					Map<String, Object> formatted = new LinkedHashMap<>();
					formatted.put("@odata.type", "#microsoft.graph.aadUserConversationMember");
					formatted.put("roles", List.of("owner"));
					String userId = String.valueOf(member.getOrDefault("userId", ""));
					formatted.put("user@odata.bind", "https://graph.microsoft.com/v1.0/users('" + userId + "')");
					formattedMembers.add(formatted);
				}

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("chatType", chatType);
				body.put("members", formattedMembers);

				HttpResponse<String> response = post(BASE_URL + "/chats", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String chatId = context.getParameter("chatId", "");
				HttpResponse<String> response = get(BASE_URL + "/chats/" + encode(chatId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int top = toInt(context.getParameters().get("limit"), 50);
				HttpResponse<String> response = get(BASE_URL + "/chats?$top=" + top, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown chat operation: " + operation);
		};
	}

	// ========================= Chat Message =========================

	private Map<String, Object> handleChatMessage(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String chatId = context.getParameter("chatId", "");

		return switch (operation) {
			case "create" -> {
				String messageContent = context.getParameter("messageContent", "");
				String contentType = context.getParameter("contentType", "text");

				Map<String, Object> bodyContent = new LinkedHashMap<>();
				bodyContent.put("contentType", contentType);
				bodyContent.put("content", messageContent);

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("body", bodyContent);

				HttpResponse<String> response = post(BASE_URL + "/chats/" + encode(chatId) + "/messages", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String messageId = context.getParameter("messageId", "");
				HttpResponse<String> response = get(BASE_URL + "/chats/" + encode(chatId) + "/messages/" + encode(messageId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int top = toInt(context.getParameters().get("limit"), 50);
				HttpResponse<String> response = get(BASE_URL + "/chats/" + encode(chatId) + "/messages?$top=" + top, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown chatMessage operation: " + operation);
		};
	}

	// ========================= Task (Planner) =========================

	private Map<String, Object> handleTask(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "create" -> {
				String planId = context.getParameter("planId", "");
				String bucketId = context.getParameter("bucketId", "");
				String title = context.getParameter("title", "");
				String assigneeId = context.getParameter("assigneeId", "");
				String dueDateTime = context.getParameter("dueDateTime", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("planId", planId);
				body.put("title", title);
				if (!bucketId.isBlank()) body.put("bucketId", bucketId);
				if (!dueDateTime.isBlank()) body.put("dueDateTime", dueDateTime);
				if (!assigneeId.isBlank()) {
					Map<String, Object> assignments = new LinkedHashMap<>();
					Map<String, Object> assignee = new LinkedHashMap<>();
					assignee.put("@odata.type", "#microsoft.graph.plannerAssignment");
					assignee.put("orderHint", " !");
					assignments.put(assigneeId, assignee);
					body.put("assignments", assignments);
				}

				HttpResponse<String> response = post(BASE_URL + "/planner/tasks", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String taskId = context.getParameter("taskId", "");
				String etag = context.getParameter("etag", "");
				Map<String, String> deleteHeaders = new HashMap<>(headers);
				if (!etag.isBlank()) deleteHeaders.put("If-Match", etag);
				HttpResponse<String> response = delete(BASE_URL + "/planner/tasks/" + encode(taskId), deleteHeaders);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("taskId", taskId);
				yield result;
			}
			case "get" -> {
				String taskId = context.getParameter("taskId", "");
				HttpResponse<String> response = get(BASE_URL + "/planner/tasks/" + encode(taskId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				String planId = context.getParameter("planId", "");
				HttpResponse<String> response = get(BASE_URL + "/planner/plans/" + encode(planId) + "/tasks", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String taskId = context.getParameter("taskId", "");
				String etag = context.getParameter("etag", "");
				String title = context.getParameter("title", "");
				String dueDateTime = context.getParameter("dueDateTime", "");
				String percentComplete = context.getParameter("percentComplete", "");

				Map<String, String> patchHeaders = new HashMap<>(headers);
				if (!etag.isBlank()) patchHeaders.put("If-Match", etag);

				Map<String, Object> body = new LinkedHashMap<>();
				if (!title.isBlank()) body.put("title", title);
				if (!dueDateTime.isBlank()) body.put("dueDateTime", dueDateTime);
				if (!percentComplete.isBlank()) body.put("percentComplete", Integer.parseInt(percentComplete));

				HttpResponse<String> response = patch(BASE_URL + "/planner/tasks/" + encode(taskId), body, patchHeaders);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown task operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS)
						.defaultValue("channelMessage")
						.options(List.of(
								ParameterOption.builder().name("Channel").value("channel").build(),
								ParameterOption.builder().name("Channel Message").value("channelMessage").build(),
								ParameterOption.builder().name("Chat").value("chat").build(),
								ParameterOption.builder().name("Chat Message").value("chatMessage").build(),
								ParameterOption.builder().name("Task").value("task").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("create")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("teamId").displayName("Team ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the Microsoft Teams team.").build(),
				NodeParameter.builder()
						.name("channelId").displayName("Channel ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the channel.").build(),
				NodeParameter.builder()
						.name("chatId").displayName("Chat ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the chat.").build(),
				NodeParameter.builder()
						.name("messageId").displayName("Message ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the message.").build(),
				NodeParameter.builder()
						.name("messageContent").displayName("Message Content")
						.type(ParameterType.STRING).defaultValue("")
						.description("The message content to send.").build(),
				NodeParameter.builder()
						.name("contentType").displayName("Content Type")
						.type(ParameterType.OPTIONS).defaultValue("text")
						.options(List.of(
								ParameterOption.builder().name("Text").value("text").build(),
								ParameterOption.builder().name("HTML").value("html").build()
						)).build(),
				NodeParameter.builder()
						.name("displayName").displayName("Display Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The display name of the resource.").build(),
				NodeParameter.builder()
						.name("description").displayName("Description")
						.type(ParameterType.STRING).defaultValue("")
						.description("The description of the resource.").build(),
				NodeParameter.builder()
						.name("membershipType").displayName("Membership Type")
						.type(ParameterType.OPTIONS).defaultValue("standard")
						.options(List.of(
								ParameterOption.builder().name("Standard").value("standard").build(),
								ParameterOption.builder().name("Private").value("private").build(),
								ParameterOption.builder().name("Shared").value("shared").build()
						)).build(),
				NodeParameter.builder()
						.name("chatType").displayName("Chat Type")
						.type(ParameterType.OPTIONS).defaultValue("oneOnOne")
						.options(List.of(
								ParameterOption.builder().name("One-on-One").value("oneOnOne").build(),
								ParameterOption.builder().name("Group").value("group").build()
						)).build(),
				NodeParameter.builder()
						.name("members").displayName("Members")
						.type(ParameterType.JSON).defaultValue("[]")
						.description("JSON array of member objects with userId field for creating a chat.").build(),
				NodeParameter.builder()
						.name("planId").displayName("Plan ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the Planner plan.").build(),
				NodeParameter.builder()
						.name("bucketId").displayName("Bucket ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the Planner bucket.").build(),
				NodeParameter.builder()
						.name("taskId").displayName("Task ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the Planner task.").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("")
						.description("The title of the task.").build(),
				NodeParameter.builder()
						.name("assigneeId").displayName("Assignee ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The user ID to assign the task to.").build(),
				NodeParameter.builder()
						.name("dueDateTime").displayName("Due Date Time")
						.type(ParameterType.STRING).defaultValue("")
						.description("The due date and time (ISO 8601 format).").build(),
				NodeParameter.builder()
						.name("percentComplete").displayName("Percent Complete")
						.type(ParameterType.STRING).defaultValue("")
						.description("Percentage of task completion (0-100).").build(),
				NodeParameter.builder()
						.name("etag").displayName("ETag")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ETag value for concurrency control (required for update/delete of tasks).").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(50)
						.description("Maximum number of results to return.").build()
		);
	}
}
