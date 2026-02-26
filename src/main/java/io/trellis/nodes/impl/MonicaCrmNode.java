package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Monica CRM — manage contacts, activities, notes, and more in Monica personal CRM.
 */
@Node(
		type = "monicaCrm",
		displayName = "Monica CRM",
		description = "Manage contacts, activities, notes, and more in Monica CRM",
		category = "CRM & Sales",
		icon = "monicaCrm",
		credentials = {"monicaCrmApi"}
)
public class MonicaCrmNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String baseUrl = getBaseUrl(credentials);
		String apiToken = (String) credentials.getOrDefault("apiToken", "");

		String resource = context.getParameter("resource", "contact");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + apiToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "activity" -> handleActivity(context, baseUrl, headers, operation);
					case "contact" -> handleContact(context, baseUrl, headers, operation);
					case "contactTag" -> handleContactTag(context, baseUrl, headers, operation);
					case "note" -> handleNote(context, baseUrl, headers, operation);
					case "task" -> handleTask(context, baseUrl, headers, operation);
					case "tag" -> handleTag(context, baseUrl, headers, operation);
					case "journalEntry" -> handleJournalEntry(context, baseUrl, headers, operation);
					case "call" -> handleCall(context, baseUrl, headers, operation);
					case "reminder" -> handleReminder(context, baseUrl, headers, operation);
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

	protected String getBaseUrl(Map<String, Object> credentials) {
		String environment = (String) credentials.getOrDefault("environment", "cloud");
		if ("selfHosted".equals(environment)) {
			String domain = (String) credentials.getOrDefault("domain", "");
			return domain.endsWith("/") ? domain.substring(0, domain.length() - 1) + "/api" : domain + "/api";
		}
		return "https://app.monicahq.com/api";
	}

	// ---- Activity ----
	private Map<String, Object> handleActivity(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("activity_type_id", context.getParameter("activityTypeId", ""));
				body.put("summary", context.getParameter("summary", ""));
				body.put("happened_at", context.getParameter("happenedAt", ""));
				String contacts = context.getParameter("contacts", "");
				if (!contacts.isEmpty()) {
					body.put("contacts", Arrays.stream(contacts.split(",")).map(String::trim).map(Integer::parseInt).toList());
				}
				HttpResponse<String> response = post(baseUrl + "/activities", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String id = context.getParameter("activityId", "");
				HttpResponse<String> response = delete(baseUrl + "/activities/" + id, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String id = context.getParameter("activityId", "");
				HttpResponse<String> response = get(baseUrl + "/activities/" + id, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(baseUrl + "/activities", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String id = context.getParameter("activityId", "");
				HttpResponse<String> resp = get(baseUrl + "/activities/" + id, headers);
				Map<String, Object> existing = parseResponse(resp);
				@SuppressWarnings("unchecked")
				Map<String, Object> data = (Map<String, Object>) existing.getOrDefault("data", existing);
				Map<String, Object> body = new LinkedHashMap<>(data);
				String summary = context.getParameter("summary", "");
				if (!summary.isEmpty()) body.put("summary", summary);
				String happenedAt = context.getParameter("happenedAt", "");
				if (!happenedAt.isEmpty()) body.put("happened_at", happenedAt);
				HttpResponse<String> response = put(baseUrl + "/activities/" + id, body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown activity operation: " + operation);
		};
	}

	// ---- Contact ----
	private Map<String, Object> handleContact(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("first_name", context.getParameter("firstName", ""));
				body.put("gender_id", toInt(context.getParameters().get("genderId"), 1));
				String lastName = context.getParameter("lastName", "");
				if (!lastName.isEmpty()) body.put("last_name", lastName);
				String birthdate = context.getParameter("birthdate", "");
				if (!birthdate.isEmpty()) body.put("birthdate", birthdate);
				body.put("is_deceased", toBoolean(context.getParameters().get("isDeceased"), false));
				HttpResponse<String> response = post(baseUrl + "/contacts", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String id = context.getParameter("contactId", "");
				HttpResponse<String> response = delete(baseUrl + "/contacts/" + id, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String id = context.getParameter("contactId", "");
				HttpResponse<String> response = get(baseUrl + "/contacts/" + id, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(baseUrl + "/contacts", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String id = context.getParameter("contactId", "");
				HttpResponse<String> resp = get(baseUrl + "/contacts/" + id, headers);
				Map<String, Object> existing = parseResponse(resp);
				@SuppressWarnings("unchecked")
				Map<String, Object> data = (Map<String, Object>) existing.getOrDefault("data", existing);
				Map<String, Object> body = new LinkedHashMap<>(data);
				String firstName = context.getParameter("firstName", "");
				if (!firstName.isEmpty()) body.put("first_name", firstName);
				String lastName = context.getParameter("lastName", "");
				if (!lastName.isEmpty()) body.put("last_name", lastName);
				HttpResponse<String> response = put(baseUrl + "/contacts/" + id, body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown contact operation: " + operation);
		};
	}

	// ---- Contact Tag ----
	private Map<String, Object> handleContactTag(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		String contactId = context.getParameter("contactId", "");
		return switch (operation) {
			case "add" -> {
				String tags = context.getParameter("tags", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("tags", Arrays.stream(tags.split(",")).map(String::trim).toList());
				HttpResponse<String> response = post(baseUrl + "/contacts/" + contactId + "/setTags", body, headers);
				yield parseResponse(response);
			}
			case "remove" -> {
				String tagName = context.getParameter("tagName", "");
				Map<String, Object> body = Map.of("name", tagName);
				HttpResponse<String> response = post(baseUrl + "/contacts/" + contactId + "/unsetTag", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown contactTag operation: " + operation);
		};
	}

	// ---- Note ----
	private Map<String, Object> handleNote(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("body", context.getParameter("body", ""));
				body.put("contact_id", toInt(context.getParameters().get("contactId"), 0));
				body.put("is_favorited", toBoolean(context.getParameters().get("isFavorited"), false));
				HttpResponse<String> response = post(baseUrl + "/notes", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String id = context.getParameter("noteId", "");
				HttpResponse<String> response = delete(baseUrl + "/notes/" + id, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String id = context.getParameter("noteId", "");
				HttpResponse<String> response = get(baseUrl + "/notes/" + id, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(baseUrl + "/notes", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String id = context.getParameter("noteId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("body", context.getParameter("body", ""));
				body.put("contact_id", toInt(context.getParameters().get("contactId"), 0));
				HttpResponse<String> response = put(baseUrl + "/notes/" + id, body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown note operation: " + operation);
		};
	}

	// ---- Task ----
	private Map<String, Object> handleTask(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", context.getParameter("title", ""));
				body.put("contact_id", toInt(context.getParameters().get("contactId"), 0));
				HttpResponse<String> response = post(baseUrl + "/tasks", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String id = context.getParameter("taskId", "");
				HttpResponse<String> response = delete(baseUrl + "/tasks/" + id, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String id = context.getParameter("taskId", "");
				HttpResponse<String> response = get(baseUrl + "/tasks/" + id, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(baseUrl + "/tasks", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String id = context.getParameter("taskId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", context.getParameter("title", ""));
				body.put("contact_id", toInt(context.getParameters().get("contactId"), 0));
				HttpResponse<String> response = put(baseUrl + "/tasks/" + id, body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown task operation: " + operation);
		};
	}

	// ---- Tag ----
	private Map<String, Object> handleTag(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = Map.of("name", context.getParameter("name", ""));
				HttpResponse<String> response = post(baseUrl + "/tags", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String id = context.getParameter("tagId", "");
				HttpResponse<String> response = delete(baseUrl + "/tags/" + id, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String id = context.getParameter("tagId", "");
				HttpResponse<String> response = get(baseUrl + "/tags/" + id, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(baseUrl + "/tags", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String id = context.getParameter("tagId", "");
				Map<String, Object> body = Map.of("name", context.getParameter("name", ""));
				HttpResponse<String> response = put(baseUrl + "/tags/" + id, body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown tag operation: " + operation);
		};
	}

	// ---- Journal Entry ----
	private Map<String, Object> handleJournalEntry(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", context.getParameter("title", ""));
				body.put("post", context.getParameter("post", ""));
				HttpResponse<String> response = post(baseUrl + "/journal", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String id = context.getParameter("journalId", "");
				HttpResponse<String> response = delete(baseUrl + "/journal/" + id, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String id = context.getParameter("journalId", "");
				HttpResponse<String> response = get(baseUrl + "/journal/" + id, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(baseUrl + "/journal", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String id = context.getParameter("journalId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", context.getParameter("title", ""));
				body.put("post", context.getParameter("post", ""));
				HttpResponse<String> response = put(baseUrl + "/journal/" + id, body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown journalEntry operation: " + operation);
		};
	}

	// ---- Call ----
	private Map<String, Object> handleCall(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("content", context.getParameter("content", ""));
				body.put("contact_id", toInt(context.getParameters().get("contactId"), 0));
				body.put("called_at", context.getParameter("calledAt", ""));
				HttpResponse<String> response = post(baseUrl + "/calls", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String id = context.getParameter("callId", "");
				HttpResponse<String> response = delete(baseUrl + "/calls/" + id, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String id = context.getParameter("callId", "");
				HttpResponse<String> response = get(baseUrl + "/calls/" + id, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(baseUrl + "/calls", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String id = context.getParameter("callId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("content", context.getParameter("content", ""));
				body.put("contact_id", toInt(context.getParameters().get("contactId"), 0));
				body.put("called_at", context.getParameter("calledAt", ""));
				HttpResponse<String> response = put(baseUrl + "/calls/" + id, body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown call operation: " + operation);
		};
	}

	// ---- Reminder ----
	private Map<String, Object> handleReminder(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", context.getParameter("title", ""));
				body.put("contact_id", toInt(context.getParameters().get("contactId"), 0));
				body.put("initial_date", context.getParameter("initialDate", ""));
				body.put("frequency_type", context.getParameter("frequencyType", "one_time"));
				body.put("frequency_number", toInt(context.getParameters().get("frequencyNumber"), 1));
				HttpResponse<String> response = post(baseUrl + "/reminders", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String id = context.getParameter("reminderId", "");
				HttpResponse<String> response = delete(baseUrl + "/reminders/" + id, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String id = context.getParameter("reminderId", "");
				HttpResponse<String> response = get(baseUrl + "/reminders/" + id, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(baseUrl + "/reminders", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String id = context.getParameter("reminderId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", context.getParameter("title", ""));
				body.put("contact_id", toInt(context.getParameters().get("contactId"), 0));
				body.put("initial_date", context.getParameter("initialDate", ""));
				body.put("frequency_type", context.getParameter("frequencyType", "one_time"));
				HttpResponse<String> response = put(baseUrl + "/reminders/" + id, body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown reminder operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("contact")
						.options(List.of(
								ParameterOption.builder().name("Activity").value("activity").build(),
								ParameterOption.builder().name("Call").value("call").build(),
								ParameterOption.builder().name("Contact").value("contact").build(),
								ParameterOption.builder().name("Contact Tag").value("contactTag").build(),
								ParameterOption.builder().name("Journal Entry").value("journalEntry").build(),
								ParameterOption.builder().name("Note").value("note").build(),
								ParameterOption.builder().name("Reminder").value("reminder").build(),
								ParameterOption.builder().name("Tag").value("tag").build(),
								ParameterOption.builder().name("Task").value("task").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Update").value("update").build(),
								ParameterOption.builder().name("Add").value("add").build(),
								ParameterOption.builder().name("Remove").value("remove").build()
						)).build(),
				NodeParameter.builder()
						.name("contactId").displayName("Contact ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("ID of the contact.").build(),
				NodeParameter.builder()
						.name("firstName").displayName("First Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("First name of the contact.").build(),
				NodeParameter.builder()
						.name("lastName").displayName("Last Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Last name of the contact.").build(),
				NodeParameter.builder()
						.name("genderId").displayName("Gender ID")
						.type(ParameterType.NUMBER).defaultValue(1)
						.description("Gender ID for the contact.").build(),
				NodeParameter.builder()
						.name("birthdate").displayName("Birthdate")
						.type(ParameterType.STRING).defaultValue("")
						.description("Birthdate in YYYY-MM-DD format.").build(),
				NodeParameter.builder()
						.name("isDeceased").displayName("Is Deceased")
						.type(ParameterType.BOOLEAN).defaultValue(false).build(),
				NodeParameter.builder()
						.name("activityId").displayName("Activity ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("activityTypeId").displayName("Activity Type ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("summary").displayName("Summary")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("happenedAt").displayName("Happened At")
						.type(ParameterType.STRING).defaultValue("")
						.description("Date in YYYY-MM-DD format.").build(),
				NodeParameter.builder()
						.name("contacts").displayName("Contact IDs")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated contact IDs.").build(),
				NodeParameter.builder()
						.name("body").displayName("Body")
						.type(ParameterType.STRING).defaultValue("")
						.description("Body text for note or message.").build(),
				NodeParameter.builder()
						.name("isFavorited").displayName("Is Favorited")
						.type(ParameterType.BOOLEAN).defaultValue(false).build(),
				NodeParameter.builder()
						.name("noteId").displayName("Note ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("post").displayName("Post")
						.type(ParameterType.STRING).defaultValue("")
						.description("Journal entry content.").build(),
				NodeParameter.builder()
						.name("journalId").displayName("Journal Entry ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("taskId").displayName("Task ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("tagId").displayName("Tag ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("name").displayName("Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name for a tag.").build(),
				NodeParameter.builder()
						.name("tags").displayName("Tags")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated tag names.").build(),
				NodeParameter.builder()
						.name("tagName").displayName("Tag Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("callId").displayName("Call ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("content").displayName("Content")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("calledAt").displayName("Called At")
						.type(ParameterType.STRING).defaultValue("")
						.description("Date in YYYY-MM-DD format.").build(),
				NodeParameter.builder()
						.name("reminderId").displayName("Reminder ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("initialDate").displayName("Initial Date")
						.type(ParameterType.STRING).defaultValue("")
						.description("Date in YYYY-MM-DD format.").build(),
				NodeParameter.builder()
						.name("frequencyType").displayName("Frequency Type")
						.type(ParameterType.OPTIONS).defaultValue("one_time")
						.options(List.of(
								ParameterOption.builder().name("One Time").value("one_time").build(),
								ParameterOption.builder().name("Week").value("week").build(),
								ParameterOption.builder().name("Month").value("month").build(),
								ParameterOption.builder().name("Year").value("year").build()
						)).build(),
				NodeParameter.builder()
						.name("frequencyNumber").displayName("Frequency Number")
						.type(ParameterType.NUMBER).defaultValue(1).build()
		);
	}
}
