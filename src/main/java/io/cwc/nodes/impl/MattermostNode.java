package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Mattermost — interact with the Mattermost API to manage channels,
 * messages, reactions and users.
 */
@Node(
		type = "mattermost",
		displayName = "Mattermost",
		description = "Interact with the Mattermost API",
		category = "Communication",
		icon = "mattermost",
		credentials = {"mattermostApi"}
)
public class MattermostNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String baseUrl = context.getCredentialString("baseUrl", "");
		String token = context.getCredentialString("accessToken", "");
		String resource = context.getParameter("resource", "message");
		String operation = context.getParameter("operation", "post");

		// Strip trailing slash
		if (baseUrl.endsWith("/")) {
			baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		}
		String apiUrl = baseUrl + "/api/v4";

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + token);
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "channel" -> handleChannel(context, operation, apiUrl, headers);
					case "message" -> handleMessage(context, operation, apiUrl, headers);
					case "reaction" -> handleReaction(context, operation, apiUrl, headers);
					case "user" -> handleUser(context, operation, apiUrl, headers);
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
			String apiUrl, Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "addUser" -> {
				String channelId = context.getParameter("channelId", "");
				String userId = context.getParameter("userId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("user_id", userId);
				HttpResponse<String> response = post(apiUrl + "/channels/" + encode(channelId) + "/members", body, headers);
				yield parseResponse(response);
			}
			case "create" -> {
				String teamId = context.getParameter("teamId", "");
				String name = context.getParameter("channelName", "");
				String displayName = context.getParameter("displayName", "");
				String type = context.getParameter("channelType", "O");
				String purpose = context.getParameter("purpose", "");
				String channelHeader = context.getParameter("header", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("team_id", teamId);
				body.put("name", name);
				body.put("display_name", displayName);
				body.put("type", type);
				if (!purpose.isBlank()) body.put("purpose", purpose);
				if (!channelHeader.isBlank()) body.put("header", channelHeader);

				HttpResponse<String> response = post(apiUrl + "/channels", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String channelId = context.getParameter("channelId", "");
				HttpResponse<String> response = delete(apiUrl + "/channels/" + encode(channelId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("channelId", channelId);
				yield result;
			}
			case "members" -> {
				String channelId = context.getParameter("channelId", "");
				int limit = toInt(context.getParameters().get("limit"), 100);
				int page = toInt(context.getParameters().get("page"), 0);
				HttpResponse<String> response = get(apiUrl + "/channels/" + encode(channelId) + "/members?per_page=" + limit + "&page=" + page, headers);
				yield parseResponse(response);
			}
			case "restore" -> {
				String channelId = context.getParameter("channelId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				HttpResponse<String> response = post(apiUrl + "/channels/" + encode(channelId) + "/restore", body, headers);
				yield parseResponse(response);
			}
			case "search" -> {
				String teamId = context.getParameter("teamId", "");
				String term = context.getParameter("searchTerm", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("term", term);
				HttpResponse<String> response = post(apiUrl + "/teams/" + encode(teamId) + "/channels/search", body, headers);
				yield parseResponse(response);
			}
			case "statistics" -> {
				String channelId = context.getParameter("channelId", "");
				HttpResponse<String> response = get(apiUrl + "/channels/" + encode(channelId) + "/stats", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown channel operation: " + operation);
		};
	}

	// ========================= Message =========================

	private Map<String, Object> handleMessage(NodeExecutionContext context, String operation,
			String apiUrl, Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "post" -> {
				String channelId = context.getParameter("channelId", "");
				String message = context.getParameter("message", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("channel_id", channelId);
				body.put("message", message);
				HttpResponse<String> response = post(apiUrl + "/posts", body, headers);
				yield parseResponse(response);
			}
			case "postEphemeral" -> {
				String channelId = context.getParameter("channelId", "");
				String userId = context.getParameter("userId", "");
				String message = context.getParameter("message", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("user_id", userId);
				Map<String, Object> postBody = new LinkedHashMap<>();
				postBody.put("channel_id", channelId);
				postBody.put("message", message);
				body.put("post", postBody);
				HttpResponse<String> response = post(apiUrl + "/posts/ephemeral", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String postId = context.getParameter("postId", "");
				HttpResponse<String> response = delete(apiUrl + "/posts/" + encode(postId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("postId", postId);
				yield result;
			}
			default -> throw new IllegalArgumentException("Unknown message operation: " + operation);
		};
	}

	// ========================= Reaction =========================

	private Map<String, Object> handleReaction(NodeExecutionContext context, String operation,
			String apiUrl, Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "create" -> {
				String userId = context.getParameter("userId", "");
				String postId = context.getParameter("postId", "");
				String emojiName = context.getParameter("emojiName", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("user_id", userId);
				body.put("post_id", postId);
				body.put("emoji_name", emojiName);
				HttpResponse<String> response = post(apiUrl + "/reactions", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String userId = context.getParameter("userId", "");
				String postId = context.getParameter("postId", "");
				String emojiName = context.getParameter("emojiName", "");
				HttpResponse<String> response = delete(apiUrl + "/users/" + encode(userId)
						+ "/posts/" + encode(postId) + "/reactions/" + encode(emojiName), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				yield result;
			}
			case "getAll" -> {
				String postId = context.getParameter("postId", "");
				HttpResponse<String> response = get(apiUrl + "/posts/" + encode(postId) + "/reactions", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown reaction operation: " + operation);
		};
	}

	// ========================= User =========================

	private Map<String, Object> handleUser(NodeExecutionContext context, String operation,
			String apiUrl, Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "create" -> {
				String email = context.getParameter("email", "");
				String username = context.getParameter("username", "");
				String password = context.getParameter("password", "");
				String firstName = context.getParameter("firstName", "");
				String lastName = context.getParameter("lastName", "");
				String nickname = context.getParameter("nickname", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("email", email);
				body.put("username", username);
				body.put("password", password);
				if (!firstName.isBlank()) body.put("first_name", firstName);
				if (!lastName.isBlank()) body.put("last_name", lastName);
				if (!nickname.isBlank()) body.put("nickname", nickname);

				HttpResponse<String> response = post(apiUrl + "/users", body, headers);
				yield parseResponse(response);
			}
			case "deactive" -> {
				String userId = context.getParameter("userId", "");
				HttpResponse<String> response = delete(apiUrl + "/users/" + encode(userId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("userId", userId);
				yield result;
			}
			case "getByEmail" -> {
				String email = context.getParameter("email", "");
				HttpResponse<String> response = get(apiUrl + "/users/email/" + encode(email), headers);
				yield parseResponse(response);
			}
			case "getById" -> {
				String userId = context.getParameter("userId", "");
				HttpResponse<String> response = get(apiUrl + "/users/" + encode(userId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				int page = toInt(context.getParameters().get("page"), 0);
				HttpResponse<String> response = get(apiUrl + "/users?per_page=" + limit + "&page=" + page, headers);
				yield parseResponse(response);
			}
			case "invite" -> {
				String teamId = context.getParameter("teamId", "");
				String emails = context.getParameter("emails", "");
				List<String> emailList = new ArrayList<>();
				for (String e : emails.split(",")) {
					String trimmed = e.trim();
					if (!trimmed.isEmpty()) emailList.add(trimmed);
				}
				HttpResponse<String> response = post(apiUrl + "/teams/" + encode(teamId) + "/invite/email",
						emailList, headers);
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
								ParameterOption.builder().name("Reaction").value("reaction").build(),
								ParameterOption.builder().name("User").value("user").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("post")
						.options(List.of(
								ParameterOption.builder().name("Add User").value("addUser").build(),
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Members").value("members").build(),
								ParameterOption.builder().name("Restore").value("restore").build(),
								ParameterOption.builder().name("Search").value("search").build(),
								ParameterOption.builder().name("Statistics").value("statistics").build(),
								ParameterOption.builder().name("Post").value("post").build(),
								ParameterOption.builder().name("Post Ephemeral").value("postEphemeral").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Get By Email").value("getByEmail").build(),
								ParameterOption.builder().name("Get By ID").value("getById").build(),
								ParameterOption.builder().name("Deactivate").value("deactive").build(),
								ParameterOption.builder().name("Invite").value("invite").build()
						)).build(),
				NodeParameter.builder()
						.name("channelId").displayName("Channel ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the channel.").build(),
				NodeParameter.builder()
						.name("teamId").displayName("Team ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the team.").build(),
				NodeParameter.builder()
						.name("channelName").displayName("Channel Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The unique name of the channel.").build(),
				NodeParameter.builder()
						.name("displayName").displayName("Display Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The display name for the channel.").build(),
				NodeParameter.builder()
						.name("channelType").displayName("Channel Type")
						.type(ParameterType.OPTIONS)
						.defaultValue("O")
						.options(List.of(
								ParameterOption.builder().name("Public").value("O").build(),
								ParameterOption.builder().name("Private").value("P").build()
						)).build(),
				NodeParameter.builder()
						.name("purpose").displayName("Purpose")
						.type(ParameterType.STRING).defaultValue("")
						.description("The purpose of the channel.").build(),
				NodeParameter.builder()
						.name("header").displayName("Header")
						.type(ParameterType.STRING).defaultValue("")
						.description("The channel header text.").build(),
				NodeParameter.builder()
						.name("message").displayName("Message")
						.type(ParameterType.STRING).defaultValue("")
						.description("The message content.").build(),
				NodeParameter.builder()
						.name("postId").displayName("Post ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the post.").build(),
				NodeParameter.builder()
						.name("userId").displayName("User ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the user.").build(),
				NodeParameter.builder()
						.name("emojiName").displayName("Emoji Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The name of the emoji (without colons).").build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("The user email address.").build(),
				NodeParameter.builder()
						.name("username").displayName("Username")
						.type(ParameterType.STRING).defaultValue("")
						.description("The username.").build(),
				NodeParameter.builder()
						.name("password").displayName("Password")
						.type(ParameterType.STRING).defaultValue("")
						.description("The user password.").build(),
				NodeParameter.builder()
						.name("firstName").displayName("First Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The first name of the user.").build(),
				NodeParameter.builder()
						.name("lastName").displayName("Last Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The last name of the user.").build(),
				NodeParameter.builder()
						.name("nickname").displayName("Nickname")
						.type(ParameterType.STRING).defaultValue("")
						.description("The nickname of the user.").build(),
				NodeParameter.builder()
						.name("emails").displayName("Emails")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated list of emails to invite.").build(),
				NodeParameter.builder()
						.name("searchTerm").displayName("Search Term")
						.type(ParameterType.STRING).defaultValue("")
						.description("The term to search channels by.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Maximum number of results to return.").build(),
				NodeParameter.builder()
						.name("page").displayName("Page")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Page number for pagination.").build()
		);
	}
}
