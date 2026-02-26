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
 * Google Cloud Firestore Node -- manage Firestore documents via the REST API.
 */
@Slf4j
@Node(
	type = "googleFirebaseCloudFirestore",
	displayName = "Google Cloud Firestore",
	description = "Manage Google Cloud Firestore documents",
	category = "Data & Storage / Databases",
	icon = "googleFirebaseCloudFirestore",
	credentials = {"googleFirebaseCloudFirestoreOAuth2Api"}
)
public class GoogleFirebaseCloudFirestoreNode extends AbstractApiNode {

	private static final String BASE_URL = "https://firestore.googleapis.com/v1";

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

		// Resource selector
		params.add(NodeParameter.builder()
			.name("resource").displayName("Resource")
			.type(ParameterType.OPTIONS).required(true).defaultValue("document")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Document").value("document").description("Manage Firestore documents").build()
			)).build());

		// Document operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("document"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a document").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a document").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a document").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all documents in a collection").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a document").build(),
				ParameterOption.builder().name("Query").value("query").description("Run a structured query").build()
			)).build());

		// Common parameters
		params.add(NodeParameter.builder()
			.name("projectId").displayName("Project ID")
			.type(ParameterType.STRING).required(true)
			.description("The Google Cloud project ID.")
			.build());

		params.add(NodeParameter.builder()
			.name("database").displayName("Database")
			.type(ParameterType.STRING).defaultValue("(default)")
			.description("The Firestore database ID. Use '(default)' for the default database.")
			.build());

		// Collection path
		params.add(NodeParameter.builder()
			.name("collection").displayName("Collection")
			.type(ParameterType.STRING).required(true)
			.description("The collection path (e.g., 'users' or 'users/uid/subcollection').")
			.placeHolder("users")
			.build());

		// Document ID (for get, delete, update)
		params.add(NodeParameter.builder()
			.name("documentId").displayName("Document ID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("operation", List.of("get", "delete", "update"))))
			.build());

		// Document ID for create (optional -- auto-generated if blank)
		params.add(NodeParameter.builder()
			.name("createDocumentId").displayName("Document ID")
			.type(ParameterType.STRING)
			.description("Optional document ID. If blank, one will be auto-generated.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create"))))
			.build());

		// Fields JSON (for create, update)
		params.add(NodeParameter.builder()
			.name("fieldsJson").displayName("Fields (JSON)")
			.type(ParameterType.JSON).required(true)
			.description("The document fields as a JSON object. Values are automatically converted to Firestore value format.")
			.placeHolder("{\"name\": \"John\", \"age\": 30}")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create", "update"))))
			.build());

		// Update mask (for update)
		params.add(NodeParameter.builder()
			.name("updateMask").displayName("Update Mask")
			.type(ParameterType.STRING)
			.description("Comma-separated list of field paths to update. If empty, all fields are replaced.")
			.placeHolder("name,email")
			.displayOptions(Map.of("show", Map.of("operation", List.of("update"))))
			.build());

		// GetAll options
		params.add(NodeParameter.builder()
			.name("listOptions").displayName("Options")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("pageSize").displayName("Page Size").type(ParameterType.NUMBER)
					.defaultValue(100).description("Maximum number of documents to return.").build(),
				NodeParameter.builder().name("orderBy").displayName("Order By").type(ParameterType.STRING)
					.description("Field path to order results by.").build()
			)).build());

		// Query: structured query JSON
		params.add(NodeParameter.builder()
			.name("queryJson").displayName("Structured Query (JSON)")
			.type(ParameterType.JSON).required(true)
			.typeOptions(Map.of("rows", 8))
			.description("The structured query in JSON format. See Firestore REST API documentation for the query format.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("query"))))
			.build());

		return params;
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String operation = context.getParameter("operation", "get");

		try {
			String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));
			Map<String, String> headers = getAuthHeaders(accessToken);

			String projectId = context.getParameter("projectId", "");
			String database = context.getParameter("database", "(default)");
			String collection = context.getParameter("collection", "");
			String basePath = BASE_URL + "/projects/" + encode(projectId)
				+ "/databases/" + encode(database) + "/documents";

			return switch (operation) {
				case "create" -> executeCreate(context, basePath, collection, headers);
				case "delete" -> executeDelete(context, basePath, collection, headers);
				case "get" -> executeGet(context, basePath, collection, headers);
				case "getAll" -> executeGetAll(context, basePath, collection, headers);
				case "update" -> executeUpdate(context, basePath, collection, headers);
				case "query" -> executeQuery(context, basePath, headers);
				default -> NodeExecutionResult.error("Unknown operation: " + operation);
			};
		} catch (Exception e) {
			return handleError(context, "Google Cloud Firestore API error: " + e.getMessage(), e);
		}
	}

	// ========================= Document Operations =========================

	private NodeExecutionResult executeCreate(NodeExecutionContext context, String basePath,
			String collection, Map<String, String> headers) throws Exception {
		String fieldsJson = context.getParameter("fieldsJson", "{}");
		String documentId = context.getParameter("createDocumentId", "");

		Map<String, Object> fields = parseJsonObject(fieldsJson);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("fields", toFirestoreFields(fields));

		String url = basePath + "/" + collection;
		if (documentId != null && !documentId.isEmpty()) {
			url += "?documentId=" + encode(documentId);
		}

		HttpResponse<String> response = post(url, body, headers);
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult executeDelete(NodeExecutionContext context, String basePath,
			String collection, Map<String, String> headers) throws Exception {
		String documentId = context.getParameter("documentId", "");
		String url = basePath + "/" + collection + "/" + encode(documentId);

		HttpResponse<String> response = delete(url, headers);
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "documentId", documentId))));
	}

	private NodeExecutionResult executeGet(NodeExecutionContext context, String basePath,
			String collection, Map<String, String> headers) throws Exception {
		String documentId = context.getParameter("documentId", "");
		String url = basePath + "/" + collection + "/" + encode(documentId);

		HttpResponse<String> response = get(url, headers);
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeGetAll(NodeExecutionContext context, String basePath,
			String collection, Map<String, String> headers) throws Exception {
		Map<String, Object> options = context.getParameter("listOptions", Map.of());
		Map<String, Object> queryParams = new LinkedHashMap<>();
		if (options.get("pageSize") != null) queryParams.put("pageSize", options.get("pageSize"));
		if (options.get("orderBy") != null) queryParams.put("orderBy", options.get("orderBy"));

		String url = buildUrl(basePath + "/" + collection, queryParams);
		HttpResponse<String> response = get(url, headers);
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		Object documents = parsed.get("documents");
		if (documents instanceof List) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Object doc : (List<?>) documents) {
				if (doc instanceof Map) {
					items.add(wrapInJson(doc));
				}
			}
			return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult executeUpdate(NodeExecutionContext context, String basePath,
			String collection, Map<String, String> headers) throws Exception {
		String documentId = context.getParameter("documentId", "");
		String fieldsJson = context.getParameter("fieldsJson", "{}");
		String updateMask = context.getParameter("updateMask", "");

		Map<String, Object> fields = parseJsonObject(fieldsJson);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("fields", toFirestoreFields(fields));

		String url = basePath + "/" + collection + "/" + encode(documentId);
		if (updateMask != null && !updateMask.isEmpty()) {
			StringBuilder sb = new StringBuilder(url);
			String[] maskFields = updateMask.split(",");
			for (int i = 0; i < maskFields.length; i++) {
				sb.append(i == 0 ? "?" : "&");
				sb.append("updateMask.fieldPaths=").append(encode(maskFields[i].trim()));
			}
			url = sb.toString();
		}

		HttpResponse<String> response = patch(url, body, headers);
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeQuery(NodeExecutionContext context, String basePath,
			Map<String, String> headers) throws Exception {
		String queryJson = context.getParameter("queryJson", "{}");
		Map<String, Object> queryBody = parseJsonObject(queryJson);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("structuredQuery", queryBody);

		String url = basePath + ":runQuery";
		HttpResponse<String> response = post(url, body, headers);
		if (response.statusCode() >= 400) {
			return apiError(response);
		}

		List<Map<String, Object>> results = parseArrayResponse(response);
		if (results.isEmpty()) {
			return NodeExecutionResult.empty();
		}

		List<Map<String, Object>> items = new ArrayList<>();
		for (Map<String, Object> result : results) {
			Object document = result.get("document");
			if (document instanceof Map) {
				items.add(wrapInJson(document));
			} else {
				items.add(wrapInJson(result));
			}
		}
		return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(String accessToken) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}

	/**
	 * Converts a plain JSON map into Firestore value format.
	 * e.g. {"name": "John"} -> {"name": {"stringValue": "John"}}
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> toFirestoreFields(Map<String, Object> fields) {
		Map<String, Object> firestoreFields = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : fields.entrySet()) {
			firestoreFields.put(entry.getKey(), toFirestoreValue(entry.getValue()));
		}
		return firestoreFields;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> toFirestoreValue(Object value) {
		if (value == null) {
			return Map.of("nullValue", "NULL_VALUE");
		} else if (value instanceof String) {
			return Map.of("stringValue", value);
		} else if (value instanceof Boolean) {
			return Map.of("booleanValue", value);
		} else if (value instanceof Integer || value instanceof Long) {
			return Map.of("integerValue", String.valueOf(value));
		} else if (value instanceof Number) {
			return Map.of("doubleValue", ((Number) value).doubleValue());
		} else if (value instanceof Map) {
			Map<String, Object> mapFields = toFirestoreFields((Map<String, Object>) value);
			return Map.of("mapValue", Map.of("fields", mapFields));
		} else if (value instanceof List) {
			List<Map<String, Object>> arrayValues = new ArrayList<>();
			for (Object item : (List<?>) value) {
				arrayValues.add(toFirestoreValue(item));
			}
			return Map.of("arrayValue", Map.of("values", arrayValues));
		} else {
			return Map.of("stringValue", String.valueOf(value));
		}
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Google Cloud Firestore API error (HTTP " + response.statusCode() + "): " + body);
	}
}
