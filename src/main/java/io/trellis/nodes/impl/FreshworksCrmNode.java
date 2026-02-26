package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Freshworks CRM — manage data in Freshworks CRM (Freshsales).
 */
@Node(
		type = "freshworksCrm",
		displayName = "Freshworks CRM",
		description = "Manage data in Freshworks CRM",
		category = "CRM & Sales",
		icon = "freshworksCrm",
		credentials = {"freshworksCrmApi"}
)
public class FreshworksCrmNode extends AbstractApiNode {

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
				.name("resource").displayName("Resource")
				.type(ParameterType.OPTIONS).required(true).defaultValue("contact")
				.options(List.of(
						ParameterOption.builder().name("Account").value("account").description("Manage accounts").build(),
						ParameterOption.builder().name("Appointment").value("appointment").description("Manage appointments").build(),
						ParameterOption.builder().name("Contact").value("contact").description("Manage contacts").build(),
						ParameterOption.builder().name("Deal").value("deal").description("Manage deals").build(),
						ParameterOption.builder().name("Note").value("note").description("Manage notes").build(),
						ParameterOption.builder().name("Sales Activity").value("salesActivity").description("Manage sales activities").build(),
						ParameterOption.builder().name("Task").value("task").description("Manage tasks").build()
				)).build());

		// Operations for all resources (same CRUD set)
		for (String res : List.of("account", "appointment", "contact", "deal", "note", "salesActivity", "task")) {
			params.add(NodeParameter.builder()
					.name("operation").displayName("Operation")
					.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
					.displayOptions(Map.of("show", Map.of("resource", List.of(res))))
					.options(List.of(
							ParameterOption.builder().name("Create").value("create").build(),
							ParameterOption.builder().name("Update").value("update").build(),
							ParameterOption.builder().name("Get").value("get").build(),
							ParameterOption.builder().name("Get All").value("getAll").build(),
							ParameterOption.builder().name("Delete").value("delete").build()
					)).build());
		}

		// Common parameters
		params.add(NodeParameter.builder()
				.name("resourceId").displayName("ID")
				.type(ParameterType.STRING).defaultValue("")
				.description("The ID of the resource.").build());

		params.add(NodeParameter.builder()
				.name("name").displayName("Name")
				.type(ParameterType.STRING).defaultValue("")
				.description("Name of the resource.").build());

		params.add(NodeParameter.builder()
				.name("email").displayName("Email")
				.type(ParameterType.STRING).defaultValue("")
				.description("Email address.").build());

		params.add(NodeParameter.builder()
				.name("firstName").displayName("First Name")
				.type(ParameterType.STRING).defaultValue("")
				.description("First name.").build());

		params.add(NodeParameter.builder()
				.name("lastName").displayName("Last Name")
				.type(ParameterType.STRING).defaultValue("")
				.description("Last name.").build());

		params.add(NodeParameter.builder()
				.name("title").displayName("Title")
				.type(ParameterType.STRING).defaultValue("")
				.description("Title or subject.").build());

		params.add(NodeParameter.builder()
				.name("description").displayName("Description")
				.type(ParameterType.STRING).defaultValue("")
				.description("Description or body.").build());

		params.add(NodeParameter.builder()
				.name("amount").displayName("Amount")
				.type(ParameterType.NUMBER).defaultValue(0)
				.description("Deal amount.").build());

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
		String domain = context.getCredentialString("domain", "");
		String apiKey = context.getCredentialString("apiKey", "");
		String resource = context.getParameter("resource", "contact");
		String operation = context.getParameter("operation", "getAll");

		String baseUrl = "https://" + domain + ".myfreshworks.com/crm/sales/api";

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Token token=" + apiKey);
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = executeResource(context, resource, operation, baseUrl, headers);
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

	private Map<String, Object> executeResource(NodeExecutionContext context, String resource,
			String operation, String baseUrl, Map<String, String> headers) throws Exception {

		String endpoint = getEndpoint(resource);
		String resourceKey = getResourceKey(resource);

		switch (operation) {
			case "create": {
				Map<String, Object> fields = buildBody(context, resource);
				Map<String, Object> body = Map.of(resourceKey, fields);
				HttpResponse<String> response = post(baseUrl + "/" + endpoint, body, headers);
				return parseResponse(response);
			}
			case "update": {
				String id = context.getParameter("resourceId", "");
				Map<String, Object> fields = buildBody(context, resource);
				Map<String, Object> body = Map.of(resourceKey, fields);
				HttpResponse<String> response = put(baseUrl + "/" + endpoint + "/" + encode(id), body, headers);
				return parseResponse(response);
			}
			case "get": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(baseUrl + "/" + endpoint + "/" + encode(id), headers);
				return parseResponse(response);
			}
			case "getAll": {
				boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
				int limit = toInt(context.getParameters().get("limit"), 100);
				String url = baseUrl + "/" + endpoint + "?per_page=" + (returnAll ? 100 : Math.min(limit, 100));
				HttpResponse<String> response = get(url, headers);
				return parseResponse(response);
			}
			case "delete": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(baseUrl + "/" + endpoint + "/" + encode(id), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("id", id);
				return result;
			}
			default:
				throw new IllegalArgumentException("Unknown operation: " + operation);
		}
	}

	private String getEndpoint(String resource) {
		return switch (resource) {
			case "account" -> "sales_accounts";
			case "appointment" -> "appointments";
			case "contact" -> "contacts";
			case "deal" -> "deals";
			case "note" -> "notes";
			case "salesActivity" -> "sales_activities";
			case "task" -> "tasks";
			default -> resource;
		};
	}

	private String getResourceKey(String resource) {
		return switch (resource) {
			case "account" -> "sales_account";
			case "appointment" -> "appointment";
			case "contact" -> "contact";
			case "deal" -> "deal";
			case "note" -> "note";
			case "salesActivity" -> "sales_activity";
			case "task" -> "task";
			default -> resource;
		};
	}

	private Map<String, Object> buildBody(NodeExecutionContext context, String resource) {
		String additionalJson = context.getParameter("additionalFields", "{}");
		Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));

		switch (resource) {
			case "contact" -> {
				String firstName = context.getParameter("firstName", "");
				String lastName = context.getParameter("lastName", "");
				String email = context.getParameter("email", "");
				if (!firstName.isEmpty()) body.put("first_name", firstName);
				if (!lastName.isEmpty()) body.put("last_name", lastName);
				if (!email.isEmpty()) body.put("emails", email);
			}
			case "account" -> {
				String name = context.getParameter("name", "");
				if (!name.isEmpty()) body.put("name", name);
			}
			case "deal" -> {
				String name = context.getParameter("name", "");
				double amount = toDouble(context.getParameters().get("amount"), 0);
				if (!name.isEmpty()) body.put("name", name);
				if (amount > 0) body.put("amount", amount);
			}
			case "appointment", "task" -> {
				String title = context.getParameter("title", "");
				String description = context.getParameter("description", "");
				if (!title.isEmpty()) body.put("title", title);
				if (!description.isEmpty()) body.put("description", description);
			}
			case "note" -> {
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) body.put("description", description);
			}
			case "salesActivity" -> {
				String title = context.getParameter("title", "");
				String description = context.getParameter("description", "");
				if (!title.isEmpty()) body.put("title", title);
				if (!description.isEmpty()) body.put("notes", description);
			}
		}

		return body;
	}
}
