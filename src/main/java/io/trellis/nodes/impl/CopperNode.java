package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Copper — manage data in Copper CRM.
 */
@Node(
		type = "copper",
		displayName = "Copper",
		description = "Manage data in Copper CRM",
		category = "CRM & Sales",
		icon = "copper",
		credentials = {"copperApi"}
)
public class CopperNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.copper.com/developer_api/v1";

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
				.name("resource").displayName("Resource")
				.type(ParameterType.OPTIONS).required(true).defaultValue("person")
				.options(List.of(
						ParameterOption.builder().name("Company").value("company").description("Manage companies").build(),
						ParameterOption.builder().name("Customer Source").value("customerSource").description("Get customer sources").build(),
						ParameterOption.builder().name("Lead").value("lead").description("Manage leads").build(),
						ParameterOption.builder().name("Opportunity").value("opportunity").description("Manage opportunities").build(),
						ParameterOption.builder().name("Person").value("person").description("Manage persons").build(),
						ParameterOption.builder().name("Project").value("project").description("Manage projects").build(),
						ParameterOption.builder().name("Task").value("task").description("Manage tasks").build(),
						ParameterOption.builder().name("User").value("user").description("Get users").build()
				)).build());

		// Company operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("company"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").build(),
						ParameterOption.builder().name("Update").value("update").build(),
						ParameterOption.builder().name("Get").value("get").build(),
						ParameterOption.builder().name("Get All").value("getAll").build(),
						ParameterOption.builder().name("Delete").value("delete").build()
				)).build());

		// Customer Source operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("customerSource"))))
				.options(List.of(
						ParameterOption.builder().name("Get All").value("getAll").build()
				)).build());

		// Lead operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("lead"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").build(),
						ParameterOption.builder().name("Update").value("update").build(),
						ParameterOption.builder().name("Get").value("get").build(),
						ParameterOption.builder().name("Get All").value("getAll").build(),
						ParameterOption.builder().name("Delete").value("delete").build()
				)).build());

		// Opportunity operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("opportunity"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").build(),
						ParameterOption.builder().name("Update").value("update").build(),
						ParameterOption.builder().name("Get").value("get").build(),
						ParameterOption.builder().name("Get All").value("getAll").build(),
						ParameterOption.builder().name("Delete").value("delete").build()
				)).build());

		// Person operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("person"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").build(),
						ParameterOption.builder().name("Update").value("update").build(),
						ParameterOption.builder().name("Get").value("get").build(),
						ParameterOption.builder().name("Get All").value("getAll").build(),
						ParameterOption.builder().name("Delete").value("delete").build()
				)).build());

		// Project operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("project"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").build(),
						ParameterOption.builder().name("Update").value("update").build(),
						ParameterOption.builder().name("Get").value("get").build(),
						ParameterOption.builder().name("Get All").value("getAll").build(),
						ParameterOption.builder().name("Delete").value("delete").build()
				)).build());

		// Task operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("task"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").build(),
						ParameterOption.builder().name("Update").value("update").build(),
						ParameterOption.builder().name("Get").value("get").build(),
						ParameterOption.builder().name("Get All").value("getAll").build(),
						ParameterOption.builder().name("Delete").value("delete").build()
				)).build());

		// User operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("user"))))
				.options(List.of(
						ParameterOption.builder().name("Get All").value("getAll").build()
				)).build());

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
		String emailCred = context.getCredentialString("email", "");
		String apiKey = context.getCredentialString("apiKey", "");
		String resource = context.getParameter("resource", "person");
		String operation = context.getParameter("operation", "getAll");

		// Copper uses custom headers for auth
		Map<String, String> headers = new HashMap<>();
		headers.put("X-PW-AccessToken", apiKey);
		headers.put("X-PW-Application", "developer_api");
		headers.put("X-PW-UserEmail", emailCred);
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = executeResource(context, resource, operation, headers);
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
			String operation, Map<String, String> headers) throws Exception {

		String endpoint = getEndpoint(resource);

		switch (operation) {
			case "create": {
				Map<String, Object> body = buildBody(context, resource);
				HttpResponse<String> response = post(BASE_URL + "/" + endpoint, body, headers);
				return parseResponse(response);
			}
			case "update": {
				String id = context.getParameter("resourceId", "");
				Map<String, Object> body = buildBody(context, resource);
				HttpResponse<String> response = put(BASE_URL + "/" + endpoint + "/" + encode(id), body, headers);
				return parseResponse(response);
			}
			case "get": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(BASE_URL + "/" + endpoint + "/" + encode(id), headers);
				return parseResponse(response);
			}
			case "getAll": {
				// Copper uses POST for search/list operations
				boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
				int limit = toInt(context.getParameters().get("limit"), 100);
				Map<String, Object> searchBody = new LinkedHashMap<>();
				searchBody.put("page_size", returnAll ? 200 : Math.min(limit, 200));
				searchBody.put("page_number", 1);

				String searchEndpoint = getSearchEndpoint(resource);
				HttpResponse<String> response = post(BASE_URL + "/" + searchEndpoint, searchBody, headers);
				return parseResponse(response);
			}
			case "delete": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(BASE_URL + "/" + endpoint + "/" + encode(id), headers);
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
			case "company" -> "companies";
			case "customerSource" -> "customer_sources";
			case "lead" -> "leads";
			case "opportunity" -> "opportunities";
			case "person" -> "people";
			case "project" -> "projects";
			case "task" -> "tasks";
			case "user" -> "users";
			default -> resource;
		};
	}

	private String getSearchEndpoint(String resource) {
		return switch (resource) {
			case "company" -> "companies/search";
			case "customerSource" -> "customer_sources";
			case "lead" -> "leads/search";
			case "opportunity" -> "opportunities/search";
			case "person" -> "people/search";
			case "project" -> "projects/search";
			case "task" -> "tasks/search";
			case "user" -> "users/search";
			default -> resource + "/search";
		};
	}

	private Map<String, Object> buildBody(NodeExecutionContext context, String resource) {
		String name = context.getParameter("name", "");
		String email = context.getParameter("email", "");
		String additionalJson = context.getParameter("additionalFields", "{}");

		Map<String, Object> body = new LinkedHashMap<>();
		if (!name.isEmpty()) body.put("name", name);
		if (!email.isEmpty()) {
			if ("person".equals(resource) || "lead".equals(resource)) {
				body.put("emails", List.of(Map.of("email", email, "category", "work")));
			} else {
				body.put("email", email);
			}
		}
		body.putAll(parseJson(additionalJson));
		return body;
	}
}
