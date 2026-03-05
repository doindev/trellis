package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Quick Base — manage records, fields, files, and reports using the Quick Base API.
 */
@Node(
		type = "quickBase",
		displayName = "Quick Base",
		description = "Manage records and data in Quick Base",
		category = "Spreadsheets & Data Tables",
		icon = "quickBase",
		credentials = {"quickbaseApi"}
)
public class QuickBaseNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.quickbase.com/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String userToken = context.getCredentialString("userToken", "");
		String hostname = context.getCredentialString("hostname", "");

		String resource = context.getParameter("resource", "record");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "QB-USER-TOKEN " + userToken);
		headers.put("QB-Realm-Hostname", hostname);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "field" -> handleField(context, headers, operation);
					case "record" -> handleRecord(context, headers, operation);
					case "report" -> handleReport(context, headers, operation);
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

	private Map<String, Object> handleField(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "getAll" -> {
				String tableId = context.getParameter("tableId", "");
				HttpResponse<String> response = get(BASE_URL + "/fields?tableId=" + encode(tableId), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown field operation: " + operation);
		};
	}

	private Map<String, Object> handleRecord(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		String tableId = context.getParameter("tableId", "");
		return switch (operation) {
			case "create" -> {
				String fieldsJson = context.getParameter("fields", "{}");
				Object fields = parseJson(fieldsJson);
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("to", tableId);
				body.put("data", List.of(fields));
				body.put("fieldsToReturn", List.of(3));
				HttpResponse<String> response = post(BASE_URL + "/records", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String where = context.getParameter("where", "");
				Map<String, Object> body = Map.of("from", tableId, "where", where);
				HttpResponse<String> response = delete(BASE_URL + "/records", headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("from", tableId);
				Map<String, Object> options = new LinkedHashMap<>();
				options.put("top", limit);
				body.put("options", options);
				String where = context.getParameter("where", "");
				if (!where.isEmpty()) body.put("where", where);
				String select = context.getParameter("select", "");
				if (!select.isEmpty()) {
					List<Integer> selectFields = new ArrayList<>();
					for (String s : select.split(",")) {
						selectFields.add(toInt(s.trim(), 0));
					}
					body.put("select", selectFields);
				}
				HttpResponse<String> response = post(BASE_URL + "/records/query", body, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String fieldsJson = context.getParameter("fields", "{}");
				Object fields = parseJson(fieldsJson);
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("to", tableId);
				body.put("data", List.of(fields));
				HttpResponse<String> response = post(BASE_URL + "/records", body, headers);
				yield parseResponse(response);
			}
			case "upsert" -> {
				String fieldsJson = context.getParameter("fields", "{}");
				Object fields = parseJson(fieldsJson);
				int mergeFieldId = toInt(context.getParameters().get("mergeFieldId"), 0);
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("to", tableId);
				body.put("data", List.of(fields));
				if (mergeFieldId > 0) body.put("mergeFieldId", mergeFieldId);
				HttpResponse<String> response = post(BASE_URL + "/records", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown record operation: " + operation);
		};
	}

	private Map<String, Object> handleReport(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		String tableId = context.getParameter("tableId", "");
		String reportId = context.getParameter("reportId", "");
		return switch (operation) {
			case "get" -> {
				HttpResponse<String> response = get(BASE_URL + "/reports/" + encode(reportId) + "?tableId=" + encode(tableId), headers);
				yield parseResponse(response);
			}
			case "run" -> {
				HttpResponse<String> response = post(BASE_URL + "/reports/" + encode(reportId) + "/run?tableId=" + encode(tableId), Map.of(), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown report operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("record")
						.options(List.of(
								ParameterOption.builder().name("Field").value("field").build(),
								ParameterOption.builder().name("Record").value("record").build(),
								ParameterOption.builder().name("Report").value("report").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Run").value("run").build(),
								ParameterOption.builder().name("Update").value("update").build(),
								ParameterOption.builder().name("Upsert").value("upsert").build()
						)).build(),
				NodeParameter.builder()
						.name("tableId").displayName("Table ID")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("The Quick Base table ID.").build(),
				NodeParameter.builder()
						.name("reportId").displayName("Report ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("fields").displayName("Fields")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("Record fields as JSON (field ID keys with value objects).").build(),
				NodeParameter.builder()
						.name("where").displayName("Where")
						.type(ParameterType.STRING).defaultValue("")
						.description("Quick Base query filter (e.g., {6.EX.'value'}).").build(),
				NodeParameter.builder()
						.name("select").displayName("Select Fields")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated field IDs to return.").build(),
				NodeParameter.builder()
						.name("mergeFieldId").displayName("Merge Field ID")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Field ID to use as merge key for upsert.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max records to return.").build()
		);
	}
}
