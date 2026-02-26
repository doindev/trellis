package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Cockpit — manage collections, singletons, and forms in Cockpit CMS.
 */
@Node(
		type = "cockpit",
		displayName = "Cockpit",
		description = "Manage collections, forms, and singletons in Cockpit CMS",
		category = "CMS / Website Builders",
		icon = "cockpit",
		credentials = {"cockpitApi"}
)
public class CockpitNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String baseUrl = context.getCredentialString("url", "");
		String accessToken = context.getCredentialString("accessToken", "");
		String resource = context.getParameter("resource", "collection");
		String operation = context.getParameter("operation", "getAll");

		if (baseUrl.endsWith("/")) {
			baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		}

		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "collection" -> handleCollection(context, baseUrl, accessToken, operation, headers);
					case "form" -> handleForm(context, baseUrl, accessToken, headers);
					case "singleton" -> handleSingleton(context, baseUrl, accessToken, headers);
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

	private Map<String, Object> handleCollection(NodeExecutionContext context, String baseUrl,
			String token, String operation, Map<String, String> headers) throws Exception {
		String collectionName = context.getParameter("collectionName", "");
		String apiUrl = baseUrl + "/api/collections/entries/" + encode(collectionName) + "?token=" + encode(token);

		return switch (operation) {
			case "create" -> {
				String data = context.getParameter("data", "{}");
				Map<String, Object> body = Map.of("data", parseJson(data));
				String saveUrl = baseUrl + "/api/collections/save/" + encode(collectionName) + "?token=" + encode(token);
				HttpResponse<String> resp = post(saveUrl, body, headers);
				yield parseResponse(resp);
			}
			case "getAll" -> {
				boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
				int limit = toInt(context.getParameters().get("limit"), 100);
				String url = apiUrl;
				if (!returnAll) {
					url += "&limit=" + limit;
				}
				HttpResponse<String> resp = get(url, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("entries", parseJsonArray(resp.body()));
				yield result;
			}
			case "update" -> {
				String entryId = context.getParameter("entryId", "");
				String data = context.getParameter("data", "{}");
				Map<String, Object> entryData = parseJson(data);
				entryData.put("_id", entryId);
				Map<String, Object> body = Map.of("data", entryData);
				String saveUrl = baseUrl + "/api/collections/save/" + encode(collectionName) + "?token=" + encode(token);
				HttpResponse<String> resp = post(saveUrl, body, headers);
				yield parseResponse(resp);
			}
			default -> throw new IllegalArgumentException("Unknown collection operation: " + operation);
		};
	}

	private Map<String, Object> handleForm(NodeExecutionContext context, String baseUrl,
			String token, Map<String, String> headers) throws Exception {
		String formName = context.getParameter("formName", "");
		String data = context.getParameter("data", "{}");

		String url = baseUrl + "/api/forms/submit/" + encode(formName) + "?token=" + encode(token);
		Map<String, Object> body = Map.of("form", parseJson(data));

		HttpResponse<String> response = post(url, body, headers);
		return parseResponse(response);
	}

	private Map<String, Object> handleSingleton(NodeExecutionContext context, String baseUrl,
			String token, Map<String, String> headers) throws Exception {
		String singletonName = context.getParameter("singletonName", "");
		String url = baseUrl + "/api/singletons/get/" + encode(singletonName) + "?token=" + encode(token);

		HttpResponse<String> response = get(url, headers);
		return parseResponse(response);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS)
						.defaultValue("collection")
						.options(List.of(
								ParameterOption.builder().name("Collection").value("collection").build(),
								ParameterOption.builder().name("Form").value("form").build(),
								ParameterOption.builder().name("Singleton").value("singleton").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Update").value("update").build(),
								ParameterOption.builder().name("Submit").value("submit").build(),
								ParameterOption.builder().name("Get").value("get").build()
						)).build(),
				NodeParameter.builder()
						.name("collectionName").displayName("Collection Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name of the collection to operate on.").build(),
				NodeParameter.builder()
						.name("formName").displayName("Form Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name of the form to submit.").build(),
				NodeParameter.builder()
						.name("singletonName").displayName("Singleton Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name of the singleton to retrieve.").build(),
				NodeParameter.builder()
						.name("entryId").displayName("Entry ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the entry to update.").build(),
				NodeParameter.builder()
						.name("data").displayName("Data (JSON)")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("Entry data as JSON.").build(),
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
