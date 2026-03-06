package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Zoom — interact with the Zoom API to manage meetings,
 * meeting registrants and webinars.
 */
@Node(
		type = "zoom",
		displayName = "Zoom",
		description = "Interact with the Zoom API",
		category = "Communication",
		icon = "zoom",
		credentials = {"zoomOAuth2Api"}
)
public class ZoomNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.zoom.us/v2";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessToken = context.getCredentialString("accessToken", "");
		String resource = context.getParameter("resource", "meeting");
		String operation = context.getParameter("operation", "create");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "meeting" -> handleMeeting(context, operation, headers);
					case "meetingRegistrant" -> handleMeetingRegistrant(context, operation, headers);
					case "webinar" -> handleWebinar(context, operation, headers);
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

	// ========================= Meeting =========================

	private Map<String, Object> handleMeeting(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "create" -> {
				String userId = context.getParameter("userId", "me");
				String topic = context.getParameter("topic", "");
				int type = toInt(context.getParameters().get("meetingType"), 2);
				String startTime = context.getParameter("startTime", "");
				int duration = toInt(context.getParameters().get("duration"), 60);
				String timezone = context.getParameter("timezone", "");
				String agenda = context.getParameter("agenda", "");
				String password = context.getParameter("password", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("topic", topic);
				body.put("type", type);
				if (!startTime.isBlank()) body.put("start_time", startTime);
				body.put("duration", duration);
				if (!timezone.isBlank()) body.put("timezone", timezone);
				if (!agenda.isBlank()) body.put("agenda", agenda);
				if (!password.isBlank()) body.put("password", password);

				HttpResponse<String> response = post(BASE_URL + "/users/" + encode(userId) + "/meetings", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String meetingId = context.getParameter("meetingId", "");
				HttpResponse<String> response = delete(BASE_URL + "/meetings/" + encode(meetingId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("meetingId", meetingId);
				yield result;
			}
			case "get" -> {
				String meetingId = context.getParameter("meetingId", "");
				HttpResponse<String> response = get(BASE_URL + "/meetings/" + encode(meetingId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				String userId = context.getParameter("userId", "me");
				int pageSize = toInt(context.getParameters().get("limit"), 30);
				String type = context.getParameter("listType", "scheduled");
				HttpResponse<String> response = get(BASE_URL + "/users/" + encode(userId)
						+ "/meetings?page_size=" + pageSize + "&type=" + encode(type), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String meetingId = context.getParameter("meetingId", "");
				String topic = context.getParameter("topic", "");
				String startTime = context.getParameter("startTime", "");
				int duration = toInt(context.getParameters().get("duration"), 60);
				String timezone = context.getParameter("timezone", "");
				String agenda = context.getParameter("agenda", "");
				String password = context.getParameter("password", "");

				Map<String, Object> body = new LinkedHashMap<>();
				if (!topic.isBlank()) body.put("topic", topic);
				if (!startTime.isBlank()) body.put("start_time", startTime);
				body.put("duration", duration);
				if (!timezone.isBlank()) body.put("timezone", timezone);
				if (!agenda.isBlank()) body.put("agenda", agenda);
				if (!password.isBlank()) body.put("password", password);

				HttpResponse<String> response = patch(BASE_URL + "/meetings/" + encode(meetingId), body, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("meetingId", meetingId);
				yield result;
			}
			default -> throw new IllegalArgumentException("Unknown meeting operation: " + operation);
		};
	}

	// ========================= Meeting Registrant =========================

	private Map<String, Object> handleMeetingRegistrant(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String meetingId = context.getParameter("meetingId", "");

		return switch (operation) {
			case "create" -> {
				String email = context.getParameter("email", "");
				String firstName = context.getParameter("firstName", "");
				String lastName = context.getParameter("lastName", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("email", email);
				body.put("first_name", firstName);
				if (!lastName.isBlank()) body.put("last_name", lastName);

				HttpResponse<String> response = post(BASE_URL + "/meetings/" + encode(meetingId)
						+ "/registrants", body, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int pageSize = toInt(context.getParameters().get("limit"), 30);
				String status = context.getParameter("status", "approved");
				HttpResponse<String> response = get(BASE_URL + "/meetings/" + encode(meetingId)
						+ "/registrants?page_size=" + pageSize + "&status=" + encode(status), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String action = context.getParameter("registrantAction", "approve");
				String registrantId = context.getParameter("registrantId", "");
				String registrantEmail = context.getParameter("registrantEmail", "");

				Map<String, Object> registrant = new LinkedHashMap<>();
				registrant.put("id", registrantId);
				if (!registrantEmail.isBlank()) registrant.put("email", registrantEmail);

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("action", action);
				body.put("registrants", List.of(registrant));

				HttpResponse<String> response = put(BASE_URL + "/meetings/" + encode(meetingId)
						+ "/registrants/status", body, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("action", action);
				result.put("registrantId", registrantId);
				yield result;
			}
			default -> throw new IllegalArgumentException("Unknown meeting registrant operation: " + operation);
		};
	}

	// ========================= Webinar =========================

	private Map<String, Object> handleWebinar(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "create" -> {
				String userId = context.getParameter("userId", "me");
				String topic = context.getParameter("topic", "");
				int type = toInt(context.getParameters().get("webinarType"), 5);
				String startTime = context.getParameter("startTime", "");
				int duration = toInt(context.getParameters().get("duration"), 60);
				String timezone = context.getParameter("timezone", "");
				String agenda = context.getParameter("agenda", "");
				String password = context.getParameter("password", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("topic", topic);
				body.put("type", type);
				if (!startTime.isBlank()) body.put("start_time", startTime);
				body.put("duration", duration);
				if (!timezone.isBlank()) body.put("timezone", timezone);
				if (!agenda.isBlank()) body.put("agenda", agenda);
				if (!password.isBlank()) body.put("password", password);

				HttpResponse<String> response = post(BASE_URL + "/users/" + encode(userId) + "/webinars", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String webinarId = context.getParameter("webinarId", "");
				HttpResponse<String> response = delete(BASE_URL + "/webinars/" + encode(webinarId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("webinarId", webinarId);
				yield result;
			}
			case "get" -> {
				String webinarId = context.getParameter("webinarId", "");
				HttpResponse<String> response = get(BASE_URL + "/webinars/" + encode(webinarId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				String userId = context.getParameter("userId", "me");
				int pageSize = toInt(context.getParameters().get("limit"), 30);
				HttpResponse<String> response = get(BASE_URL + "/users/" + encode(userId)
						+ "/webinars?page_size=" + pageSize, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String webinarId = context.getParameter("webinarId", "");
				String topic = context.getParameter("topic", "");
				String startTime = context.getParameter("startTime", "");
				int duration = toInt(context.getParameters().get("duration"), 60);
				String timezone = context.getParameter("timezone", "");
				String agenda = context.getParameter("agenda", "");
				String password = context.getParameter("password", "");

				Map<String, Object> body = new LinkedHashMap<>();
				if (!topic.isBlank()) body.put("topic", topic);
				if (!startTime.isBlank()) body.put("start_time", startTime);
				body.put("duration", duration);
				if (!timezone.isBlank()) body.put("timezone", timezone);
				if (!agenda.isBlank()) body.put("agenda", agenda);
				if (!password.isBlank()) body.put("password", password);

				HttpResponse<String> response = patch(BASE_URL + "/webinars/" + encode(webinarId), body, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("webinarId", webinarId);
				yield result;
			}
			default -> throw new IllegalArgumentException("Unknown webinar operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS)
						.defaultValue("meeting")
						.options(List.of(
								ParameterOption.builder().name("Meeting").value("meeting").build(),
								ParameterOption.builder().name("Meeting Registrant").value("meetingRegistrant").build(),
								ParameterOption.builder().name("Webinar").value("webinar").build()
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
						.name("userId").displayName("User ID")
						.type(ParameterType.STRING).defaultValue("me")
						.description("The user ID or email. Use 'me' for the authenticated user.").build(),
				NodeParameter.builder()
						.name("meetingId").displayName("Meeting ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the meeting.").build(),
				NodeParameter.builder()
						.name("webinarId").displayName("Webinar ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the webinar.").build(),
				NodeParameter.builder()
						.name("topic").displayName("Topic")
						.type(ParameterType.STRING).defaultValue("")
						.description("The meeting or webinar topic.").build(),
				NodeParameter.builder()
						.name("meetingType").displayName("Meeting Type")
						.type(ParameterType.OPTIONS)
						.defaultValue(2)
						.options(List.of(
								ParameterOption.builder().name("Instant").value(1).build(),
								ParameterOption.builder().name("Scheduled").value(2).build(),
								ParameterOption.builder().name("Recurring (No Fixed Time)").value(3).build(),
								ParameterOption.builder().name("Recurring (Fixed Time)").value(8).build()
						)).build(),
				NodeParameter.builder()
						.name("webinarType").displayName("Webinar Type")
						.type(ParameterType.OPTIONS)
						.defaultValue(5)
						.options(List.of(
								ParameterOption.builder().name("Webinar").value(5).build(),
								ParameterOption.builder().name("Recurring (No Fixed Time)").value(6).build(),
								ParameterOption.builder().name("Recurring (Fixed Time)").value(9).build()
						)).build(),
				NodeParameter.builder()
						.name("startTime").displayName("Start Time")
						.type(ParameterType.STRING).defaultValue("")
						.description("Meeting start time in ISO 8601 format (e.g. 2025-01-01T10:00:00Z).").build(),
				NodeParameter.builder()
						.name("duration").displayName("Duration (minutes)")
						.type(ParameterType.NUMBER).defaultValue(60)
						.description("Duration of the meeting in minutes.").build(),
				NodeParameter.builder()
						.name("timezone").displayName("Timezone")
						.type(ParameterType.STRING).defaultValue("")
						.description("Timezone for the meeting (e.g. America/New_York).").build(),
				NodeParameter.builder()
						.name("agenda").displayName("Agenda")
						.type(ParameterType.STRING).defaultValue("")
						.description("Meeting or webinar agenda.").build(),
				NodeParameter.builder()
						.name("password").displayName("Password")
						.type(ParameterType.STRING).defaultValue("")
						.description("Password for the meeting or webinar.").build(),
				NodeParameter.builder()
						.name("listType").displayName("List Type")
						.type(ParameterType.OPTIONS)
						.defaultValue("scheduled")
						.options(List.of(
								ParameterOption.builder().name("Scheduled").value("scheduled").build(),
								ParameterOption.builder().name("Live").value("live").build(),
								ParameterOption.builder().name("Upcoming").value("upcoming").build()
						)).build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Registrant email address.").build(),
				NodeParameter.builder()
						.name("firstName").displayName("First Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Registrant first name.").build(),
				NodeParameter.builder()
						.name("lastName").displayName("Last Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Registrant last name.").build(),
				NodeParameter.builder()
						.name("status").displayName("Status")
						.type(ParameterType.OPTIONS)
						.defaultValue("approved")
						.options(List.of(
								ParameterOption.builder().name("Approved").value("approved").build(),
								ParameterOption.builder().name("Pending").value("pending").build(),
								ParameterOption.builder().name("Denied").value("denied").build()
						)).build(),
				NodeParameter.builder()
						.name("registrantAction").displayName("Action")
						.type(ParameterType.OPTIONS)
						.defaultValue("approve")
						.options(List.of(
								ParameterOption.builder().name("Approve").value("approve").build(),
								ParameterOption.builder().name("Deny").value("deny").build(),
								ParameterOption.builder().name("Cancel").value("cancel").build()
						)).build(),
				NodeParameter.builder()
						.name("registrantId").displayName("Registrant ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the registrant.").build(),
				NodeParameter.builder()
						.name("registrantEmail").displayName("Registrant Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("The email of the registrant.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(30)
						.description("Maximum number of results to return.").build()
		);
	}
}
