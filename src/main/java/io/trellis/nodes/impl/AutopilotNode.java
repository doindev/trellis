package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Autopilot — manage contacts and lists for marketing automation via the Autopilot API.
 */
@Node(
		type = "autopilot",
		displayName = "Autopilot",
		description = "Manage contacts and lists in Autopilot",
		category = "Marketing",
		icon = "autopilot",
		credentials = {"autopilotApi"},
		searchOnly = true
)
public class AutopilotNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api2.autopilothq.com/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = (String) credentials.getOrDefault("apiKey", "");

		String resource = context.getParameter("resource", "contact");
		String operation = context.getParameter("operation", "upsert");

		Map<String, String> headers = new HashMap<>();
		headers.put("autopilotapikey", apiKey);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "contact" -> handleContact(context, headers, operation);
					case "contactJourney" -> handleContactJourney(context, headers);
					case "contactList" -> handleContactList(context, headers, operation);
					case "list" -> handleList(context, headers, operation);
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

	private Map<String, Object> handleContact(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "upsert" -> {
				Map<String, Object> contact = new LinkedHashMap<>();
				contact.put("Email", context.getParameter("email", ""));
				String firstName = context.getParameter("firstName", "");
				if (!firstName.isEmpty()) contact.put("FirstName", firstName);
				String lastName = context.getParameter("lastName", "");
				if (!lastName.isEmpty()) contact.put("LastName", lastName);
				String company = context.getParameter("company", "");
				if (!company.isEmpty()) contact.put("Company", company);
				String phone = context.getParameter("phone", "");
				if (!phone.isEmpty()) contact.put("Phone", phone);
				Map<String, Object> body = Map.of("contact", contact);
				HttpResponse<String> response = post(BASE_URL + "/contact", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String contactId = context.getParameter("contactId", "");
				HttpResponse<String> response = delete(BASE_URL + "/contact/" + encode(contactId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String contactId = context.getParameter("contactId", "");
				HttpResponse<String> response = get(BASE_URL + "/contact/" + encode(contactId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				HttpResponse<String> response = get(BASE_URL + "/contacts?limit=" + limit, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown contact operation: " + operation);
		};
	}

	private Map<String, Object> handleContactJourney(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String triggerId = context.getParameter("triggerId", "");
		String contactId = context.getParameter("contactId", "");
		HttpResponse<String> response = post(BASE_URL + "/trigger/" + encode(triggerId) + "/contact/" + encode(contactId), Map.of(), headers);
		return parseResponse(response);
	}

	private Map<String, Object> handleContactList(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		String listId = context.getParameter("listId", "");
		String contactId = context.getParameter("contactId", "");
		return switch (operation) {
			case "add" -> {
				HttpResponse<String> response = post(BASE_URL + "/list/" + encode(listId) + "/contact/" + encode(contactId), Map.of(), headers);
				yield parseResponse(response);
			}
			case "remove" -> {
				HttpResponse<String> response = delete(BASE_URL + "/list/" + encode(listId) + "/contact/" + encode(contactId), headers);
				yield parseResponse(response);
			}
			case "exist" -> {
				HttpResponse<String> response = get(BASE_URL + "/list/" + encode(listId) + "/contact/" + encode(contactId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				HttpResponse<String> response = get(BASE_URL + "/list/" + encode(listId) + "/contacts?limit=" + limit, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown contact list operation: " + operation);
		};
	}

	private Map<String, Object> handleList(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				String name = context.getParameter("listName", "");
				Map<String, Object> body = Map.of("name", name);
				HttpResponse<String> response = post(BASE_URL + "/list", body, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/lists", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown list operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("contact")
						.options(List.of(
								ParameterOption.builder().name("Contact").value("contact").build(),
								ParameterOption.builder().name("Contact Journey").value("contactJourney").build(),
								ParameterOption.builder().name("Contact List").value("contactList").build(),
								ParameterOption.builder().name("List").value("list").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("upsert")
						.options(List.of(
								ParameterOption.builder().name("Upsert").value("upsert").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Add").value("add").build(),
								ParameterOption.builder().name("Remove").value("remove").build(),
								ParameterOption.builder().name("Check Existence").value("exist").build(),
								ParameterOption.builder().name("Create").value("create").build()
						)).build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("contactId").displayName("Contact ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Contact ID or email address.").build(),
				NodeParameter.builder()
						.name("firstName").displayName("First Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("lastName").displayName("Last Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("company").displayName("Company")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("phone").displayName("Phone")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("listId").displayName("List ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("listName").displayName("List Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("triggerId").displayName("Trigger ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Journey trigger ID.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max results to return (1-500).").build()
		);
	}
}
