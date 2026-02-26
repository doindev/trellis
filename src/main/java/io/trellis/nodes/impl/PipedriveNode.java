package io.trellis.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Pipedrive Node -- manage deals, persons, organizations, activities,
 * notes, and leads in Pipedrive CRM.
 */
@Slf4j
@Node(
	type = "pipedrive",
	displayName = "Pipedrive",
	description = "Manage deals, persons, organizations, activities, notes, and leads in Pipedrive CRM",
	category = "CRM",
	icon = "pipedrive",
	credentials = {"pipedriveApi"}
)
public class PipedriveNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.pipedrive.com/v1";

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
		List<NodeParameter> params = new ArrayList<>();

		// Resource selector
		params.add(NodeParameter.builder()
			.name("resource").displayName("Resource")
			.type(ParameterType.OPTIONS).required(true).defaultValue("deal")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Deal").value("deal").description("Manage deals").build(),
				ParameterOption.builder().name("Person").value("person").description("Manage persons").build(),
				ParameterOption.builder().name("Organization").value("organization").description("Manage organizations").build(),
				ParameterOption.builder().name("Activity").value("activity").description("Manage activities").build(),
				ParameterOption.builder().name("Note").value("note").description("Manage notes").build(),
				ParameterOption.builder().name("Lead").value("lead").description("Manage leads").build()
			)).build());

		// Deal operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("deal"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a deal").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a deal").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a deal").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many deals").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a deal").build()
			)).build());

		// Person operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("person"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a person").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a person").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a person").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many persons").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a person").build()
			)).build());

		// Organization operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("organization"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an organization").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an organization").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an organization").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many organizations").build(),
				ParameterOption.builder().name("Update").value("update").description("Update an organization").build()
			)).build());

		// Activity operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("activity"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an activity").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an activity").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an activity").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many activities").build(),
				ParameterOption.builder().name("Update").value("update").description("Update an activity").build()
			)).build());

		// Note operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("note"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a note").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a note").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a note").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many notes").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a note").build()
			)).build());

		// Lead operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("lead"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a lead").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a lead").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a lead").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many leads").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a lead").build()
			)).build());

		// Common parameters
		params.add(NodeParameter.builder()
			.name("resourceId").displayName("ID")
			.type(ParameterType.STRING).required(true)
			.description("The ID of the resource.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("get", "update", "delete"))))
			.build());

		params.add(NodeParameter.builder()
			.name("title").displayName("Title")
			.type(ParameterType.STRING).required(true)
			.description("Title of the resource.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("name").displayName("Name")
			.type(ParameterType.STRING)
			.description("Name of the person or organization.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("person", "organization"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("email").displayName("Email")
			.type(ParameterType.STRING)
			.description("Email address of the person.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("person"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("phone").displayName("Phone")
			.type(ParameterType.STRING)
			.description("Phone number of the person.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("person"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("content").displayName("Content")
			.type(ParameterType.STRING)
			.description("Content of the note.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("note"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("dealId").displayName("Deal ID")
			.type(ParameterType.STRING)
			.description("Associated deal ID.")
			.build());

		params.add(NodeParameter.builder()
			.name("personId").displayName("Person ID")
			.type(ParameterType.STRING)
			.description("Associated person ID.")
			.build());

		params.add(NodeParameter.builder()
			.name("orgId").displayName("Organization ID")
			.type(ParameterType.STRING)
			.description("Associated organization ID.")
			.build());

		params.add(NodeParameter.builder()
			.name("additionalFields").displayName("Additional Fields (JSON)")
			.type(ParameterType.JSON).defaultValue("{}")
			.description("Additional fields as JSON.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("returnAll").displayName("Return All")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Whether to return all results.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit")
			.type(ParameterType.NUMBER).defaultValue(100)
			.description("Max number of results to return.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "deal");
		String operation = context.getParameter("operation", "getAll");
		Map<String, Object> credentials = context.getCredentials();
		String apiToken = String.valueOf(credentials.getOrDefault("apiToken", credentials.getOrDefault("apiKey", "")));

		try {
			return switch (resource) {
				case "deal" -> executeDeal(context, operation, apiToken);
				case "person" -> executePerson(context, operation, apiToken);
				case "organization" -> executeOrganization(context, operation, apiToken);
				case "activity" -> executeActivity(context, operation, apiToken);
				case "note" -> executeNote(context, operation, apiToken);
				case "lead" -> executeLead(context, operation, apiToken);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Pipedrive API error: " + e.getMessage(), e);
		}
	}

	// ========================= Deal Operations =========================

	private NodeExecutionResult executeDeal(NodeExecutionContext context, String operation, String apiToken) throws Exception {
		switch (operation) {
			case "create": {
				String title = context.getParameter("title", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				body.put("title", title);
				putIfNotEmpty(body, "person_id", context.getParameter("personId", ""));
				putIfNotEmpty(body, "org_id", context.getParameter("orgId", ""));
				HttpResponse<String> response = post(apiUrl("/deals", apiToken), body, jsonHeaders());
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(apiUrl("/deals/" + encode(id), apiToken), jsonHeaders());
				return toDeleteResult(response, id);
			}
			case "get": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(apiUrl("/deals/" + encode(id), apiToken), jsonHeaders());
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(apiUrl("/deals", apiToken) + "&limit=" + limit + "&start=0", jsonHeaders());
				return toResult(response);
			}
			case "update": {
				String id = context.getParameter("resourceId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				putIfNotEmpty(body, "title", context.getParameter("title", ""));
				HttpResponse<String> response = put(apiUrl("/deals/" + encode(id), apiToken), body, jsonHeaders());
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown deal operation: " + operation);
		}
	}

	// ========================= Person Operations =========================

	private NodeExecutionResult executePerson(NodeExecutionContext context, String operation, String apiToken) throws Exception {
		switch (operation) {
			case "create": {
				String name = context.getParameter("name", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				body.put("name", name);
				putIfNotEmpty(body, "email", context.getParameter("email", ""));
				putIfNotEmpty(body, "phone", context.getParameter("phone", ""));
				putIfNotEmpty(body, "org_id", context.getParameter("orgId", ""));
				HttpResponse<String> response = post(apiUrl("/persons", apiToken), body, jsonHeaders());
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(apiUrl("/persons/" + encode(id), apiToken), jsonHeaders());
				return toDeleteResult(response, id);
			}
			case "get": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(apiUrl("/persons/" + encode(id), apiToken), jsonHeaders());
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(apiUrl("/persons", apiToken) + "&limit=" + limit + "&start=0", jsonHeaders());
				return toResult(response);
			}
			case "update": {
				String id = context.getParameter("resourceId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				putIfNotEmpty(body, "name", context.getParameter("name", ""));
				putIfNotEmpty(body, "email", context.getParameter("email", ""));
				putIfNotEmpty(body, "phone", context.getParameter("phone", ""));
				HttpResponse<String> response = put(apiUrl("/persons/" + encode(id), apiToken), body, jsonHeaders());
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown person operation: " + operation);
		}
	}

	// ========================= Organization Operations =========================

	private NodeExecutionResult executeOrganization(NodeExecutionContext context, String operation, String apiToken) throws Exception {
		switch (operation) {
			case "create": {
				String name = context.getParameter("name", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				body.put("name", name);
				HttpResponse<String> response = post(apiUrl("/organizations", apiToken), body, jsonHeaders());
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(apiUrl("/organizations/" + encode(id), apiToken), jsonHeaders());
				return toDeleteResult(response, id);
			}
			case "get": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(apiUrl("/organizations/" + encode(id), apiToken), jsonHeaders());
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(apiUrl("/organizations", apiToken) + "&limit=" + limit + "&start=0", jsonHeaders());
				return toResult(response);
			}
			case "update": {
				String id = context.getParameter("resourceId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				putIfNotEmpty(body, "name", context.getParameter("name", ""));
				HttpResponse<String> response = put(apiUrl("/organizations/" + encode(id), apiToken), body, jsonHeaders());
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown organization operation: " + operation);
		}
	}

	// ========================= Activity Operations =========================

	private NodeExecutionResult executeActivity(NodeExecutionContext context, String operation, String apiToken) throws Exception {
		switch (operation) {
			case "create": {
				String title = context.getParameter("title", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				body.put("subject", title);
				putIfNotEmpty(body, "deal_id", context.getParameter("dealId", ""));
				putIfNotEmpty(body, "person_id", context.getParameter("personId", ""));
				putIfNotEmpty(body, "org_id", context.getParameter("orgId", ""));
				HttpResponse<String> response = post(apiUrl("/activities", apiToken), body, jsonHeaders());
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(apiUrl("/activities/" + encode(id), apiToken), jsonHeaders());
				return toDeleteResult(response, id);
			}
			case "get": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(apiUrl("/activities/" + encode(id), apiToken), jsonHeaders());
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(apiUrl("/activities", apiToken) + "&limit=" + limit + "&start=0", jsonHeaders());
				return toResult(response);
			}
			case "update": {
				String id = context.getParameter("resourceId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				putIfNotEmpty(body, "subject", context.getParameter("title", ""));
				HttpResponse<String> response = put(apiUrl("/activities/" + encode(id), apiToken), body, jsonHeaders());
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown activity operation: " + operation);
		}
	}

	// ========================= Note Operations =========================

	private NodeExecutionResult executeNote(NodeExecutionContext context, String operation, String apiToken) throws Exception {
		switch (operation) {
			case "create": {
				String content = context.getParameter("content", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				body.put("content", content);
				putIfNotEmpty(body, "deal_id", context.getParameter("dealId", ""));
				putIfNotEmpty(body, "person_id", context.getParameter("personId", ""));
				putIfNotEmpty(body, "org_id", context.getParameter("orgId", ""));
				HttpResponse<String> response = post(apiUrl("/notes", apiToken), body, jsonHeaders());
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(apiUrl("/notes/" + encode(id), apiToken), jsonHeaders());
				return toDeleteResult(response, id);
			}
			case "get": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(apiUrl("/notes/" + encode(id), apiToken), jsonHeaders());
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(apiUrl("/notes", apiToken) + "&limit=" + limit + "&start=0", jsonHeaders());
				return toResult(response);
			}
			case "update": {
				String id = context.getParameter("resourceId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				putIfNotEmpty(body, "content", context.getParameter("content", ""));
				HttpResponse<String> response = put(apiUrl("/notes/" + encode(id), apiToken), body, jsonHeaders());
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown note operation: " + operation);
		}
	}

	// ========================= Lead Operations =========================

	private NodeExecutionResult executeLead(NodeExecutionContext context, String operation, String apiToken) throws Exception {
		switch (operation) {
			case "create": {
				String title = context.getParameter("title", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				body.put("title", title);
				putIfNotEmpty(body, "person_id", context.getParameter("personId", ""));
				putIfNotEmpty(body, "organization_id", context.getParameter("orgId", ""));
				HttpResponse<String> response = post(apiUrl("/leads", apiToken), body, jsonHeaders());
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(apiUrl("/leads/" + encode(id), apiToken), jsonHeaders());
				return toDeleteResult(response, id);
			}
			case "get": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(apiUrl("/leads/" + encode(id), apiToken), jsonHeaders());
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(apiUrl("/leads", apiToken) + "&limit=" + limit + "&start=0", jsonHeaders());
				return toResult(response);
			}
			case "update": {
				String id = context.getParameter("resourceId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				putIfNotEmpty(body, "title", context.getParameter("title", ""));
				HttpResponse<String> response = patch(apiUrl("/leads/" + encode(id), apiToken), body, jsonHeaders());
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown lead operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private String apiUrl(String path, String apiToken) {
		return BASE_URL + path + "?api_token=" + encode(apiToken);
	}

	private Map<String, String> jsonHeaders() {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}

	private int getLimit(NodeExecutionContext context) {
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 100);
		return returnAll ? 500 : Math.min(limit, 500);
	}

	private void putIfNotEmpty(Map<String, Object> map, String key, String value) {
		if (value != null && !value.isEmpty()) {
			map.put(key, value);
		}
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return pipedriveError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		Object data = parsed.get("data");
		if (data instanceof List) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Object item : (List<?>) data) {
				if (item instanceof Map) {
					items.add(wrapInJson(item));
				}
			}
			return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
		} else if (data instanceof Map) {
			return NodeExecutionResult.success(List.of(wrapInJson(data)));
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response, String id) throws Exception {
		if (response.statusCode() >= 400) {
			return pipedriveError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "id", id))));
	}

	private NodeExecutionResult pipedriveError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Pipedrive API error (HTTP " + response.statusCode() + "): " + body);
	}
}
