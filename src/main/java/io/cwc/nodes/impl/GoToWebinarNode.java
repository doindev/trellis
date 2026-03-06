package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * GoToWebinar — manage webinars, registrants, attendees, sessions, co-organizers, and panelists.
 */
@Slf4j
@Node(
		type = "goToWebinar",
		displayName = "GoToWebinar",
		description = "Manage GoToWebinar events",
		category = "Miscellaneous",
		icon = "goToWebinar",
		credentials = {"goToWebinarOAuth2Api"},
		searchOnly = true
)
public class GoToWebinarNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.getgo.com/G2W/rest/v2";

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).required(true).defaultValue("webinar")
						.options(List.of(
								ParameterOption.builder().name("Attendee").value("attendee").build(),
								ParameterOption.builder().name("Co-Organizer").value("coOrganizer").build(),
								ParameterOption.builder().name("Panelist").value("panelist").build(),
								ParameterOption.builder().name("Registrant").value("registrant").build(),
								ParameterOption.builder().name("Session").value("session").build(),
								ParameterOption.builder().name("Webinar").value("webinar").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).required(true).defaultValue("get")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("organizerKey").displayName("Organizer Key")
						.type(ParameterType.STRING).required(true).defaultValue("")
						.description("The organizer key for the account.").build(),
				NodeParameter.builder()
						.name("webinarKey").displayName("Webinar Key")
						.type(ParameterType.STRING).defaultValue("")
						.description("The key of the webinar.").build(),
				NodeParameter.builder()
						.name("sessionKey").displayName("Session Key")
						.type(ParameterType.STRING).defaultValue("")
						.description("The key of the session.").build(),
				NodeParameter.builder()
						.name("registrantKey").displayName("Registrant Key")
						.type(ParameterType.STRING).defaultValue("")
						.description("The key of the registrant.").build(),
				NodeParameter.builder()
						.name("coOrganizerKey").displayName("Co-Organizer Key")
						.type(ParameterType.STRING).defaultValue("")
						.description("The key of the co-organizer.").build(),
				NodeParameter.builder()
						.name("panelistKey").displayName("Panelist Key")
						.type(ParameterType.STRING).defaultValue("")
						.description("The key of the panelist.").build(),
				NodeParameter.builder()
						.name("subject").displayName("Subject")
						.type(ParameterType.STRING).defaultValue("")
						.description("The subject/title of the webinar.").build(),
				NodeParameter.builder()
						.name("startTime").displayName("Start Time")
						.type(ParameterType.STRING).defaultValue("")
						.description("Start time in ISO 8601 format.").build(),
				NodeParameter.builder()
						.name("endTime").displayName("End Time")
						.type(ParameterType.STRING).defaultValue("")
						.description("End time in ISO 8601 format.").build(),
				NodeParameter.builder()
						.name("firstName").displayName("First Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("First name of the registrant.").build(),
				NodeParameter.builder()
						.name("lastName").displayName("Last Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Last name of the registrant.").build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Email address.").build(),
				NodeParameter.builder()
						.name("isExternal").displayName("External")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether the co-organizer is external.").build(),
				NodeParameter.builder()
						.name("additionalFields").displayName("Additional Fields")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("Additional fields as JSON.").build(),
				NodeParameter.builder()
						.name("returnAll").displayName("Return All")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether to return all results.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(50)
						.description("Max number of results to return.").build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "webinar");
		String operation = context.getParameter("operation", "get");
		Map<String, Object> credentials = context.getCredentials();

		Map<String, String> headers = getAuthHeaders(credentials);
		String organizerKey = context.getParameter("organizerKey", "");
		String webinarKey = context.getParameter("webinarKey", "");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "attendee" -> handleAttendee(context, headers, organizerKey, webinarKey, operation);
					case "coOrganizer" -> handleCoOrganizer(context, headers, organizerKey, webinarKey, operation);
					case "panelist" -> handlePanelist(context, headers, organizerKey, webinarKey, operation);
					case "registrant" -> handleRegistrant(context, headers, organizerKey, webinarKey, operation);
					case "session" -> handleSession(context, headers, organizerKey, webinarKey, operation);
					case "webinar" -> handleWebinar(context, headers, organizerKey, operation);
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

	private Map<String, Object> handleAttendee(NodeExecutionContext context, Map<String, String> headers,
			String organizerKey, String webinarKey, String operation) throws Exception {
		String sessionKey = context.getParameter("sessionKey", "");
		String basePath = BASE_URL + "/organizers/" + encode(organizerKey) + "/webinars/" + encode(webinarKey)
				+ "/sessions/" + encode(sessionKey) + "/attendees";

		return switch (operation) {
			case "get" -> {
				String registrantKey = context.getParameter("registrantKey", "");
				HttpResponse<String> response = get(basePath + "/" + encode(registrantKey), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(basePath, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("attendees", parseJsonArray(response.body()));
				yield result;
			}
			default -> throw new IllegalArgumentException("Unknown attendee operation: " + operation);
		};
	}

	private Map<String, Object> handleCoOrganizer(NodeExecutionContext context, Map<String, String> headers,
			String organizerKey, String webinarKey, String operation) throws Exception {
		String basePath = BASE_URL + "/organizers/" + encode(organizerKey) + "/webinars/" + encode(webinarKey) + "/coorganizers";

		return switch (operation) {
			case "create" -> {
				String email = context.getParameter("email", "");
				boolean isExternal = toBoolean(context.getParameter("isExternal", false), false);
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("email", email);
				body.put("external", isExternal);
				HttpResponse<String> response = post(basePath, List.of(body), headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String coOrganizerKey = context.getParameter("coOrganizerKey", "");
				boolean isExternal = toBoolean(context.getParameter("isExternal", false), false);
				String url = basePath + "/" + encode(coOrganizerKey) + "?external=" + isExternal;
				HttpResponse<String> response = delete(url, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				yield result;
			}
			case "getAll" -> {
				HttpResponse<String> response = get(basePath, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("coOrganizers", parseJsonArray(response.body()));
				yield result;
			}
			default -> throw new IllegalArgumentException("Unknown co-organizer operation: " + operation);
		};
	}

	private Map<String, Object> handlePanelist(NodeExecutionContext context, Map<String, String> headers,
			String organizerKey, String webinarKey, String operation) throws Exception {
		String basePath = BASE_URL + "/organizers/" + encode(organizerKey) + "/webinars/" + encode(webinarKey) + "/panelists";

		return switch (operation) {
			case "create" -> {
				String name = context.getParameter("firstName", "") + " " + context.getParameter("lastName", "");
				String email = context.getParameter("email", "");
				Map<String, Object> panelist = new LinkedHashMap<>();
				panelist.put("name", name.trim());
				panelist.put("email", email);
				HttpResponse<String> response = post(basePath, List.of(panelist), headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String panelistKey = context.getParameter("panelistKey", "");
				HttpResponse<String> response = delete(basePath + "/" + encode(panelistKey), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				yield result;
			}
			case "getAll" -> {
				HttpResponse<String> response = get(basePath, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("panelists", parseJsonArray(response.body()));
				yield result;
			}
			default -> throw new IllegalArgumentException("Unknown panelist operation: " + operation);
		};
	}

	private Map<String, Object> handleRegistrant(NodeExecutionContext context, Map<String, String> headers,
			String organizerKey, String webinarKey, String operation) throws Exception {
		String basePath = BASE_URL + "/organizers/" + encode(organizerKey) + "/webinars/" + encode(webinarKey) + "/registrants";

		return switch (operation) {
			case "create" -> {
				String firstName = context.getParameter("firstName", "");
				String lastName = context.getParameter("lastName", "");
				String email = context.getParameter("email", "");
				String additionalFields = context.getParameter("additionalFields", "{}");

				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalFields));
				body.put("firstName", firstName);
				body.put("lastName", lastName);
				body.put("email", email);

				HttpResponse<String> response = post(basePath, body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String registrantKey = context.getParameter("registrantKey", "");
				HttpResponse<String> response = delete(basePath + "/" + encode(registrantKey), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				yield result;
			}
			case "get" -> {
				String registrantKey = context.getParameter("registrantKey", "");
				HttpResponse<String> response = get(basePath + "/" + encode(registrantKey), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(basePath, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("registrants", parseJsonArray(response.body()));
				yield result;
			}
			default -> throw new IllegalArgumentException("Unknown registrant operation: " + operation);
		};
	}

	private Map<String, Object> handleSession(NodeExecutionContext context, Map<String, String> headers,
			String organizerKey, String webinarKey, String operation) throws Exception {
		String basePath = BASE_URL + "/organizers/" + encode(organizerKey) + "/webinars/" + encode(webinarKey) + "/sessions";

		return switch (operation) {
			case "get" -> {
				String sessionKey = context.getParameter("sessionKey", "");
				HttpResponse<String> response = get(basePath + "/" + encode(sessionKey), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(basePath, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("sessions", parseJsonArray(response.body()));
				yield result;
			}
			default -> throw new IllegalArgumentException("Unknown session operation: " + operation);
		};
	}

	private Map<String, Object> handleWebinar(NodeExecutionContext context, Map<String, String> headers,
			String organizerKey, String operation) throws Exception {
		String basePath = BASE_URL + "/organizers/" + encode(organizerKey) + "/webinars";

		return switch (operation) {
			case "create" -> {
				String subject = context.getParameter("subject", "");
				String startTime = context.getParameter("startTime", "");
				String endTime = context.getParameter("endTime", "");
				String additionalFields = context.getParameter("additionalFields", "{}");

				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalFields));
				body.put("subject", subject);
				body.put("times", List.of(Map.of("startTime", startTime, "endTime", endTime)));

				HttpResponse<String> response = post(basePath, body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String webinarKey = context.getParameter("webinarKey", "");
				HttpResponse<String> response = delete(basePath + "/" + encode(webinarKey), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				yield result;
			}
			case "get" -> {
				String webinarKey = context.getParameter("webinarKey", "");
				HttpResponse<String> response = get(basePath + "/" + encode(webinarKey), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(basePath, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("webinars", parseJsonArray(response.body()));
				yield result;
			}
			case "update" -> {
				String webinarKey = context.getParameter("webinarKey", "");
				String subject = context.getParameter("subject", "");
				String startTime = context.getParameter("startTime", "");
				String endTime = context.getParameter("endTime", "");
				String additionalFields = context.getParameter("additionalFields", "{}");

				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalFields));
				if (!subject.isEmpty()) body.put("subject", subject);
				if (!startTime.isEmpty() && !endTime.isEmpty()) {
					body.put("times", List.of(Map.of("startTime", startTime, "endTime", endTime)));
				}

				HttpResponse<String> response = put(basePath + "/" + encode(webinarKey), body, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("webinarKey", webinarKey);
				yield result;
			}
			default -> throw new IllegalArgumentException("Unknown webinar operation: " + operation);
		};
	}

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));

		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Authorization", "Bearer " + accessToken);
		return headers;
	}
}
