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
 * Elasticsearch Node -- index, get, search, update, and delete documents
 * in Elasticsearch via REST API.
 */
@Slf4j
@Node(
	type = "elasticsearch",
	displayName = "Elasticsearch",
	description = "Index, get, search, update, and delete documents in Elasticsearch",
	category = "Database",
	icon = "elasticsearch",
	credentials = {"elasticsearchApi"}
)
public class ElasticsearchNode extends AbstractApiNode {

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("search")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Index Document").value("index").description("Index (create or replace) a document").build(),
				ParameterOption.builder().name("Get Document").value("get").description("Get a document by ID").build(),
				ParameterOption.builder().name("Search").value("search").description("Search for documents").build(),
				ParameterOption.builder().name("Update Document").value("update").description("Update a document partially").build(),
				ParameterOption.builder().name("Delete Document").value("delete").description("Delete a document by ID").build(),
				ParameterOption.builder().name("Create Index").value("createIndex").description("Create a new index").build(),
				ParameterOption.builder().name("Delete Index").value("deleteIndex").description("Delete an index").build()
			)).build());

		// Common parameters
		params.add(NodeParameter.builder()
			.name("index").displayName("Index")
			.type(ParameterType.STRING).required(true)
			.description("The name of the Elasticsearch index.")
			.build());

		params.add(NodeParameter.builder()
			.name("documentId").displayName("Document ID")
			.type(ParameterType.STRING)
			.description("The ID of the document.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("get", "update", "delete", "index"))))
			.build());

		// Document body for indexing
		params.add(NodeParameter.builder()
			.name("document").displayName("Document (JSON)")
			.type(ParameterType.JSON).required(true).defaultValue("{}")
			.description("The document to index as JSON.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("index"))))
			.build());

		// Update fields
		params.add(NodeParameter.builder()
			.name("updateDoc").displayName("Update Fields (JSON)")
			.type(ParameterType.JSON).required(true).defaultValue("{}")
			.description("The partial document to update as JSON.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("update"))))
			.build());

		// Search query
		params.add(NodeParameter.builder()
			.name("query").displayName("Query (JSON)")
			.type(ParameterType.JSON).defaultValue("{\"match_all\": {}}")
			.description("The Elasticsearch query in JSON format.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("search"))))
			.build());

		params.add(NodeParameter.builder()
			.name("size").displayName("Size")
			.type(ParameterType.NUMBER).defaultValue(10)
			.description("Number of results to return.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("search"))))
			.build());

		params.add(NodeParameter.builder()
			.name("from").displayName("From")
			.type(ParameterType.NUMBER).defaultValue(0)
			.description("Starting offset for results.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("search"))))
			.build());

		params.add(NodeParameter.builder()
			.name("sort").displayName("Sort (JSON)")
			.type(ParameterType.JSON).defaultValue("[]")
			.description("Sort criteria as JSON array.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("search"))))
			.build());

		// Index settings for create index
		params.add(NodeParameter.builder()
			.name("indexSettings").displayName("Index Settings (JSON)")
			.type(ParameterType.JSON).defaultValue("{}")
			.description("Settings for the new index (number of shards, replicas, mappings, etc.).")
			.displayOptions(Map.of("show", Map.of("operation", List.of("createIndex"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String operation = context.getParameter("operation", "search");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String baseUrl = getBaseUrl(credentials);
			Map<String, String> headers = getAuthHeaders(credentials);
			String index = context.getParameter("index", "");

			return switch (operation) {
				case "index" -> executeIndex(context, baseUrl, index, headers);
				case "get" -> executeGet(context, baseUrl, index, headers);
				case "search" -> executeSearch(context, baseUrl, index, headers);
				case "update" -> executeUpdate(context, baseUrl, index, headers);
				case "delete" -> executeDelete(context, baseUrl, index, headers);
				case "createIndex" -> executeCreateIndex(context, baseUrl, index, headers);
				case "deleteIndex" -> executeDeleteIndex(baseUrl, index, headers);
				default -> NodeExecutionResult.error("Unknown operation: " + operation);
			};
		} catch (Exception e) {
			return handleError(context, "Elasticsearch API error: " + e.getMessage(), e);
		}
	}

	// ========================= Operations =========================

	private NodeExecutionResult executeIndex(NodeExecutionContext context, String baseUrl,
			String index, Map<String, String> headers) throws Exception {
		String documentId = context.getParameter("documentId", "");
		String documentJson = context.getParameter("document", "{}");
		Map<String, Object> document = parseJson(documentJson);

		String url;
		HttpResponse<String> response;
		if (!documentId.isEmpty()) {
			url = baseUrl + "/" + encode(index) + "/_doc/" + encode(documentId);
			response = put(url, document, headers);
		} else {
			url = baseUrl + "/" + encode(index) + "/_doc";
			response = post(url, document, headers);
		}

		return toResult(response);
	}

	private NodeExecutionResult executeGet(NodeExecutionContext context, String baseUrl,
			String index, Map<String, String> headers) throws Exception {
		String documentId = context.getParameter("documentId", "");
		String url = baseUrl + "/" + encode(index) + "/_doc/" + encode(documentId);
		HttpResponse<String> response = get(url, headers);
		return toResult(response);
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeSearch(NodeExecutionContext context, String baseUrl,
			String index, Map<String, String> headers) throws Exception {
		String queryJson = context.getParameter("query", "{\"match_all\": {}}");
		int size = toInt(context.getParameters().get("size"), 10);
		int from = toInt(context.getParameters().get("from"), 0);
		String sortJson = context.getParameter("sort", "[]");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("query", parseJson(queryJson));
		body.put("size", size);
		body.put("from", from);

		List<Object> sort = parseJsonToList(sortJson);
		if (!sort.isEmpty()) {
			body.put("sort", sort);
		}

		String url = baseUrl + "/" + encode(index) + "/_search";
		HttpResponse<String> response = post(url, body, headers);

		if (response.statusCode() >= 400) {
			return esError(response);
		}

		Map<String, Object> parsed = parseResponse(response);
		Object hitsObj = parsed.get("hits");
		if (hitsObj instanceof Map) {
			Map<String, Object> hitsMap = (Map<String, Object>) hitsObj;
			Object hitsList = hitsMap.get("hits");
			if (hitsList instanceof List) {
				List<Map<String, Object>> results = new ArrayList<>();
				for (Object hit : (List<?>) hitsList) {
					if (hit instanceof Map) {
						results.add(wrapInJson(hit));
					}
				}
				return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
			}
		}

		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult executeUpdate(NodeExecutionContext context, String baseUrl,
			String index, Map<String, String> headers) throws Exception {
		String documentId = context.getParameter("documentId", "");
		String updateDocJson = context.getParameter("updateDoc", "{}");
		Map<String, Object> updateDoc = parseJson(updateDocJson);

		Map<String, Object> body = Map.of("doc", updateDoc);

		String url = baseUrl + "/" + encode(index) + "/_update/" + encode(documentId);
		HttpResponse<String> response = post(url, body, headers);
		return toResult(response);
	}

	private NodeExecutionResult executeDelete(NodeExecutionContext context, String baseUrl,
			String index, Map<String, String> headers) throws Exception {
		String documentId = context.getParameter("documentId", "");
		String url = baseUrl + "/" + encode(index) + "/_doc/" + encode(documentId);
		HttpResponse<String> response = delete(url, headers);

		if (response.statusCode() >= 400) {
			return esError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "id", documentId, "index", index))));
	}

	private NodeExecutionResult executeCreateIndex(NodeExecutionContext context, String baseUrl,
			String index, Map<String, String> headers) throws Exception {
		String settingsJson = context.getParameter("indexSettings", "{}");
		Map<String, Object> settings = parseJson(settingsJson);

		String url = baseUrl + "/" + encode(index);
		HttpResponse<String> response = put(url, settings, headers);
		return toResult(response);
	}

	private NodeExecutionResult executeDeleteIndex(String baseUrl, String index,
			Map<String, String> headers) throws Exception {
		String url = baseUrl + "/" + encode(index);
		HttpResponse<String> response = delete(url, headers);

		if (response.statusCode() >= 400) {
			return esError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "index", index))));
	}

	// ========================= Helpers =========================

	@Override
	protected String getBaseUrl(Map<String, Object> credentials) {
		String baseUrl = String.valueOf(credentials.getOrDefault("baseUrl",
			credentials.getOrDefault("url", "http://localhost:9200")));
		// Remove trailing slash
		if (baseUrl.endsWith("/")) {
			baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		}
		return baseUrl;
	}

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		String username = String.valueOf(credentials.getOrDefault("username", ""));
		String password = String.valueOf(credentials.getOrDefault("password", ""));
		String apiKey = String.valueOf(credentials.getOrDefault("apiKey", ""));

		if (!apiKey.isEmpty()) {
			headers.put("Authorization", "ApiKey " + apiKey);
		} else if (!username.isEmpty() && !password.isEmpty()) {
			String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
			headers.put("Authorization", "Basic " + auth);
		}

		return headers;
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return esError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult esError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Elasticsearch API error (HTTP " + response.statusCode() + "): " + body);
	}

	@SuppressWarnings("unchecked")
	private List<Object> parseJsonToList(String json) {
		try {
			if (json == null || json.isBlank()) return List.of();
			if (json.trim().startsWith("[")) {
				return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<Object>>() {});
			}
			return List.of();
		} catch (Exception e) {
			return List.of();
		}
	}
}
