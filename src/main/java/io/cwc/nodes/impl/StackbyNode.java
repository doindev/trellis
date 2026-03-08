package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Stackby — manage records in Stackby spreadsheet tables.
 */
@Node(
		type = "stackby",
		displayName = "Stackby",
		description = "Manage records in Stackby tables",
		category = "Spreadsheets & Data Tables",
		icon = "stackby",
		credentials = {"stackbyApi"}
)
public class StackbyNode extends AbstractApiNode {

	private static final String BASE_URL = "https://stackby.com/api/betav1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");

		String operation = context.getParameter("operation", "list");
		String stackId = context.getParameter("stackId", "");
		String table = context.getParameter("table", "");

		Map<String, String> headers = new HashMap<>();
		headers.put("api-key", apiKey);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "append" -> {
						String fieldsJson = context.getParameter("fields", "{}");
						Object fields = parseJson(fieldsJson);
						Map<String, Object> record = new LinkedHashMap<>();
						record.put("field", fields);
						Map<String, Object> body = Map.of("records", List.of(record));
						HttpResponse<String> response = post(BASE_URL + "/rowcreate/" + encode(stackId) + "/" + encode(table), body, headers);
						yield parseResponse(response);
					}
					case "delete" -> {
						String rowId = context.getParameter("rowId", "");
						HttpResponse<String> response = delete(BASE_URL + "/rowdelete/" + encode(stackId) + "/" + encode(table) + "?rowIds=" + encode(rowId), headers);
						yield parseResponse(response);
					}
					case "list" -> {
						int limit = toInt(context.getParameters().get("limit"), 100);
						StringBuilder url = new StringBuilder(BASE_URL + "/rowlist/" + encode(stackId) + "/" + encode(table));
						url.append("?maxrecord=").append(limit);
						String view = context.getParameter("view", "");
						if (!view.isEmpty()) url.append("&view=").append(encode(view));
						HttpResponse<String> response = get(url.toString(), headers);
						yield parseResponse(response);
					}
					case "read" -> {
						String rowId = context.getParameter("rowId", "");
						HttpResponse<String> response = get(BASE_URL + "/rowlist/" + encode(stackId) + "/" + encode(table) + "?rowIds=" + encode(rowId), headers);
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
						.type(ParameterType.OPTIONS).defaultValue("list")
						.options(List.of(
								ParameterOption.builder().name("Append").value("append").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("List").value("list").build(),
								ParameterOption.builder().name("Read").value("read").build()
						)).build(),
				NodeParameter.builder()
						.name("stackId").displayName("Stack ID")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("The Stackby stack ID.").build(),
				NodeParameter.builder()
						.name("table").displayName("Table")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("The table name.").build(),
				NodeParameter.builder()
						.name("rowId").displayName("Row ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("fields").displayName("Fields")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("Column values as JSON object.").build(),
				NodeParameter.builder()
						.name("view").displayName("View")
						.type(ParameterType.STRING).defaultValue("")
						.description("View name to filter results.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max records to return.").build()
		);
	}
}
