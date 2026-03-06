package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Adalo — manage collection records using the Adalo API.
 */
@Node(
		type = "adalo",
		displayName = "Adalo",
		description = "Manage collection records in Adalo apps",
		category = "Miscellaneous",
		icon = "adalo",
		credentials = {"adaloApi"},
		searchOnly = true
)
public class AdaloNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");
		String appId = context.getCredentialString("appId", "");

		String operation = context.getParameter("operation", "getAll");
		String collectionId = context.getParameter("collectionId", "");

		String baseUrl = "https://api.adalo.com/v0/apps/" + encode(appId) + "/collections/" + encode(collectionId);

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + apiKey);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "create" -> {
						String fieldsJson = context.getParameter("fields", "{}");
						Object fields = parseJson(fieldsJson);
						Map<String, Object> body = new LinkedHashMap<>();
						if (fields instanceof Map) {
							@SuppressWarnings("unchecked")
							Map<String, Object> fieldMap = (Map<String, Object>) fields;
							body.putAll(fieldMap);
						}
						HttpResponse<String> response = post(baseUrl, body, headers);
						yield parseResponse(response);
					}
					case "delete" -> {
						String rowId = context.getParameter("rowId", "");
						HttpResponse<String> response = delete(baseUrl + "/" + encode(rowId), headers);
						yield parseResponse(response);
					}
					case "get" -> {
						String rowId = context.getParameter("rowId", "");
						HttpResponse<String> response = get(baseUrl + "/" + encode(rowId), headers);
						yield parseResponse(response);
					}
					case "getAll" -> {
						int limit = toInt(context.getParameters().get("limit"), 100);
						HttpResponse<String> response = get(baseUrl + "?limit=" + limit + "&offset=0", headers);
						yield parseResponse(response);
					}
					case "update" -> {
						String rowId = context.getParameter("rowId", "");
						String fieldsJson = context.getParameter("fields", "{}");
						Object fields = parseJson(fieldsJson);
						Map<String, Object> body = new LinkedHashMap<>();
						if (fields instanceof Map) {
							@SuppressWarnings("unchecked")
							Map<String, Object> fieldMap = (Map<String, Object>) fields;
							body.putAll(fieldMap);
						}
						HttpResponse<String> response = put(baseUrl + "/" + encode(rowId), body, headers);
						yield parseResponse(response);
					}
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

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
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
						.required(true).description("The collection ID in Adalo.").build(),
				NodeParameter.builder()
						.name("rowId").displayName("Row ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("fields").displayName("Fields")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("Field values as JSON object.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max records to return.").build()
		);
	}
}
