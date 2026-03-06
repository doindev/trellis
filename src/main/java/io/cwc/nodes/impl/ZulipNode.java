package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Zulip — manage messages, streams, and users in the Zulip team chat platform.
 */
@Node(
		type = "zulip",
		displayName = "Zulip",
		description = "Send messages and manage streams and users in Zulip",
		category = "Communication / Chat & Messaging",
		icon = "zulip",
		credentials = {"zulipApi"},
		searchOnly = true
)
public class ZulipNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String url = context.getCredentialString("url", "");
		String email = context.getCredentialString("email", "");
		String apiKey = context.getCredentialString("apiKey", "");
		String resource = context.getParameter("resource", "message");
		String operation = context.getParameter("operation", "sendStream");

		if (url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}
		String baseUrl = url + "/api/v1";

		String credentials = Base64.getEncoder().encodeToString((email + ":" + apiKey).getBytes());
		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Basic " + credentials);

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "message" -> handleMessage(context, operation, baseUrl, headers);
					case "stream" -> handleStream(context, operation, baseUrl, headers);
					case "user" -> handleUser(context, operation, baseUrl, headers);
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

	private Map<String, Object> handleMessage(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "sendPrivate" -> {
				String to = context.getParameter("to", "");
				String content = context.getParameter("content", "");

				String formBody = "type=private&to=" + encode(to) + "&content=" + encode(content);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> resp = makeFormPost(baseUrl + "/messages", formBody, headers);
				yield parseResponse(resp);
			}
			case "sendStream" -> {
				String stream = context.getParameter("stream", "");
				String topic = context.getParameter("topic", "");
				String content = context.getParameter("content", "");

				String formBody = "type=stream&to=" + encode(stream) +
						"&topic=" + encode(topic) + "&content=" + encode(content);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> resp = makeFormPost(baseUrl + "/messages", formBody, headers);
				yield parseResponse(resp);
			}
			case "update" -> {
				String messageId = context.getParameter("messageId", "");
				String content = context.getParameter("content", "");
				String topic = context.getParameter("topic", "");
				String propagateMode = context.getParameter("propagateMode", "change_one");

				StringBuilder formBody = new StringBuilder();
				if (!content.isBlank()) {
					formBody.append("content=").append(encode(content));
				}
				if (!topic.isBlank()) {
					if (formBody.length() > 0) formBody.append("&");
					formBody.append("topic=").append(encode(topic));
				}
				if (!propagateMode.isBlank()) {
					if (formBody.length() > 0) formBody.append("&");
					formBody.append("propagate_mode=").append(encode(propagateMode));
				}

				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> resp = makeFormRequest("PATCH", baseUrl + "/messages/" + encode(messageId),
						formBody.toString(), headers);
				yield parseResponse(resp);
			}
			case "get" -> {
				String messageId = context.getParameter("messageId", "");
				HttpResponse<String> resp = get(baseUrl + "/messages/" + encode(messageId), headers);
				yield parseResponse(resp);
			}
			case "delete" -> {
				String messageId = context.getParameter("messageId", "");
				HttpResponse<String> resp = delete(baseUrl + "/messages/" + encode(messageId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", resp.statusCode() >= 200 && resp.statusCode() < 300);
				result.put("messageId", messageId);
				yield result;
			}
			default -> throw new IllegalArgumentException("Unknown message operation: " + operation);
		};
	}

	private Map<String, Object> handleStream(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "create" -> {
				String streamName = context.getParameter("streamName", "");
				String streamDescription = context.getParameter("streamDescription", "");
				boolean inviteOnly = toBoolean(context.getParameters().get("inviteOnly"), false);
				boolean announce = toBoolean(context.getParameters().get("announce"), false);

				String subscriptions = "[{\"name\":\"" + streamName + "\",\"description\":\"" +
						streamDescription.replace("\"", "\\\"") + "\"}]";

				String formBody = "subscriptions=" + encode(subscriptions) +
						"&invite_only=" + inviteOnly + "&announce=" + announce;

				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> resp = makeFormPost(baseUrl + "/users/me/subscriptions", formBody, headers);
				yield parseResponse(resp);
			}
			case "delete" -> {
				String streamId = context.getParameter("streamId", "");
				HttpResponse<String> resp = delete(baseUrl + "/streams/" + encode(streamId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", resp.statusCode() >= 200 && resp.statusCode() < 300);
				result.put("streamId", streamId);
				yield result;
			}
			case "getMany" -> {
				HttpResponse<String> resp = get(baseUrl + "/streams", headers);
				yield parseResponse(resp);
			}
			case "getSubscribed" -> {
				HttpResponse<String> resp = get(baseUrl + "/users/me/subscriptions", headers);
				yield parseResponse(resp);
			}
			case "update" -> {
				String streamId = context.getParameter("streamId", "");
				String streamDescription = context.getParameter("streamDescription", "");
				String newName = context.getParameter("newName", "");
				boolean isPrivate = toBoolean(context.getParameters().get("isPrivate"), false);

				StringBuilder formBody = new StringBuilder();
				if (!streamDescription.isBlank()) {
					formBody.append("description=").append(encode(streamDescription));
				}
				if (!newName.isBlank()) {
					if (formBody.length() > 0) formBody.append("&");
					formBody.append("new_name=").append(encode(newName));
				}
				if (formBody.length() > 0) formBody.append("&");
				formBody.append("is_private=").append(isPrivate);

				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> resp = makeFormRequest("PATCH", baseUrl + "/streams/" + encode(streamId),
						formBody.toString(), headers);
				yield parseResponse(resp);
			}
			default -> throw new IllegalArgumentException("Unknown stream operation: " + operation);
		};
	}

	private Map<String, Object> handleUser(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "create" -> {
				String userEmail = context.getParameter("email", "");
				String fullName = context.getParameter("fullName", "");
				String password = context.getParameter("password", "");
				String shortName = context.getParameter("shortName", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("email", userEmail);
				body.put("full_name", fullName);
				body.put("password", password);
				body.put("short_name", shortName);

				headers.put("Content-Type", "application/json");
				HttpResponse<String> resp = post(baseUrl + "/users", body, headers);
				yield parseResponse(resp);
			}
			case "get" -> {
				String userId = context.getParameter("userId", "");
				HttpResponse<String> resp = get(baseUrl + "/users/" + encode(userId), headers);
				yield parseResponse(resp);
			}
			case "getMany" -> {
				HttpResponse<String> resp = get(baseUrl + "/users", headers);
				yield parseResponse(resp);
			}
			case "update" -> {
				String userId = context.getParameter("userId", "");
				String fullName = context.getParameter("fullName", "");
				int role = toInt(context.getParameters().get("role"), 0);

				StringBuilder formBody = new StringBuilder();
				if (!fullName.isBlank()) {
					formBody.append("full_name=").append(encode(fullName));
				}
				if (role > 0) {
					if (formBody.length() > 0) formBody.append("&");
					formBody.append("role=").append(role);
				}

				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> resp = makeFormRequest("PATCH", baseUrl + "/users/" + encode(userId),
						formBody.toString(), headers);
				yield parseResponse(resp);
			}
			case "deactivate" -> {
				String userId = context.getParameter("userId", "");
				HttpResponse<String> resp = delete(baseUrl + "/users/" + encode(userId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", resp.statusCode() >= 200 && resp.statusCode() < 300);
				result.put("userId", userId);
				yield result;
			}
			default -> throw new IllegalArgumentException("Unknown user operation: " + operation);
		};
	}

	private HttpResponse<String> makeFormPost(String url, String formBody,
			Map<String, String> headers) throws Exception {
		java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
				.uri(java.net.URI.create(url))
				.POST(java.net.http.HttpRequest.BodyPublishers.ofString(formBody));

		for (Map.Entry<String, String> header : headers.entrySet()) {
			builder.header(header.getKey(), header.getValue());
		}

		java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
		return client.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> makeFormRequest(String method, String url, String formBody,
			Map<String, String> headers) throws Exception {
		java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
				.uri(java.net.URI.create(url))
				.method(method, java.net.http.HttpRequest.BodyPublishers.ofString(formBody));

		for (Map.Entry<String, String> header : headers.entrySet()) {
			builder.header(header.getKey(), header.getValue());
		}

		java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
		return client.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS)
						.defaultValue("message")
						.options(List.of(
								ParameterOption.builder().name("Message").value("message").build(),
								ParameterOption.builder().name("Stream").value("stream").build(),
								ParameterOption.builder().name("User").value("user").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("sendStream")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Deactivate").value("deactivate").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get Many").value("getMany").build(),
								ParameterOption.builder().name("Get Subscribed").value("getSubscribed").build(),
								ParameterOption.builder().name("Send Private").value("sendPrivate").build(),
								ParameterOption.builder().name("Send to Stream").value("sendStream").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("to").displayName("To")
						.type(ParameterType.STRING).defaultValue("")
						.description("Recipient user IDs (comma-separated for private messages).").build(),
				NodeParameter.builder()
						.name("stream").displayName("Stream")
						.type(ParameterType.STRING).defaultValue("")
						.description("Target stream name.").build(),
				NodeParameter.builder()
						.name("topic").displayName("Topic")
						.type(ParameterType.STRING).defaultValue("")
						.description("Message topic within the stream.").build(),
				NodeParameter.builder()
						.name("content").displayName("Content")
						.type(ParameterType.STRING).defaultValue("")
						.description("Message body content.").build(),
				NodeParameter.builder()
						.name("messageId").displayName("Message ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The message ID.").build(),
				NodeParameter.builder()
						.name("propagateMode").displayName("Propagate Mode")
						.type(ParameterType.OPTIONS)
						.defaultValue("change_one")
						.options(List.of(
								ParameterOption.builder().name("Change One").value("change_one").build(),
								ParameterOption.builder().name("Change Later").value("change_later").build(),
								ParameterOption.builder().name("Change All").value("change_all").build()
						)).build(),
				NodeParameter.builder()
						.name("streamId").displayName("Stream ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The stream ID.").build(),
				NodeParameter.builder()
						.name("streamName").displayName("Stream Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name for the new stream.").build(),
				NodeParameter.builder()
						.name("streamDescription").displayName("Stream Description")
						.type(ParameterType.STRING).defaultValue("")
						.description("Description of the stream.").build(),
				NodeParameter.builder()
						.name("newName").displayName("New Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("New name for the stream.").build(),
				NodeParameter.builder()
						.name("inviteOnly").displayName("Invite Only")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Make the stream invite-only.").build(),
				NodeParameter.builder()
						.name("isPrivate").displayName("Is Private")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether the stream is private.").build(),
				NodeParameter.builder()
						.name("announce").displayName("Announce")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Notify users about the new stream.").build(),
				NodeParameter.builder()
						.name("userId").displayName("User ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The user ID.").build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("User email address.").build(),
				NodeParameter.builder()
						.name("fullName").displayName("Full Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("User full name.").build(),
				NodeParameter.builder()
						.name("password").displayName("Password")
						.type(ParameterType.STRING).defaultValue("")
						.description("User password.").build(),
				NodeParameter.builder()
						.name("shortName").displayName("Short Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("User short name.").build(),
				NodeParameter.builder()
						.name("role").displayName("Role")
						.type(ParameterType.OPTIONS)
						.defaultValue("400")
						.options(List.of(
								ParameterOption.builder().name("Owner").value("100").build(),
								ParameterOption.builder().name("Admin").value("200").build(),
								ParameterOption.builder().name("Moderator").value("300").build(),
								ParameterOption.builder().name("Member").value("400").build(),
								ParameterOption.builder().name("Guest").value("600").build()
						)).build()
		);
	}
}
