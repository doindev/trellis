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
 * Odoo Node -- interact with Odoo ERP via JSON-RPC API.
 * Supports CRUD and search operations on any Odoo model.
 */
@Slf4j
@Node(
	type = "odoo",
	displayName = "Odoo",
	description = "Interact with Odoo ERP via JSON-RPC to manage records on any model",
	category = "Miscellaneous",
	icon = "odoo",
	credentials = {"odooApi"}
)
public class OdooNode extends AbstractApiNode {

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

		// Operation selector
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("read")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a new record").build(),
				ParameterOption.builder().name("Read").value("read").description("Read records by IDs").build(),
				ParameterOption.builder().name("Update").value("update").description("Update an existing record").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete records by IDs").build(),
				ParameterOption.builder().name("Search").value("search").description("Search for records").build(),
				ParameterOption.builder().name("Search & Read").value("searchRead").description("Search for records and return their data").build()
			)).build());

		// Model name (required for all operations)
		params.add(NodeParameter.builder()
			.name("model").displayName("Model").type(ParameterType.STRING).required(true)
			.placeHolder("res.partner")
			.description("The Odoo model name (e.g., res.partner, sale.order, product.product).")
			.build());

		// Record IDs for read, update, delete
		params.add(NodeParameter.builder()
			.name("recordIds").displayName("Record IDs").type(ParameterType.STRING).required(true)
			.placeHolder("1,2,3")
			.description("Comma-separated list of record IDs.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("read", "delete"))))
			.build());

		// Record ID for update
		params.add(NodeParameter.builder()
			.name("recordId").displayName("Record ID").type(ParameterType.STRING).required(true)
			.description("The ID of the record to update.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("update"))))
			.build());

		// Fields for create and update
		params.add(NodeParameter.builder()
			.name("fieldsJson").displayName("Fields (JSON)").type(ParameterType.JSON).required(true)
			.placeHolder("{\"name\": \"John Doe\", \"email\": \"john@example.com\"}")
			.description("A JSON object with the field names and values to create or update.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create", "update"))))
			.build());

		// Domain for search / searchRead
		params.add(NodeParameter.builder()
			.name("domain").displayName("Domain (JSON)").type(ParameterType.JSON)
			.placeHolder("[[\"name\", \"ilike\", \"john\"]]")
			.description("Odoo domain filter as a JSON array of conditions. Example: [[\"is_company\", \"=\", true]]")
			.displayOptions(Map.of("show", Map.of("operation", List.of("search", "searchRead"))))
			.build());

		// Fields to return for read and searchRead
		params.add(NodeParameter.builder()
			.name("fields").displayName("Fields to Return").type(ParameterType.STRING)
			.placeHolder("name,email,phone")
			.description("Comma-separated list of field names to return. Leave empty for all fields.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("read", "searchRead"))))
			.build());

		// Limit for search / searchRead
		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit").type(ParameterType.NUMBER)
			.defaultValue(100)
			.description("Maximum number of records to return.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("search", "searchRead"))))
			.build());

		// Offset for search / searchRead
		params.add(NodeParameter.builder()
			.name("offset").displayName("Offset").type(ParameterType.NUMBER)
			.defaultValue(0)
			.description("Number of records to skip.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("search", "searchRead"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String operation = context.getParameter("operation", "read");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String baseUrl = getApiBaseUrl(credentials);
			int uid = authenticate(credentials, baseUrl);
			String db = String.valueOf(credentials.getOrDefault("database", ""));
			String password = String.valueOf(credentials.getOrDefault("password", ""));
			String model = context.getParameter("model", "");

			return switch (operation) {
				case "create" -> executeCreate(context, baseUrl, db, uid, password, model);
				case "read" -> executeRead(context, baseUrl, db, uid, password, model);
				case "update" -> executeUpdate(context, baseUrl, db, uid, password, model);
				case "delete" -> executeDelete(context, baseUrl, db, uid, password, model);
				case "search" -> executeSearch(context, baseUrl, db, uid, password, model);
				case "searchRead" -> executeSearchRead(context, baseUrl, db, uid, password, model);
				default -> NodeExecutionResult.error("Unknown operation: " + operation);
			};
		} catch (Exception e) {
			return handleError(context, "Odoo API error: " + e.getMessage(), e);
		}
	}

	// ========================= Create =========================

	private NodeExecutionResult executeCreate(NodeExecutionContext context, String baseUrl,
			String db, int uid, String password, String model) throws Exception {
		String fieldsJson = context.getParameter("fieldsJson", "{}");
		Map<String, Object> fields = parseJsonObject(fieldsJson);

		Map<String, Object> result = callJsonRpc(baseUrl, "object", "execute_kw",
			List.of(db, uid, password, model, "create", List.of(fields)));

		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("id", result.get("result")))));
	}

	// ========================= Read =========================

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeRead(NodeExecutionContext context, String baseUrl,
			String db, int uid, String password, String model) throws Exception {
		String recordIdsStr = context.getParameter("recordIds", "");
		List<Integer> ids = parseIds(recordIdsStr);
		String fieldsStr = context.getParameter("fields", "");

		Map<String, Object> kwargs = new LinkedHashMap<>();
		if (!fieldsStr.isBlank()) {
			kwargs.put("fields", Arrays.asList(fieldsStr.split("\\s*,\\s*")));
		}

		Map<String, Object> result = callJsonRpc(baseUrl, "object", "execute_kw",
			List.of(db, uid, password, model, "read", List.of(ids), kwargs));

		Object data = result.get("result");
		if (data instanceof List) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Object item : (List<?>) data) {
				if (item instanceof Map) {
					items.add(wrapInJson(item));
				}
			}
			return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	// ========================= Update =========================

	private NodeExecutionResult executeUpdate(NodeExecutionContext context, String baseUrl,
			String db, int uid, String password, String model) throws Exception {
		String recordIdStr = context.getParameter("recordId", "");
		int recordId = Integer.parseInt(recordIdStr.trim());
		String fieldsJson = context.getParameter("fieldsJson", "{}");
		Map<String, Object> fields = parseJsonObject(fieldsJson);

		callJsonRpc(baseUrl, "object", "execute_kw",
			List.of(db, uid, password, model, "write", List.of(List.of(recordId), fields)));

		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "id", recordId))));
	}

	// ========================= Delete =========================

	private NodeExecutionResult executeDelete(NodeExecutionContext context, String baseUrl,
			String db, int uid, String password, String model) throws Exception {
		String recordIdsStr = context.getParameter("recordIds", "");
		List<Integer> ids = parseIds(recordIdsStr);

		callJsonRpc(baseUrl, "object", "execute_kw",
			List.of(db, uid, password, model, "unlink", List.of(ids)));

		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "deletedIds", ids))));
	}

	// ========================= Search =========================

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeSearch(NodeExecutionContext context, String baseUrl,
			String db, int uid, String password, String model) throws Exception {
		String domainJson = context.getParameter("domain", "[]");
		List<?> domain = parseDomain(domainJson);
		int limit = toInt(context.getParameter("limit", 100), 100);
		int offset = toInt(context.getParameter("offset", 0), 0);

		Map<String, Object> kwargs = new LinkedHashMap<>();
		kwargs.put("limit", limit);
		kwargs.put("offset", offset);

		Map<String, Object> result = callJsonRpc(baseUrl, "object", "execute_kw",
			List.of(db, uid, password, model, "search", List.of(domain), kwargs));

		Object data = result.get("result");
		if (data instanceof List) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Object item : (List<?>) data) {
				items.add(wrapInJson(Map.of("id", item)));
			}
			return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	// ========================= Search & Read =========================

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeSearchRead(NodeExecutionContext context, String baseUrl,
			String db, int uid, String password, String model) throws Exception {
		String domainJson = context.getParameter("domain", "[]");
		List<?> domain = parseDomain(domainJson);
		String fieldsStr = context.getParameter("fields", "");
		int limit = toInt(context.getParameter("limit", 100), 100);
		int offset = toInt(context.getParameter("offset", 0), 0);

		Map<String, Object> kwargs = new LinkedHashMap<>();
		if (!fieldsStr.isBlank()) {
			kwargs.put("fields", Arrays.asList(fieldsStr.split("\\s*,\\s*")));
		}
		kwargs.put("limit", limit);
		kwargs.put("offset", offset);

		Map<String, Object> result = callJsonRpc(baseUrl, "object", "execute_kw",
			List.of(db, uid, password, model, "search_read", List.of(domain), kwargs));

		Object data = result.get("result");
		if (data instanceof List) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Object item : (List<?>) data) {
				if (item instanceof Map) {
					items.add(wrapInJson(item));
				}
			}
			return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	// ========================= Helpers =========================

	private String getApiBaseUrl(Map<String, Object> credentials) {
		String url = String.valueOf(credentials.getOrDefault("url", "http://localhost:8069"));
		if (url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}
		return url;
	}

	private int authenticate(Map<String, Object> credentials, String baseUrl) throws Exception {
		String db = String.valueOf(credentials.getOrDefault("database", ""));
		String username = String.valueOf(credentials.getOrDefault("username", ""));
		String password = String.valueOf(credentials.getOrDefault("password", ""));

		Map<String, Object> rpcPayload = buildJsonRpcPayload("common", "authenticate",
			List.of(db, username, password, Map.of()));

		Map<String, String> headers = Map.of("Content-Type", "application/json");
		HttpResponse<String> response = post(baseUrl + "/jsonrpc", rpcPayload, headers);

		Map<String, Object> result = parseResponse(response);
		Object uid = result.get("result");
		if (uid == null || "false".equals(String.valueOf(uid))) {
			throw new RuntimeException("Odoo authentication failed. Check credentials.");
		}
		return ((Number) uid).intValue();
	}

	private Map<String, Object> callJsonRpc(String baseUrl, String service, String method, List<?> args) throws Exception {
		Map<String, Object> rpcPayload = buildJsonRpcPayload(service, method, args);
		Map<String, String> headers = Map.of("Content-Type", "application/json");

		HttpResponse<String> response = post(baseUrl + "/jsonrpc", rpcPayload, headers);

		if (response.statusCode() >= 400) {
			String body = response.body() != null ? response.body() : "";
			if (body.length() > 300) body = body.substring(0, 300) + "...";
			throw new RuntimeException("Odoo JSON-RPC error (HTTP " + response.statusCode() + "): " + body);
		}

		Map<String, Object> result = parseResponse(response);
		if (result.containsKey("error")) {
			@SuppressWarnings("unchecked")
			Map<String, Object> error = (Map<String, Object>) result.get("error");
			String message = String.valueOf(error.getOrDefault("message", "Unknown Odoo error"));
			throw new RuntimeException("Odoo error: " + message);
		}

		return result;
	}

	private Map<String, Object> buildJsonRpcPayload(String service, String method, List<?> args) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("jsonrpc", "2.0");
		payload.put("method", "call");
		payload.put("id", 1);
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("service", service);
		params.put("method", method);
		params.put("args", args);
		payload.put("params", params);
		return payload;
	}

	private List<Integer> parseIds(String idsStr) {
		List<Integer> ids = new ArrayList<>();
		if (idsStr != null && !idsStr.isBlank()) {
			for (String id : idsStr.split("\\s*,\\s*")) {
				if (!id.isEmpty()) {
					ids.add(Integer.parseInt(id.trim()));
				}
			}
		}
		return ids;
	}

	private List<?> parseDomain(String domainJson) {
		try {
			if (domainJson == null || domainJson.isBlank()) {
				return List.of();
			}
			return objectMapper.readValue(domainJson, List.class);
		} catch (Exception e) {
			log.warn("Failed to parse Odoo domain JSON: {}", domainJson, e);
			return List.of();
		}
	}
}
