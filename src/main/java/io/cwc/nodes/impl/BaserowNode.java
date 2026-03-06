package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Baserow — manage rows in Baserow open-source database tables.
 */
@Node(
		type = "baserow",
		displayName = "Baserow",
		description = "Manage rows in Baserow database tables",
		category = "Data & Storage / Databases",
		icon = "baserow",
		credentials = {"baserowApi"}
)
public class BaserowNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String host = (String) credentials.getOrDefault("host", "https://api.baserow.io");
		if (host.endsWith("/")) host = host.substring(0, host.length() - 1);
		String username = (String) credentials.getOrDefault("username", "");
		String password = (String) credentials.getOrDefault("password", "");

		// Authenticate to get JWT token
		String jwtToken;
		try {
			Map<String, Object> authBody = Map.of("username", username, "password", password);
			Map<String, String> authHeaders = Map.of("Content-Type", "application/json");
			HttpResponse<String> authResponse = post(host + "/api/user/token-auth/", authBody, authHeaders);
			Map<String, Object> authData = parseResponse(authResponse);
			jwtToken = (String) authData.getOrDefault("token", "");
		} catch (Exception e) {
			// Fall back to using API token directly
			jwtToken = (String) credentials.getOrDefault("apiToken", "");
		}

		String operation = context.getParameter("operation", "getAll");
		String tableId = context.getParameter("tableId", "");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "JWT " + jwtToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		String apiBase = host + "/api/database/rows/table/" + tableId;

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "get" -> {
						String rowId = context.getParameter("rowId", "");
						HttpResponse<String> response = get(apiBase + "/" + rowId + "/", headers);
						yield parseResponse(response);
					}
					case "getAll" -> {
						int limit = toInt(context.getParameters().get("limit"), 50);
						StringBuilder params = new StringBuilder();
						params.append("size=").append(limit);
						String searchTerm = context.getParameter("searchTerm", "");
						if (!searchTerm.isEmpty()) params.append("&search=").append(encode(searchTerm));
						String orderBy = context.getParameter("orderBy", "");
						if (!orderBy.isEmpty()) params.append("&order_by=").append(encode(orderBy));
						HttpResponse<String> response = get(apiBase + "/?" + params, headers);
						yield parseResponse(response);
					}
					case "create" -> {
						String fieldsJson = context.getParameter("fields", "");
						Map<String, Object> body = fieldsJson.isEmpty() ? new LinkedHashMap<>() : parseJson(fieldsJson);
						HttpResponse<String> response = post(apiBase + "/", body, headers);
						yield parseResponse(response);
					}
					case "update" -> {
						String rowId = context.getParameter("rowId", "");
						String fieldsJson = context.getParameter("fields", "");
						Map<String, Object> body = fieldsJson.isEmpty() ? new LinkedHashMap<>() : parseJson(fieldsJson);
						HttpResponse<String> response = patch(apiBase + "/" + rowId + "/", body, headers);
						yield parseResponse(response);
					}
					case "delete" -> {
						String rowId = context.getParameter("rowId", "");
						HttpResponse<String> response = delete(apiBase + "/" + rowId + "/", headers);
						yield Map.<String, Object>of("success", true, "rowId", rowId);
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
						.name("tableId").displayName("Table ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("ID of the Baserow table.").build(),
				NodeParameter.builder()
						.name("rowId").displayName("Row ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("ID of the row.").build(),
				NodeParameter.builder()
						.name("fields").displayName("Fields (JSON)")
						.type(ParameterType.STRING).defaultValue("")
						.description("JSON object of field name/value pairs for create or update.").build(),
				NodeParameter.builder()
						.name("searchTerm").displayName("Search Term")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("orderBy").displayName("Order By")
						.type(ParameterType.STRING).defaultValue("")
						.description("Field name to sort by (prefix with - for descending).").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(50)
						.description("Max rows to return (1-100).").build()
		);
	}
}
