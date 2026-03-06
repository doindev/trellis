package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Action Network — manage events, people, petitions, signatures, and tags
 * using the Action Network API.
 */
@Node(
		type = "actionNetwork",
		displayName = "Action Network",
		description = "Manage events, people, petitions, and tags with Action Network",
		category = "Miscellaneous",
		icon = "actionNetwork",
		credentials = {"actionNetworkApi"},
		searchOnly = true
)
public class ActionNetworkNode extends AbstractApiNode {

	private static final String BASE_URL = "https://actionnetwork.org/api/v2";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");

		String resource = context.getParameter("resource", "person");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("OSDI-API-Token", apiKey);
		headers.put("Accept", "application/hal+json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "attendance" -> handleAttendance(context, headers, operation);
					case "event" -> handleEvent(context, headers, operation);
					case "person" -> handlePerson(context, headers, operation);
					case "personTag" -> handlePersonTag(context, headers, operation);
					case "petition" -> handlePetition(context, headers, operation);
					case "signature" -> handleSignature(context, headers, operation);
					case "tag" -> handleTag(context, headers, operation);
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

	private Map<String, Object> handleAttendance(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		String eventId = context.getParameter("eventId", "");
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String email = context.getParameter("email", "");
				body.put("person", Map.of("email_addresses", List.of(Map.of("address", email))));
				HttpResponse<String> response = post(BASE_URL + "/events/" + encode(eventId) + "/attendances", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String attendanceId = context.getParameter("attendanceId", "");
				HttpResponse<String> response = get(BASE_URL + "/events/" + encode(eventId) + "/attendances/" + encode(attendanceId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/events/" + encode(eventId) + "/attendances", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown attendance operation: " + operation);
		};
	}

	private Map<String, Object> handleEvent(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", context.getParameter("title", ""));
				body.put("origin_system", context.getParameter("originSystem", ""));
				String startDate = context.getParameter("startDate", "");
				if (!startDate.isEmpty()) body.put("start_date", startDate);
				HttpResponse<String> response = post(BASE_URL + "/events", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String eventId = context.getParameter("eventId", "");
				HttpResponse<String> response = get(BASE_URL + "/events/" + encode(eventId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/events", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown event operation: " + operation);
		};
	}

	private Map<String, Object> handlePerson(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String email = context.getParameter("email", "");
				body.put("email_addresses", List.of(Map.of("address", email)));
				String givenName = context.getParameter("givenName", "");
				String familyName = context.getParameter("familyName", "");
				if (!givenName.isEmpty() || !familyName.isEmpty()) {
					Map<String, Object> name = new LinkedHashMap<>();
					if (!givenName.isEmpty()) name.put("given_name", givenName);
					if (!familyName.isEmpty()) name.put("family_name", familyName);
					body.put("given_name", givenName);
					body.put("family_name", familyName);
				}
				String language = context.getParameter("language", "");
				if (!language.isEmpty()) body.put("languages_spoken", List.of(language));
				HttpResponse<String> response = post(BASE_URL + "/people", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String personId = context.getParameter("personId", "");
				HttpResponse<String> response = get(BASE_URL + "/people/" + encode(personId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/people", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String personId = context.getParameter("personId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) body.put("email_addresses", List.of(Map.of("address", email)));
				String givenName = context.getParameter("givenName", "");
				if (!givenName.isEmpty()) body.put("given_name", givenName);
				String familyName = context.getParameter("familyName", "");
				if (!familyName.isEmpty()) body.put("family_name", familyName);
				HttpResponse<String> response = put(BASE_URL + "/people/" + encode(personId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown person operation: " + operation);
		};
	}

	private Map<String, Object> handlePersonTag(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		String tagId = context.getParameter("tagId", "");
		String personId = context.getParameter("personId", "");
		return switch (operation) {
			case "add" -> {
				Map<String, Object> body = Map.of("_links", Map.of("osdi:person", Map.of("href", BASE_URL + "/people/" + personId)));
				HttpResponse<String> response = post(BASE_URL + "/tags/" + encode(tagId) + "/taggings", body, headers);
				yield parseResponse(response);
			}
			case "remove" -> {
				String taggingId = context.getParameter("taggingId", "");
				HttpResponse<String> response = delete(BASE_URL + "/tags/" + encode(tagId) + "/taggings/" + encode(taggingId), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown person tag operation: " + operation);
		};
	}

	private Map<String, Object> handlePetition(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", context.getParameter("title", ""));
				body.put("origin_system", context.getParameter("originSystem", ""));
				String target = context.getParameter("target", "");
				if (!target.isEmpty()) body.put("target", List.of(Map.of("name", target)));
				HttpResponse<String> response = post(BASE_URL + "/petitions", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String petitionId = context.getParameter("petitionId", "");
				HttpResponse<String> response = get(BASE_URL + "/petitions/" + encode(petitionId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/petitions", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String petitionId = context.getParameter("petitionId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String title = context.getParameter("title", "");
				if (!title.isEmpty()) body.put("title", title);
				String target = context.getParameter("target", "");
				if (!target.isEmpty()) body.put("target", List.of(Map.of("name", target)));
				HttpResponse<String> response = put(BASE_URL + "/petitions/" + encode(petitionId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown petition operation: " + operation);
		};
	}

	private Map<String, Object> handleSignature(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		String petitionId = context.getParameter("petitionId", "");
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String email = context.getParameter("email", "");
				body.put("person", Map.of("email_addresses", List.of(Map.of("address", email))));
				HttpResponse<String> response = post(BASE_URL + "/petitions/" + encode(petitionId) + "/signatures", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String signatureId = context.getParameter("signatureId", "");
				HttpResponse<String> response = get(BASE_URL + "/petitions/" + encode(petitionId) + "/signatures/" + encode(signatureId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/petitions/" + encode(petitionId) + "/signatures", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String signatureId = context.getParameter("signatureId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) body.put("person", Map.of("email_addresses", List.of(Map.of("address", email))));
				HttpResponse<String> response = put(BASE_URL + "/petitions/" + encode(petitionId) + "/signatures/" + encode(signatureId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown signature operation: " + operation);
		};
	}

	private Map<String, Object> handleTag(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = Map.of("name", context.getParameter("tagName", ""));
				HttpResponse<String> response = post(BASE_URL + "/tags", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String tagId = context.getParameter("tagId", "");
				HttpResponse<String> response = get(BASE_URL + "/tags/" + encode(tagId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/tags", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown tag operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("person")
						.options(List.of(
								ParameterOption.builder().name("Attendance").value("attendance").build(),
								ParameterOption.builder().name("Event").value("event").build(),
								ParameterOption.builder().name("Person").value("person").build(),
								ParameterOption.builder().name("Person Tag").value("personTag").build(),
								ParameterOption.builder().name("Petition").value("petition").build(),
								ParameterOption.builder().name("Signature").value("signature").build(),
								ParameterOption.builder().name("Tag").value("tag").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Add").value("add").build(),
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Remove").value("remove").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("eventId").displayName("Event ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("attendanceId").displayName("Attendance ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("personId").displayName("Person ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("petitionId").displayName("Petition ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("signatureId").displayName("Signature ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("tagId").displayName("Tag ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("taggingId").displayName("Tagging ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("givenName").displayName("Given Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("familyName").displayName("Family Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("tagName").displayName("Tag Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("originSystem").displayName("Origin System")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("target").displayName("Target")
						.type(ParameterType.STRING).defaultValue("")
						.description("Petition target name.").build(),
				NodeParameter.builder()
						.name("startDate").displayName("Start Date")
						.type(ParameterType.STRING).defaultValue("")
						.description("Event start date (ISO 8601).").build(),
				NodeParameter.builder()
						.name("language").displayName("Language")
						.type(ParameterType.STRING).defaultValue("")
						.description("Language spoken (e.g., en).").build()
		);
	}
}
