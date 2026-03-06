package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * GetResponse — manage contacts via the GetResponse API.
 */
@Slf4j
@Node(
	type = "getResponse",
	displayName = "GetResponse",
	description = "Manage contacts in GetResponse",
	category = "Marketing",
	icon = "getResponse",
	credentials = {"getResponseApi"},
	searchOnly = true
)
public class GetResponseNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.getresponse.com/v3";

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
			.name("resource").displayName("Resource")
			.type(ParameterType.OPTIONS).required(true).defaultValue("contact")
			.options(List.of(
				ParameterOption.builder().name("Contact").value("contact").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").build(),
				ParameterOption.builder().name("Delete").value("delete").build(),
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Get All").value("getAll").build(),
				ParameterOption.builder().name("Update").value("update").build()
			)).build());

		// Contact fields
		params.add(NodeParameter.builder()
			.name("contactId").displayName("Contact ID")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("get", "update", "delete"))))
			.build());

		params.add(NodeParameter.builder()
			.name("email").displayName("Email")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("campaignId").displayName("Campaign ID")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.description("The campaign (list) to add the contact to")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("name").displayName("Name")
			.type(ParameterType.STRING).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("dayOfCycle").displayName("Day of Cycle")
			.type(ParameterType.NUMBER).defaultValue(0)
			.description("Day of the autoresponder cycle")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("customFields").displayName("Custom Fields (JSON)")
			.type(ParameterType.STRING).defaultValue("[]")
			.description("Custom fields as JSON array, e.g. [{\"customFieldId\":\"abc\",\"value\":[\"val\"]}]")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("tags").displayName("Tag IDs")
			.type(ParameterType.STRING).defaultValue("")
			.description("Comma-separated tag IDs to assign")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("create", "update"))))
			.build());

		// Pagination
		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit")
			.type(ParameterType.NUMBER).defaultValue(100)
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("page").displayName("Page")
			.type(ParameterType.NUMBER).defaultValue(1)
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String apiKey = context.getCredentialString("apiKey", "");
			String operation = context.getParameter("operation", "get");

			Map<String, String> headers = new LinkedHashMap<>();
			headers.put("X-Auth-Token", "api-key " + apiKey);
			headers.put("Content-Type", "application/json");

			return switch (operation) {
				case "create" -> createContact(context, headers);
				case "delete" -> deleteContact(context, headers);
				case "get" -> getContact(context, headers);
				case "getAll" -> getAllContacts(context, headers);
				case "update" -> updateContact(context, headers);
				default -> NodeExecutionResult.error("Unknown operation: " + operation);
			};
		} catch (Exception e) {
			return handleError(context, "GetResponse API error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult createContact(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("email", context.getParameter("email", ""));
		body.put("campaign", Map.of("campaignId", context.getParameter("campaignId", "")));

		String name = context.getParameter("name", "");
		if (!name.isEmpty()) body.put("name", name);

		int dayOfCycle = toInt(context.getParameter("dayOfCycle", 0), 0);
		if (dayOfCycle > 0) body.put("dayOfCycle", dayOfCycle);

		addCustomFields(body, context);
		addTags(body, context);

		HttpResponse<String> response = post(BASE_URL + "/contacts", body, headers);
		return toResult(response);
	}

	private NodeExecutionResult deleteContact(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String contactId = context.getParameter("contactId", "");
		HttpResponse<String> response = delete(BASE_URL + "/contacts/" + encode(contactId), headers);
		return toDeleteResult(response);
	}

	private NodeExecutionResult getContact(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String contactId = context.getParameter("contactId", "");
		HttpResponse<String> response = get(BASE_URL + "/contacts/" + encode(contactId), headers);
		return toResult(response);
	}

	private NodeExecutionResult getAllContacts(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		int limit = toInt(context.getParameter("limit", 100), 100);
		int page = toInt(context.getParameter("page", 1), 1);
		String url = BASE_URL + "/contacts?perPage=" + limit + "&page=" + page;
		HttpResponse<String> response = get(url, headers);

		if (response.statusCode() >= 400) {
			return apiError(response);
		}

		List<Map<String, Object>> parsed = parseArrayResponse(response);
		List<Map<String, Object>> results = new ArrayList<>();
		for (Map<String, Object> item : parsed) {
			results.add(wrapInJson(item));
		}
		return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
	}

	private NodeExecutionResult updateContact(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String contactId = context.getParameter("contactId", "");
		Map<String, Object> body = new LinkedHashMap<>();

		String name = context.getParameter("name", "");
		if (!name.isEmpty()) body.put("name", name);

		int dayOfCycle = toInt(context.getParameter("dayOfCycle", 0), 0);
		if (dayOfCycle > 0) body.put("dayOfCycle", dayOfCycle);

		addCustomFields(body, context);
		addTags(body, context);

		HttpResponse<String> response = post(BASE_URL + "/contacts/" + encode(contactId), body, headers);
		return toResult(response);
	}

	// ========================= Helpers =========================

	private void addCustomFields(Map<String, Object> body, NodeExecutionContext context) {
		String customFieldsJson = context.getParameter("customFields", "[]");
		try {
			Object customFields = objectMapper.readValue(customFieldsJson, Object.class);
			if (customFields instanceof List && !((List<?>) customFields).isEmpty()) {
				body.put("customFieldValues", customFields);
			}
		} catch (Exception ignored) {
			// ignore invalid JSON
		}
	}

	private void addTags(Map<String, Object> body, NodeExecutionContext context) {
		String tags = context.getParameter("tags", "");
		if (!tags.isEmpty()) {
			List<Map<String, String>> tagList = Arrays.stream(tags.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.map(tagId -> Map.of("tagId", tagId))
				.toList();
			if (!tagList.isEmpty()) {
				body.put("tags", tagList);
			}
		}
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		if (response.body() == null || response.body().isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		return toResult(response);
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("GetResponse API error (HTTP " + response.statusCode() + "): " + body);
	}
}
