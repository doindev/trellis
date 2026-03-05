package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Affinity — manage relationships in Affinity CRM.
 */
@Node(
		type = "affinity",
		displayName = "Affinity",
		description = "Manage relationships in Affinity CRM",
		category = "CRM",
		icon = "affinity",
		credentials = {"affinityApi"}
)
public class AffinityNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.affinity.co";

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
				.name("resource").displayName("Resource")
				.type(ParameterType.OPTIONS).required(true).defaultValue("person")
				.options(List.of(
						ParameterOption.builder().name("List").value("list").description("Manage lists").build(),
						ParameterOption.builder().name("List Entry").value("listEntry").description("Manage list entries").build(),
						ParameterOption.builder().name("Person").value("person").description("Manage persons").build(),
						ParameterOption.builder().name("Organization").value("organization").description("Manage organizations").build()
				)).build());

		// List operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("list"))))
				.options(List.of(
						ParameterOption.builder().name("Get").value("get").description("Get a list by ID").build(),
						ParameterOption.builder().name("Get All").value("getAll").description("Get all lists").build()
				)).build());

		// List Entry operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("listEntry"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").description("Create a list entry").build(),
						ParameterOption.builder().name("Get").value("get").description("Get a list entry").build(),
						ParameterOption.builder().name("Get All").value("getAll").description("Get all list entries").build(),
						ParameterOption.builder().name("Delete").value("delete").description("Delete a list entry").build()
				)).build());

		// Person operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("person"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").description("Create a person").build(),
						ParameterOption.builder().name("Update").value("update").description("Update a person").build(),
						ParameterOption.builder().name("Get").value("get").description("Get a person").build(),
						ParameterOption.builder().name("Get All").value("getAll").description("Get all persons").build(),
						ParameterOption.builder().name("Delete").value("delete").description("Delete a person").build()
				)).build());

		// Organization operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("organization"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").description("Create an organization").build(),
						ParameterOption.builder().name("Update").value("update").description("Update an organization").build(),
						ParameterOption.builder().name("Get").value("get").description("Get an organization").build(),
						ParameterOption.builder().name("Get All").value("getAll").description("Get all organizations").build(),
						ParameterOption.builder().name("Delete").value("delete").description("Delete an organization").build()
				)).build());

		// Common parameters
		params.add(NodeParameter.builder()
				.name("listId").displayName("List ID")
				.type(ParameterType.STRING).defaultValue("")
				.description("The ID of the list.").build());

		params.add(NodeParameter.builder()
				.name("entryId").displayName("Entry ID")
				.type(ParameterType.STRING).defaultValue("")
				.description("The ID of the list entry.").build());

		params.add(NodeParameter.builder()
				.name("personId").displayName("Person ID")
				.type(ParameterType.STRING).defaultValue("")
				.description("The ID of the person.").build());

		params.add(NodeParameter.builder()
				.name("organizationId").displayName("Organization ID")
				.type(ParameterType.STRING).defaultValue("")
				.description("The ID of the organization.").build());

		params.add(NodeParameter.builder()
				.name("entityId").displayName("Entity ID")
				.type(ParameterType.STRING).defaultValue("")
				.description("The ID of the entity to add to the list.").build());

		params.add(NodeParameter.builder()
				.name("firstName").displayName("First Name")
				.type(ParameterType.STRING).defaultValue("")
				.description("First name of the person.").build());

		params.add(NodeParameter.builder()
				.name("lastName").displayName("Last Name")
				.type(ParameterType.STRING).defaultValue("")
				.description("Last name of the person.").build());

		params.add(NodeParameter.builder()
				.name("emails").displayName("Emails")
				.type(ParameterType.STRING).defaultValue("")
				.description("Comma-separated email addresses.").build());

		params.add(NodeParameter.builder()
				.name("organizationName").displayName("Organization Name")
				.type(ParameterType.STRING).defaultValue("")
				.description("Name of the organization.").build());

		params.add(NodeParameter.builder()
				.name("domain").displayName("Domain")
				.type(ParameterType.STRING).defaultValue("")
				.description("Domain of the organization.").build());

		params.add(NodeParameter.builder()
				.name("additionalFields").displayName("Additional Fields (JSON)")
				.type(ParameterType.JSON).defaultValue("{}")
				.description("Additional fields as JSON.").build());

		params.add(NodeParameter.builder()
				.name("returnAll").displayName("Return All")
				.type(ParameterType.BOOLEAN).defaultValue(false)
				.description("Whether to return all results.").build());

		params.add(NodeParameter.builder()
				.name("limit").displayName("Limit")
				.type(ParameterType.NUMBER).defaultValue(100)
				.description("Max number of results to return.").build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");
		String resource = context.getParameter("resource", "person");
		String operation = context.getParameter("operation", "getAll");

		// Affinity uses Basic Auth with empty username and API key as password
		String auth = Base64.getEncoder().encodeToString((":" + apiKey).getBytes());
		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Basic " + auth);
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "list" -> executeList(context, operation, headers);
					case "listEntry" -> executeListEntry(context, operation, headers);
					case "person" -> executePerson(context, operation, headers);
					case "organization" -> executeOrganization(context, operation, headers);
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

	private Map<String, Object> executeList(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "get": {
				String listId = context.getParameter("listId", "");
				HttpResponse<String> response = get(BASE_URL + "/lists/" + encode(listId), headers);
				return parseResponse(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(BASE_URL + "/lists", headers);
				return parseResponse(response);
			}
			default:
				throw new IllegalArgumentException("Unknown list operation: " + operation);
		}
	}

	private Map<String, Object> executeListEntry(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String listId = context.getParameter("listId", "");

		switch (operation) {
			case "create": {
				String entityId = context.getParameter("entityId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("entity_id", Long.parseLong(entityId));
				HttpResponse<String> response = post(BASE_URL + "/lists/" + encode(listId) + "/list-entries", body, headers);
				return parseResponse(response);
			}
			case "get": {
				String entryId = context.getParameter("entryId", "");
				HttpResponse<String> response = get(BASE_URL + "/lists/" + encode(listId) + "/list-entries/" + encode(entryId), headers);
				return parseResponse(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(BASE_URL + "/lists/" + encode(listId) + "/list-entries", headers);
				return parseResponse(response);
			}
			case "delete": {
				String entryId = context.getParameter("entryId", "");
				HttpResponse<String> response = delete(BASE_URL + "/lists/" + encode(listId) + "/list-entries/" + encode(entryId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("entryId", entryId);
				return result;
			}
			default:
				throw new IllegalArgumentException("Unknown list entry operation: " + operation);
		}
	}

	private Map<String, Object> executePerson(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String firstName = context.getParameter("firstName", "");
				String lastName = context.getParameter("lastName", "");
				String emails = context.getParameter("emails", "");
				String additionalJson = context.getParameter("additionalFields", "{}");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("first_name", firstName);
				body.put("last_name", lastName);
				if (!emails.isEmpty()) {
					body.put("emails", Arrays.asList(emails.split(",")));
				}
				body.putAll(parseJson(additionalJson));

				HttpResponse<String> response = post(BASE_URL + "/persons", body, headers);
				return parseResponse(response);
			}
			case "update": {
				String personId = context.getParameter("personId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");

				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				String firstName = context.getParameter("firstName", "");
				String lastName = context.getParameter("lastName", "");
				String emails = context.getParameter("emails", "");
				if (!firstName.isEmpty()) body.put("first_name", firstName);
				if (!lastName.isEmpty()) body.put("last_name", lastName);
				if (!emails.isEmpty()) body.put("emails", Arrays.asList(emails.split(",")));

				HttpResponse<String> response = put(BASE_URL + "/persons/" + encode(personId), body, headers);
				return parseResponse(response);
			}
			case "get": {
				String personId = context.getParameter("personId", "");
				HttpResponse<String> response = get(BASE_URL + "/persons/" + encode(personId), headers);
				return parseResponse(response);
			}
			case "getAll": {
				boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
				int limit = toInt(context.getParameters().get("limit"), 100);
				String url = BASE_URL + "/persons?page_size=" + (returnAll ? 500 : Math.min(limit, 500));
				HttpResponse<String> response = get(url, headers);
				return parseResponse(response);
			}
			case "delete": {
				String personId = context.getParameter("personId", "");
				HttpResponse<String> response = delete(BASE_URL + "/persons/" + encode(personId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("personId", personId);
				return result;
			}
			default:
				throw new IllegalArgumentException("Unknown person operation: " + operation);
		}
	}

	private Map<String, Object> executeOrganization(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String name = context.getParameter("organizationName", "");
				String domain = context.getParameter("domain", "");
				String additionalJson = context.getParameter("additionalFields", "{}");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", name);
				if (!domain.isEmpty()) body.put("domain", domain);
				body.putAll(parseJson(additionalJson));

				HttpResponse<String> response = post(BASE_URL + "/organizations", body, headers);
				return parseResponse(response);
			}
			case "update": {
				String orgId = context.getParameter("organizationId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");

				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				String name = context.getParameter("organizationName", "");
				String domain = context.getParameter("domain", "");
				if (!name.isEmpty()) body.put("name", name);
				if (!domain.isEmpty()) body.put("domain", domain);

				HttpResponse<String> response = put(BASE_URL + "/organizations/" + encode(orgId), body, headers);
				return parseResponse(response);
			}
			case "get": {
				String orgId = context.getParameter("organizationId", "");
				HttpResponse<String> response = get(BASE_URL + "/organizations/" + encode(orgId), headers);
				return parseResponse(response);
			}
			case "getAll": {
				boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
				int limit = toInt(context.getParameters().get("limit"), 100);
				String url = BASE_URL + "/organizations?page_size=" + (returnAll ? 500 : Math.min(limit, 500));
				HttpResponse<String> response = get(url, headers);
				return parseResponse(response);
			}
			case "delete": {
				String orgId = context.getParameter("organizationId", "");
				HttpResponse<String> response = delete(BASE_URL + "/organizations/" + encode(orgId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("organizationId", orgId);
				return result;
			}
			default:
				throw new IllegalArgumentException("Unknown organization operation: " + operation);
		}
	}
}
