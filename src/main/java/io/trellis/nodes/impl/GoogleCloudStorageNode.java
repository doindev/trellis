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
	type = "googleCloudStorage",
	displayName = "Google Cloud Storage",
	description = "Manage objects and buckets in Google Cloud Storage.",
	category = "Cloud Storage",
	icon = "googleCloudStorage",
	credentials = {"googleCloudStorageOAuth2Api"}
)
public class GoogleCloudStorageNode extends AbstractApiNode {

	private static final String BASE_URL = "https://storage.googleapis.com/storage/v1";
	private static final String UPLOAD_URL = "https://storage.googleapis.com/upload/storage/v1";

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("bucket")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Bucket").value("bucket").description("Manage GCS buckets").build(),
				ParameterOption.builder().name("Object").value("object").description("Manage GCS objects").build()
			)).build());

		// Bucket operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("bucket"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a bucket").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a bucket").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a bucket's metadata").build(),
				ParameterOption.builder().name("Get All").value("getAll").description("List all buckets").build()
			)).build());

		// Object operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Upload an object").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an object").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an object's metadata").build(),
				ParameterOption.builder().name("Get All").value("getAll").description("List all objects in a bucket").build()
			)).build());

		addBucketParameters(params);
		addObjectParameters(params);

		return params;
	}

	private void addBucketParameters(List<NodeParameter> params) {
		// Project ID (for create, getAll)
		params.add(NodeParameter.builder()
			.name("projectId").displayName("Project ID").type(ParameterType.STRING).required(true)
			.description("The Google Cloud project ID.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("bucket"), "operation", List.of("create", "getAll"))))
			.build());

		// Create: bucket name
		params.add(NodeParameter.builder()
			.name("bucketName").displayName("Bucket Name").type(ParameterType.STRING).required(true)
			.placeHolder("my-bucket")
			.displayOptions(Map.of("show", Map.of("resource", List.of("bucket"), "operation", List.of("create"))))
			.build());

		// Create: location
		params.add(NodeParameter.builder()
			.name("location").displayName("Location").type(ParameterType.STRING).defaultValue("US")
			.description("The location for the bucket (e.g., US, EU, ASIA).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("bucket"), "operation", List.of("create"))))
			.build());

		// Create: storage class
		params.add(NodeParameter.builder()
			.name("storageClass").displayName("Storage Class").type(ParameterType.OPTIONS).defaultValue("STANDARD")
			.displayOptions(Map.of("show", Map.of("resource", List.of("bucket"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Standard").value("STANDARD").build(),
				ParameterOption.builder().name("Nearline").value("NEARLINE").build(),
				ParameterOption.builder().name("Coldline").value("COLDLINE").build(),
				ParameterOption.builder().name("Archive").value("ARCHIVE").build()
			)).build());

		// Bucket name (for get, delete)
		params.add(NodeParameter.builder()
			.name("bucket").displayName("Bucket Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("bucket"), "operation", List.of("get", "delete"))))
			.build());

		// GetAll: max results
		params.add(NodeParameter.builder()
			.name("maxResults").displayName("Max Results").type(ParameterType.NUMBER).defaultValue(100)
			.displayOptions(Map.of("show", Map.of("resource", List.of("bucket"), "operation", List.of("getAll"))))
			.build());
	}

	private void addObjectParameters(List<NodeParameter> params) {
		// Bucket name (all object operations)
		params.add(NodeParameter.builder()
			.name("objectBucket").displayName("Bucket Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"))))
			.build());

		// Object name (for create, delete, get)
		params.add(NodeParameter.builder()
			.name("objectName").displayName("Object Name").type(ParameterType.STRING).required(true)
			.placeHolder("path/to/file.txt")
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"), "operation", List.of("create", "delete", "get"))))
			.build());

		// Create: content type
		params.add(NodeParameter.builder()
			.name("contentType").displayName("Content Type").type(ParameterType.STRING).defaultValue("application/octet-stream")
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"), "operation", List.of("create"))))
			.build());

		// Create: binary data flag
		params.add(NodeParameter.builder()
			.name("binaryData").displayName("Binary Data").type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Use binary data from the input.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"), "operation", List.of("create"))))
			.build());

		// Create: binary property name
		params.add(NodeParameter.builder()
			.name("binaryPropertyName").displayName("Binary Property").type(ParameterType.STRING).defaultValue("data")
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"), "operation", List.of("create"), "binaryData", List.of(true))))
			.build());

		// Create: file content
		params.add(NodeParameter.builder()
			.name("fileContent").displayName("File Content").type(ParameterType.STRING)
			.typeOptions(Map.of("rows", 5))
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"), "operation", List.of("create"), "binaryData", List.of(false))))
			.build());

		// GetAll: prefix
		params.add(NodeParameter.builder()
			.name("prefix").displayName("Prefix").type(ParameterType.STRING)
			.description("Filter objects by prefix.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"), "operation", List.of("getAll"))))
			.build());

		// GetAll: delimiter
		params.add(NodeParameter.builder()
			.name("delimiter").displayName("Delimiter").type(ParameterType.STRING).defaultValue("/")
			.description("Delimiter used to simulate folder hierarchy.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"), "operation", List.of("getAll"))))
			.build());

		// GetAll: max results
		params.add(NodeParameter.builder()
			.name("objectMaxResults").displayName("Max Results").type(ParameterType.NUMBER).defaultValue(100)
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"), "operation", List.of("getAll"))))
			.build());
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "bucket");
		Map<String, Object> credentials = context.getCredentials();

		try {
			return switch (resource) {
				case "bucket" -> executeBucket(context, credentials);
				case "object" -> executeObject(context, credentials);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Google Cloud Storage API error: " + e.getMessage(), e);
		}
	}

	// ========================= Bucket Operations =========================

	private NodeExecutionResult executeBucket(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		Map<String, String> headers = getAuthHeaders(credentials);

		switch (operation) {
			case "create": {
				String projectId = context.getParameter("projectId", "");
				String bucketName = context.getParameter("bucketName", "");
				String location = context.getParameter("location", "US");
				String storageClass = context.getParameter("storageClass", "STANDARD");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", bucketName);
				body.put("location", location);
				body.put("storageClass", storageClass);

				String url = BASE_URL + "/b?project=" + encode(projectId);
				HttpResponse<String> response = post(url, body, headers);
				return toResult(response);
			}
			case "delete": {
				String bucket = context.getParameter("bucket", "");
				HttpResponse<String> response = delete(BASE_URL + "/b/" + encode(bucket), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String bucket = context.getParameter("bucket", "");
				HttpResponse<String> response = get(BASE_URL + "/b/" + encode(bucket), headers);
				return toResult(response);
			}
			case "getAll": {
				String projectId = context.getParameter("projectId", "");
				int maxResults = toInt(context.getParameter("maxResults", 100), 100);
				String url = BASE_URL + "/b?project=" + encode(projectId) + "&maxResults=" + maxResults;
				HttpResponse<String> response = get(url, headers);
				return toListResult(response, "items");
			}
			default:
				return NodeExecutionResult.error("Unknown bucket operation: " + operation);
		}
	}

	// ========================= Object Operations =========================

	private NodeExecutionResult executeObject(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		Map<String, String> headers = getAuthHeaders(credentials);

		switch (operation) {
			case "create": {
				String bucket = context.getParameter("objectBucket", "");
				String objectName = context.getParameter("objectName", "");
				String contentType = context.getParameter("contentType", "application/octet-stream");
				String fileContent = context.getParameter("fileContent", "");

				Map<String, String> uploadHeaders = new LinkedHashMap<>(headers);
				uploadHeaders.put("Content-Type", contentType);

				String url = UPLOAD_URL + "/b/" + encode(bucket) + "/o?uploadType=media&name=" + encode(objectName);
				HttpResponse<String> response = post(url, fileContent, uploadHeaders);
				return toResult(response);
			}
			case "delete": {
				String bucket = context.getParameter("objectBucket", "");
				String objectName = context.getParameter("objectName", "");
				HttpResponse<String> response = delete(BASE_URL + "/b/" + encode(bucket) + "/o/" + encode(objectName), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String bucket = context.getParameter("objectBucket", "");
				String objectName = context.getParameter("objectName", "");
				HttpResponse<String> response = get(BASE_URL + "/b/" + encode(bucket) + "/o/" + encode(objectName), headers);
				return toResult(response);
			}
			case "getAll": {
				String bucket = context.getParameter("objectBucket", "");
				String prefix = context.getParameter("prefix", "");
				String delimiter = context.getParameter("delimiter", "/");
				int maxResults = toInt(context.getParameter("objectMaxResults", 100), 100);

				String url = BASE_URL + "/b/" + encode(bucket) + "/o?maxResults=" + maxResults;
				if (!prefix.isEmpty()) url += "&prefix=" + encode(prefix);
				if (!delimiter.isEmpty()) url += "&delimiter=" + encode(delimiter);

				HttpResponse<String> response = get(url, headers);
				return toListResult(response, "items");
			}
			default:
				return NodeExecutionResult.error("Unknown object operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		return headers;
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return gcsError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toListResult(HttpResponse<String> response, String dataKey) throws Exception {
		if (response.statusCode() >= 400) {
			return gcsError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		Object data = parsed.getOrDefault(dataKey, parsed);
		if (data instanceof List) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Object item : (List<?>) data) {
				if (item instanceof Map) {
					items.add(wrapInJson(item));
				}
			}
			return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return gcsError(response);
		}
		if (response.body() == null || response.body().isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		return toResult(response);
	}

	private NodeExecutionResult gcsError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Google Cloud Storage API error (HTTP " + response.statusCode() + "): " + body);
	}
}
