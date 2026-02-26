package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Cisco Webex — manage messages, meetings, and rooms via the Webex REST API.
 */
@Node(
		type = "ciscoWebex",
		displayName = "Webex by Cisco",
		description = "Interact with the Cisco Webex API",
		category = "Communication",
		icon = "ciscoWebex",
		credentials = {"ciscoWebexOAuth2Api"}
)
public class CiscoWebexNode extends AbstractApiNode {

	private static final String BASE_URL = "https://webexapis.com/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessToken = context.getCredentialString("accessToken", "");
		String resource = context.getParameter("resource", "message");
		String operation = context.getParameter("operation", "create");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "message" -> handleMessage(context, operation, headers);
					case "meeting" -> handleMeeting(context, operation, headers);
					case "room" -> handleRoom(context, operation, headers);
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

	// ========================= Message =========================

	private Map<String, Object> handleMessage(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "create" -> {
				String roomId = context.getParameter("roomId", "");
				String toPersonId = context.getParameter("toPersonId", "");
				String toPersonEmail = context.getParameter("toPersonEmail", "");
				String text = context.getParameter("text", "");
				String markdown = context.getParameter("markdown", "");
				String fileUris = context.getParameter("fileUris", "");

				Map<String, Object> body = new LinkedHashMap<>();
				if (!roomId.isEmpty()) body.put("roomId", roomId);
				if (!toPersonId.isEmpty()) body.put("toPersonId", toPersonId);
				if (!toPersonEmail.isEmpty()) body.put("toPersonEmail", toPersonEmail);
				if (!text.isEmpty()) body.put("text", text);
				if (!markdown.isEmpty()) body.put("markdown", markdown);
				if (!fileUris.isEmpty()) {
					body.put("files", Arrays.asList(fileUris.split("\\s*,\\s*")));
				}

				HttpResponse<String> response = post(BASE_URL + "/messages", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String messageId = context.getParameter("messageId", "");
				HttpResponse<String> response = delete(BASE_URL + "/messages/" + encode(messageId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", true);
				result.put("statusCode", response.statusCode());
				yield result;
			}
			case "get" -> {
				String messageId = context.getParameter("messageId", "");
				HttpResponse<String> response = get(BASE_URL + "/messages/" + encode(messageId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				String roomId = context.getParameter("roomId", "");
				int limit = toInt(context.getParameter("limit", 50), 50);
				String url = BASE_URL + "/messages?roomId=" + encode(roomId) + "&max=" + limit;
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String messageId = context.getParameter("messageId", "");
				String text = context.getParameter("text", "");
				String markdown = context.getParameter("markdown", "");

				Map<String, Object> body = new LinkedHashMap<>();
				if (!text.isEmpty()) body.put("text", text);
				if (!markdown.isEmpty()) body.put("markdown", markdown);

				HttpResponse<String> response = put(BASE_URL + "/messages/" + encode(messageId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown message operation: " + operation);
		};
	}

	// ========================= Meeting =========================

	private Map<String, Object> handleMeeting(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "create" -> {
				String title = context.getParameter("title", "");
				String start = context.getParameter("start", "");
				String end = context.getParameter("end", "");
				String agenda = context.getParameter("agenda", "");
				String password = context.getParameter("password", "");
				boolean enableAutoRecordMeeting = toBoolean(context.getParameters().get("enableAutoRecordMeeting"), false);

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", title);
				if (!start.isEmpty()) body.put("start", start);
				if (!end.isEmpty()) body.put("end", end);
				if (!agenda.isEmpty()) body.put("agenda", agenda);
				if (!password.isEmpty()) body.put("password", password);
				body.put("enableAutoRecordMeeting", enableAutoRecordMeeting);

				HttpResponse<String> response = post(BASE_URL + "/meetings", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String meetingId = context.getParameter("meetingId", "");
				HttpResponse<String> response = delete(BASE_URL + "/meetings/" + encode(meetingId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", true);
				result.put("statusCode", response.statusCode());
				yield result;
			}
			case "get" -> {
				String meetingId = context.getParameter("meetingId", "");
				HttpResponse<String> response = get(BASE_URL + "/meetings/" + encode(meetingId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				String meetingType = context.getParameter("meetingType", "scheduledMeeting");
				int limit = toInt(context.getParameter("limit", 50), 50);
				String url = BASE_URL + "/meetings?meetingType=" + encode(meetingType) + "&max=" + limit;
				String from = context.getParameter("from", "");
				String to = context.getParameter("to", "");
				if (!from.isEmpty()) url += "&from=" + encode(from);
				if (!to.isEmpty()) url += "&to=" + encode(to);
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String meetingId = context.getParameter("meetingId", "");
				String title = context.getParameter("title", "");
				String start = context.getParameter("start", "");
				String end = context.getParameter("end", "");
				String agenda = context.getParameter("agenda", "");
				String password = context.getParameter("password", "");

				Map<String, Object> body = new LinkedHashMap<>();
				if (!title.isEmpty()) body.put("title", title);
				if (!start.isEmpty()) body.put("start", start);
				if (!end.isEmpty()) body.put("end", end);
				if (!agenda.isEmpty()) body.put("agenda", agenda);
				if (!password.isEmpty()) body.put("password", password);

				HttpResponse<String> response = put(BASE_URL + "/meetings/" + encode(meetingId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown meeting operation: " + operation);
		};
	}

	// ========================= Room =========================

	private Map<String, Object> handleRoom(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "create" -> {
				String title = context.getParameter("title", "");
				String teamId = context.getParameter("teamId", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", title);
				if (!teamId.isEmpty()) body.put("teamId", teamId);

				HttpResponse<String> response = post(BASE_URL + "/rooms", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String roomId = context.getParameter("roomId", "");
				HttpResponse<String> response = delete(BASE_URL + "/rooms/" + encode(roomId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", true);
				result.put("statusCode", response.statusCode());
				yield result;
			}
			case "get" -> {
				String roomId = context.getParameter("roomId", "");
				HttpResponse<String> response = get(BASE_URL + "/rooms/" + encode(roomId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameter("limit", 50), 50);
				String teamId = context.getParameter("teamId", "");
				String roomType = context.getParameter("roomType", "");
				String url = BASE_URL + "/rooms?max=" + limit;
				if (!teamId.isEmpty()) url += "&teamId=" + encode(teamId);
				if (!roomType.isEmpty()) url += "&type=" + encode(roomType);
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String roomId = context.getParameter("roomId", "");
				String title = context.getParameter("title", "");

				Map<String, Object> body = new LinkedHashMap<>();
				if (!title.isEmpty()) body.put("title", title);

				HttpResponse<String> response = put(BASE_URL + "/rooms/" + encode(roomId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown room operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("message")
						.options(List.of(
								ParameterOption.builder().name("Message").value("message").build(),
								ParameterOption.builder().name("Meeting").value("meeting").build(),
								ParameterOption.builder().name("Room").value("room").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("create")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("roomId").displayName("Room ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the room.").build(),
				NodeParameter.builder()
						.name("messageId").displayName("Message ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the message.").build(),
				NodeParameter.builder()
						.name("meetingId").displayName("Meeting ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the meeting.").build(),
				NodeParameter.builder()
						.name("toPersonId").displayName("To Person ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The person ID of the message recipient.").build(),
				NodeParameter.builder()
						.name("toPersonEmail").displayName("To Person Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("The email address of the message recipient.").build(),
				NodeParameter.builder()
						.name("text").displayName("Text")
						.type(ParameterType.STRING).defaultValue("")
						.description("The plain text content of the message.").build(),
				NodeParameter.builder()
						.name("markdown").displayName("Markdown")
						.type(ParameterType.STRING).defaultValue("")
						.description("The markdown content of the message.").build(),
				NodeParameter.builder()
						.name("fileUris").displayName("File URIs")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated public URLs of files to attach.").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("")
						.description("Title for the room or meeting.").build(),
				NodeParameter.builder()
						.name("teamId").displayName("Team ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the team.").build(),
				NodeParameter.builder()
						.name("start").displayName("Start")
						.type(ParameterType.STRING).defaultValue("")
						.description("Start date/time for the meeting (ISO 8601).").build(),
				NodeParameter.builder()
						.name("end").displayName("End")
						.type(ParameterType.STRING).defaultValue("")
						.description("End date/time for the meeting (ISO 8601).").build(),
				NodeParameter.builder()
						.name("agenda").displayName("Agenda")
						.type(ParameterType.STRING).defaultValue("")
						.description("The meeting agenda.").build(),
				NodeParameter.builder()
						.name("password").displayName("Password")
						.type(ParameterType.STRING).defaultValue("")
						.description("Meeting password.").build(),
				NodeParameter.builder()
						.name("enableAutoRecordMeeting").displayName("Auto Record")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether to auto-record the meeting.").build(),
				NodeParameter.builder()
						.name("meetingType").displayName("Meeting Type")
						.type(ParameterType.OPTIONS).defaultValue("scheduledMeeting")
						.options(List.of(
								ParameterOption.builder().name("Scheduled Meeting").value("scheduledMeeting").build(),
								ParameterOption.builder().name("Meeting Series").value("meetingSeries").build()
						)).build(),
				NodeParameter.builder()
						.name("from").displayName("From")
						.type(ParameterType.STRING).defaultValue("")
						.description("Start date for listing meetings (ISO 8601).").build(),
				NodeParameter.builder()
						.name("to").displayName("To")
						.type(ParameterType.STRING).defaultValue("")
						.description("End date for listing meetings (ISO 8601).").build(),
				NodeParameter.builder()
						.name("roomType").displayName("Room Type")
						.type(ParameterType.OPTIONS).defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("All").value("").build(),
								ParameterOption.builder().name("Direct").value("direct").build(),
								ParameterOption.builder().name("Group").value("group").build()
						)).build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(50)
						.description("Maximum number of items to return.").build()
		);
	}
}
