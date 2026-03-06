package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Webflow — manage Webflow CMS items using the Webflow v2 API.
 */
@Node(
		type = "webflow",
		displayName = "Webflow",
		description = "Manage Webflow CMS items",
		category = "CMS / Website Builders",
		icon = "webflow",
		credentials = {"webflowOAuth2Api"}
)
public class WebflowNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.webflow.com/v2";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessToken = context.getCredentialString("accessToken", "");

		String resource = context.getParameter("resource", "item");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "item" -> handleItem(context, headers, operation);
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

	private Map<String, Object> handleItem(NodeExecutionContext context, Map<String, String> headers,
			String operation) throws Exception {
		String collectionId = context.getParameter("collectionId", "");
		return switch (operation) {
			case "create" -> {
				Map<String, Object> fieldData = buildFieldData(context);
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("fieldData", fieldData);
				boolean isLive = toBoolean(context.getParameters().get("live"), false);
				String url = BASE_URL + "/collections/" + encode(collectionId) + "/items";
				if (isLive) {
					url += "?live=true";
				}
				HttpResponse<String> response = post(url, body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String itemId = context.getParameter("itemId", "");
				String url = BASE_URL + "/collections/" + encode(collectionId) + "/items/" + encode(itemId);
				HttpResponse<String> response = delete(url, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String itemId = context.getParameter("itemId", "");
				String url = BASE_URL + "/collections/" + encode(collectionId) + "/items/" + encode(itemId);
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				StringBuilder url = new StringBuilder(BASE_URL + "/collections/" + encode(collectionId) + "/items");
				int limit = toInt(context.getParameters().get("limit"), 100);
				url.append("?limit=").append(limit);
				String offset = context.getParameter("offset", "");
				if (!offset.isEmpty()) {
					url.append("&offset=").append(encode(offset));
				}
				HttpResponse<String> response = get(url.toString(), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String itemId = context.getParameter("itemId", "");
				Map<String, Object> fieldData = buildFieldData(context);
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("fieldData", fieldData);
				boolean isLive = toBoolean(context.getParameters().get("live"), false);
				String url = BASE_URL + "/collections/" + encode(collectionId) + "/items/" + encode(itemId);
				if (isLive) {
					url += "?live=true";
				}
				HttpResponse<String> response = patch(url, body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown item operation: " + operation);
		};
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> buildFieldData(NodeExecutionContext context) {
		Map<String, Object> fieldData = new LinkedHashMap<>();
		String name = context.getParameter("name", "");
		if (!name.isEmpty()) fieldData.put("name", name);
		String slug = context.getParameter("slug", "");
		if (!slug.isEmpty()) fieldData.put("slug", slug);

		// Check for additional fields provided as JSON or key-value pairs
		Object additionalFields = context.getParameters().get("additionalFields");
		if (additionalFields instanceof Map) {
			fieldData.putAll((Map<String, Object>) additionalFields);
		}

		return fieldData;
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("item")
						.options(List.of(
								ParameterOption.builder().name("Item").value("item").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("collectionId").displayName("Collection ID")
						.type(ParameterType.STRING).defaultValue("")
						.required(true)
						.description("The ID of the Webflow collection.").build(),
				NodeParameter.builder()
						.name("itemId").displayName("Item ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the item.").build(),
				NodeParameter.builder()
						.name("name").displayName("Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The name of the item.").build(),
				NodeParameter.builder()
						.name("slug").displayName("Slug")
						.type(ParameterType.STRING).defaultValue("")
						.description("URL slug for the item.").build(),
				NodeParameter.builder()
						.name("live").displayName("Live")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether to publish the item to the live site immediately.").build(),
				NodeParameter.builder()
						.name("additionalFields").displayName("Additional Fields")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("Additional field data as JSON to include in the item's fieldData.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Maximum number of items to return.").build(),
				NodeParameter.builder()
						.name("offset").displayName("Offset")
						.type(ParameterType.STRING).defaultValue("")
						.description("Pagination offset for retrieving additional items.").build()
		);
	}
}
