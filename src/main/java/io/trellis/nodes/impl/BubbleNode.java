package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Bubble — CRUD operations on Bubble.io app objects via the Data API.
 */
@Node(
		type = "bubble",
		displayName = "Bubble",
		description = "Create, read, update and delete objects in Bubble",
		category = "CMS / Website Builders",
		icon = "bubble",
		credentials = {"bubbleApi"}
)
public class BubbleNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String appName = context.getCredentialString("appName", "");
		String apiToken = context.getCredentialString("apiToken", "");
		boolean useDevelopment = toBoolean(context.getParameters().get("useDevelopment"), false);
		String typeName = context.getParameter("typeName", "");
		String operation = context.getParameter("operation", "getMany");

		String env = useDevelopment ? "/version-test" : "";
		String baseUrl = "https://" + appName + ".bubbleapps.io" + env + "/api/1.1/obj/" + encode(typeName);

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + apiToken);
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "create" -> handleCreate(context, baseUrl, headers);
					case "delete" -> handleDelete(context, baseUrl, headers);
					case "get" -> handleGet(context, baseUrl, headers);
					case "getMany" -> handleGetMany(context, baseUrl, headers);
					case "update" -> handleUpdate(context, baseUrl, headers);
					default -> throw new IllegalArgumentException("Unknown operation: " + operation);
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

	private Map<String, Object> handleCreate(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String propertiesJson = context.getParameter("properties", "{}");
		Map<String, Object> body = parseJson(propertiesJson);

		HttpResponse<String> response = post(baseUrl, body, headers);
		return parseResponse(response);
	}

	private Map<String, Object> handleDelete(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String objectId = context.getParameter("objectId", "");
		HttpResponse<String> response = delete(baseUrl + "/" + encode(objectId), headers);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
		result.put("objectId", objectId);
		return result;
	}

	private Map<String, Object> handleGet(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String objectId = context.getParameter("objectId", "");
		HttpResponse<String> response = get(baseUrl + "/" + encode(objectId), headers);
		return parseResponse(response);
	}

	private Map<String, Object> handleGetMany(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 100);

		String url = baseUrl + "?limit=" + (returnAll ? 100 : Math.min(limit, 100));
		HttpResponse<String> response = get(url, headers);
		return parseResponse(response);
	}

	private Map<String, Object> handleUpdate(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String objectId = context.getParameter("objectId", "");
		String propertiesJson = context.getParameter("properties", "{}");
		Map<String, Object> body = parseJson(propertiesJson);

		HttpResponse<String> response = patch(baseUrl + "/" + encode(objectId), body, headers);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
		result.put("objectId", objectId);
		if (response.body() != null && !response.body().isBlank()) {
			result.putAll(parseJson(response.body()));
		}
		return result;
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("getMany")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get Many").value("getMany").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("typeName").displayName("Type Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name of the data type to operate on.").build(),
				NodeParameter.builder()
						.name("objectId").displayName("Object ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("ID of the object.").build(),
				NodeParameter.builder()
						.name("properties").displayName("Properties (JSON)")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("Object properties as JSON for create/update.").build(),
				NodeParameter.builder()
						.name("useDevelopment").displayName("Use Development API")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Use the development version of the API.").build(),
				NodeParameter.builder()
						.name("returnAll").displayName("Return All")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether to return all results.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max number of results to return.").build()
		);
	}
}
