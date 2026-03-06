package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Matrix — interact with the Matrix chat protocol to send messages,
 * manage rooms and retrieve events.
 */
@Node(
		type = "matrix",
		displayName = "Matrix",
		description = "Interact with the Matrix chat protocol",
		category = "Communication",
		icon = "matrix",
		credentials = {"matrixApi"}
)
public class MatrixNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String homeserverUrl = context.getCredentialString("homeserverUrl", "");
		String accessToken = context.getCredentialString("accessToken", "");
		String resource = context.getParameter("resource", "message");
		String operation = context.getParameter("operation", "create");

		// Strip trailing slash from homeserver URL
		if (homeserverUrl.endsWith("/")) {
			homeserverUrl = homeserverUrl.substring(0, homeserverUrl.length() - 1);
		}
		String baseUrl = homeserverUrl + "/_matrix/client/r0";

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "account" -> handleAccount(context, operation, baseUrl, headers);
					case "room" -> handleRoom(context, operation, baseUrl, headers);
					case "message" -> handleMessage(context, operation, baseUrl, headers);
					case "event" -> handleEvent(context, operation, baseUrl, headers);
					case "roomMember" -> handleRoomMember(context, operation, baseUrl, headers);
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

	// ========================= Account =========================

	private Map<String, Object> handleAccount(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		if ("me".equals(operation)) {
			HttpResponse<String> response = get(baseUrl + "/account/whoami", headers);
			return parseResponse(response);
		}
		throw new IllegalArgumentException("Unknown account operation: " + operation);
	}

	// ========================= Room =========================

	private Map<String, Object> handleRoom(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		String roomId = context.getParameter("roomId", "");

		return switch (operation) {
			case "create" -> {
				String name = context.getParameter("roomName", "");
				String topic = context.getParameter("topic", "");
				String visibility = context.getParameter("visibility", "private");
				String preset = context.getParameter("preset", "private_chat");

				Map<String, Object> body = new LinkedHashMap<>();
				if (!name.isBlank()) body.put("name", name);
				if (!topic.isBlank()) body.put("topic", topic);
				body.put("visibility", visibility);
				body.put("preset", preset);

				HttpResponse<String> response = post(baseUrl + "/createRoom", body, headers);
				yield parseResponse(response);
			}
			case "join" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				HttpResponse<String> response = post(baseUrl + "/join/" + encode(roomId), body, headers);
				yield parseResponse(response);
			}
			case "leave" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				HttpResponse<String> response = post(baseUrl + "/rooms/" + encode(roomId) + "/leave", body, headers);
				yield parseResponse(response);
			}
			case "invite" -> {
				String userId = context.getParameter("userId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("user_id", userId);
				HttpResponse<String> response = post(baseUrl + "/rooms/" + encode(roomId) + "/invite", body, headers);
				yield parseResponse(response);
			}
			case "kick" -> {
				String userId = context.getParameter("userId", "");
				String reason = context.getParameter("reason", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("user_id", userId);
				if (!reason.isBlank()) body.put("reason", reason);
				HttpResponse<String> response = post(baseUrl + "/rooms/" + encode(roomId) + "/kick", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown room operation: " + operation);
		};
	}

	// ========================= Message =========================

	private Map<String, Object> handleMessage(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		String roomId = context.getParameter("roomId", "");

		return switch (operation) {
			case "create" -> {
				String text = context.getParameter("text", "");
				String msgType = context.getParameter("msgType", "m.text");
				String txnId = UUID.randomUUID().toString();

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("msgtype", msgType);
				body.put("body", text);

				String url = baseUrl + "/rooms/" + encode(roomId) + "/send/m.room.message/" + txnId;
				HttpResponse<String> response = put(url, body, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 50);
				String from = context.getParameter("from", "");
				String dir = context.getParameter("direction", "b");

				String url = baseUrl + "/rooms/" + encode(roomId) + "/messages?limit=" + limit + "&dir=" + dir;
				if (!from.isBlank()) {
					url += "&from=" + encode(from);
				}
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown message operation: " + operation);
		};
	}

	// ========================= Event =========================

	private Map<String, Object> handleEvent(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		if ("get".equals(operation)) {
			String roomId = context.getParameter("roomId", "");
			String eventId = context.getParameter("eventId", "");

			String url = baseUrl + "/rooms/" + encode(roomId) + "/event/" + encode(eventId);
			HttpResponse<String> response = get(url, headers);
			return parseResponse(response);
		}
		throw new IllegalArgumentException("Unknown event operation: " + operation);
	}

	// ========================= Room Member =========================

	private Map<String, Object> handleRoomMember(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		if ("getAll".equals(operation)) {
			String roomId = context.getParameter("roomId", "");
			String url = baseUrl + "/rooms/" + encode(roomId) + "/members";
			HttpResponse<String> response = get(url, headers);
			return parseResponse(response);
		}
		throw new IllegalArgumentException("Unknown roomMember operation: " + operation);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS)
						.defaultValue("message")
						.options(List.of(
								ParameterOption.builder().name("Account").value("account").build(),
								ParameterOption.builder().name("Room").value("room").build(),
								ParameterOption.builder().name("Message").value("message").build(),
								ParameterOption.builder().name("Event").value("event").build(),
								ParameterOption.builder().name("Room Member").value("roomMember").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("create")
						.options(List.of(
								ParameterOption.builder().name("Me").value("me").build(),
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Join").value("join").build(),
								ParameterOption.builder().name("Leave").value("leave").build(),
								ParameterOption.builder().name("Invite").value("invite").build(),
								ParameterOption.builder().name("Kick").value("kick").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Get").value("get").build()
						)).build(),
				NodeParameter.builder()
						.name("roomId").displayName("Room ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the room (e.g. !roomId:server.name).").build(),
				NodeParameter.builder()
						.name("text").displayName("Message Text")
						.type(ParameterType.STRING).defaultValue("")
						.description("The text content of the message.").build(),
				NodeParameter.builder()
						.name("msgType").displayName("Message Type")
						.type(ParameterType.OPTIONS)
						.defaultValue("m.text")
						.options(List.of(
								ParameterOption.builder().name("Text").value("m.text").build(),
								ParameterOption.builder().name("Emote").value("m.emote").build(),
								ParameterOption.builder().name("Notice").value("m.notice").build()
						)).build(),
				NodeParameter.builder()
						.name("userId").displayName("User ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The Matrix user ID (e.g. @user:server.name).").build(),
				NodeParameter.builder()
						.name("roomName").displayName("Room Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The name for the new room.").build(),
				NodeParameter.builder()
						.name("topic").displayName("Topic")
						.type(ParameterType.STRING).defaultValue("")
						.description("The topic for the room.").build(),
				NodeParameter.builder()
						.name("visibility").displayName("Visibility")
						.type(ParameterType.OPTIONS)
						.defaultValue("private")
						.options(List.of(
								ParameterOption.builder().name("Private").value("private").build(),
								ParameterOption.builder().name("Public").value("public").build()
						)).build(),
				NodeParameter.builder()
						.name("preset").displayName("Preset")
						.type(ParameterType.OPTIONS)
						.defaultValue("private_chat")
						.options(List.of(
								ParameterOption.builder().name("Private Chat").value("private_chat").build(),
								ParameterOption.builder().name("Public Chat").value("public_chat").build(),
								ParameterOption.builder().name("Trusted Private Chat").value("trusted_private_chat").build()
						)).build(),
				NodeParameter.builder()
						.name("eventId").displayName("Event ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the event to retrieve.").build(),
				NodeParameter.builder()
						.name("reason").displayName("Reason")
						.type(ParameterType.STRING).defaultValue("")
						.description("The reason for kicking the user.").build(),
				NodeParameter.builder()
						.name("direction").displayName("Direction")
						.type(ParameterType.OPTIONS)
						.defaultValue("b")
						.options(List.of(
								ParameterOption.builder().name("Backwards").value("b").build(),
								ParameterOption.builder().name("Forwards").value("f").build()
						)).build(),
				NodeParameter.builder()
						.name("from").displayName("From Token")
						.type(ParameterType.STRING).defaultValue("")
						.description("Pagination token to start returning events from.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(50)
						.description("Maximum number of messages to return.").build()
		);
	}
}
