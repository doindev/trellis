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

@Slf4j
@Node(
	type = "airtable",
	displayName = "Airtable",
	description = "Manage records in Airtable bases.",
	category = "Spreadsheets",
	icon = "airtable",
	credentials = {"airtableApi"}
)
public class AirtableNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.airtable.com/v0";

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

		params.add(NodeParameter.builder()
			.name("resource").displayName("Resource")
			.type(ParameterType.OPTIONS).required(true).defaultValue("record")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Record").value("record").description("Manage table records").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("record"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a record").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a record").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a record by ID").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many records").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a record").build(),
				ParameterOption.builder().name("Upsert").value("upsert").description("Update or create records").build()
			)).build());

		// Base ID
		params.add(NodeParameter.builder()
			.name("baseId").displayName("Base ID")
			.type(ParameterType.STRING).required(true)
			.description("The ID of the Airtable base (starts with 'app').")
			.placeHolder("appXXXXXXXXXXXXXX")
			.displayOptions(Map.of("show", Map.of("resource", List.of("record"))))
			.build());

		// Table Name
		params.add(NodeParameter.builder()
			.name("tableName").displayName("Table Name")
			.type(ParameterType.STRING).required(true)
			.description("The name or ID of the table.")
			.placeHolder("My Table")
			.displayOptions(Map.of("show", Map.of("resource", List.of("record"))))
			.build());

		// Record ID (for get, delete, update)
		params.add(NodeParameter.builder()
			.name("recordId").displayName("Record ID")
			.type(ParameterType.STRING).required(true)
			.description("The ID of the record (starts with 'rec').")
			.placeHolder("recXXXXXXXXXXXXXX")
			.displayOptions(Map.of("show", Map.of("resource", List.of("record"), "operation", List.of("get", "delete", "update"))))
			.build());

		// Fields (for create, update)
		params.add(NodeParameter.builder()
			.name("fields").displayName("Fields (JSON)")
			.type(ParameterType.JSON)
			.description("The field values as a JSON object (e.g. {\"Name\": \"John\", \"Email\": \"john@example.com\"}).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("record"), "operation", List.of("create", "update"))))
			.build());

		// Upsert fields
		params.add(NodeParameter.builder()
			.name("upsertFields").displayName("Records (JSON)")
			.type(ParameterType.JSON).required(true)
			.description("JSON array of records with fields, e.g. [{\"fields\":{\"Name\":\"John\"}}].")
			.displayOptions(Map.of("show", Map.of("resource", List.of("record"), "operation", List.of("upsert"))))
			.build());

		// Upsert merge fields on
		params.add(NodeParameter.builder()
			.name("upsertFieldsToMergeOn").displayName("Fields to Merge On")
			.type(ParameterType.STRING).required(true)
			.description("Comma-separated field names to use for matching existing records.")
			.placeHolder("Email")
			.displayOptions(Map.of("show", Map.of("resource", List.of("record"), "operation", List.of("upsert"))))
			.build());

		// GetAll: additional options
		params.add(NodeParameter.builder()
			.name("getAllOptions").displayName("Options")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("record"), "operation", List.of("getAll"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("filterByFormula").displayName("Filter By Formula")
					.type(ParameterType.STRING)
					.description("An Airtable formula to filter records.")
					.placeHolder("{Status}='Active'").build(),
				NodeParameter.builder().name("maxRecords").displayName("Max Records")
					.type(ParameterType.NUMBER).defaultValue(100)
					.description("Maximum number of records to return.").build(),
				NodeParameter.builder().name("sort").displayName("Sort Field")
					.type(ParameterType.STRING)
					.description("Field name to sort by.").build(),
				NodeParameter.builder().name("sortDirection").displayName("Sort Direction")
					.type(ParameterType.OPTIONS).defaultValue("asc")
					.options(List.of(
						ParameterOption.builder().name("Ascending").value("asc").build(),
						ParameterOption.builder().name("Descending").value("desc").build()
					)).build(),
				NodeParameter.builder().name("view").displayName("View")
					.type(ParameterType.STRING)
					.description("The name or ID of a view to use.").build(),
				NodeParameter.builder().name("fields").displayName("Fields")
					.type(ParameterType.STRING)
					.description("Comma-separated list of fields to return.").build()
			)).build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = String.valueOf(credentials.getOrDefault("apiKey", credentials.getOrDefault("accessToken", "")));

		String resource = context.getParameter("resource", "record");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + apiKey);
		headers.put("Content-Type", "application/json");

		try {
			if ("record".equals(resource)) {
				return executeRecord(context, operation, headers);
			}
			return NodeExecutionResult.error("Unknown resource: " + resource);
		} catch (Exception e) {
			return handleError(context, "Airtable API error: " + e.getMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeRecord(NodeExecutionContext context, String operation, Map<String, String> headers) throws Exception {
		String baseId = context.getParameter("baseId", "");
		String tableName = context.getParameter("tableName", "");
		String baseUrl = BASE_URL + "/" + encode(baseId) + "/" + encode(tableName);

		switch (operation) {
			case "create": {
				Object fields = context.getParameter("fields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("fields", fields);

				HttpResponse<String> response = post(baseUrl, body, headers);
				return toResult(response);
			}
			case "delete": {
				String recordId = context.getParameter("recordId", "");
				HttpResponse<String> response = delete(baseUrl + "/" + encode(recordId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String recordId = context.getParameter("recordId", "");
				HttpResponse<String> response = get(baseUrl + "/" + encode(recordId), headers);
				return toResult(response);
			}
			case "getAll": {
				Map<String, Object> options = context.getParameter("getAllOptions", Map.of());
				StringBuilder url = new StringBuilder(baseUrl);

				List<String> queryParts = new ArrayList<>();
				String formula = options.get("filterByFormula") != null ? String.valueOf(options.get("filterByFormula")) : "";
				if (!formula.isEmpty()) {
					queryParts.add("filterByFormula=" + encode(formula));
				}
				int maxRecords = toInt(options.get("maxRecords"), 100);
				queryParts.add("maxRecords=" + maxRecords);

				String sortField = options.get("sort") != null ? String.valueOf(options.get("sort")) : "";
				String sortDirection = options.get("sortDirection") != null ? String.valueOf(options.get("sortDirection")) : "asc";
				if (!sortField.isEmpty()) {
					queryParts.add("sort[0][field]=" + encode(sortField));
					queryParts.add("sort[0][direction]=" + encode(sortDirection));
				}

				String view = options.get("view") != null ? String.valueOf(options.get("view")) : "";
				if (!view.isEmpty()) {
					queryParts.add("view=" + encode(view));
				}

				String fieldsList = options.get("fields") != null ? String.valueOf(options.get("fields")) : "";
				if (!fieldsList.isEmpty()) {
					String[] fieldArr = fieldsList.split(",");
					for (int i = 0; i < fieldArr.length; i++) {
						queryParts.add("fields[]=" + encode(fieldArr[i].trim()));
					}
				}

				if (!queryParts.isEmpty()) {
					url.append("?").append(String.join("&", queryParts));
				}

				// Paginate through all results
				List<Map<String, Object>> allItems = new ArrayList<>();
				String offset = null;

				do {
					String requestUrl = url.toString();
					if (offset != null) {
						requestUrl += (requestUrl.contains("?") ? "&" : "?") + "offset=" + encode(offset);
					}

					HttpResponse<String> response = get(requestUrl, headers);
					if (response.statusCode() >= 400) {
						return apiError(response);
					}

					Map<String, Object> parsed = parseResponse(response);
					Object records = parsed.get("records");
					if (records instanceof List) {
						for (Object record : (List<?>) records) {
							if (record instanceof Map) {
								allItems.add(wrapInJson(record));
							}
						}
					}

					offset = parsed.containsKey("offset") ? String.valueOf(parsed.get("offset")) : null;
				} while (offset != null && allItems.size() < maxRecords);

				return allItems.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(allItems);
			}
			case "update": {
				String recordId = context.getParameter("recordId", "");
				Object fields = context.getParameter("fields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("fields", fields);

				HttpResponse<String> response = patch(baseUrl + "/" + encode(recordId), body, headers);
				return toResult(response);
			}
			case "upsert": {
				Object upsertFields = context.getParameter("upsertFields", List.of());
				String mergeOn = context.getParameter("upsertFieldsToMergeOn", "");

				List<String> mergeFields = Arrays.stream(mergeOn.split(","))
					.map(String::trim).filter(s -> !s.isEmpty()).toList();

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("performUpsert", Map.of("fieldsToMergeOn", mergeFields));
				body.put("records", upsertFields);

				HttpResponse<String> response = patch(baseUrl, body, headers);
				if (response.statusCode() >= 400) {
					return apiError(response);
				}

				Map<String, Object> parsed = parseResponse(response);
				Object records = parsed.get("records");
				if (records instanceof List) {
					List<Map<String, Object>> items = new ArrayList<>();
					for (Object record : (List<?>) records) {
						if (record instanceof Map) {
							items.add(wrapInJson(record));
						}
					}
					return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			default:
				return NodeExecutionResult.error("Unknown record operation: " + operation);
		}
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
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
		return NodeExecutionResult.error("Airtable API error (HTTP " + response.statusCode() + "): " + body);
	}
}
