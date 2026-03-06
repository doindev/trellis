package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Node(
	type = "s3",
	displayName = "S3",
	description = "Generic S3-compatible storage. Works with MinIO, DigitalOcean Spaces, Backblaze B2, and other S3-compatible services.",
	category = "AWS",
	icon = "s3",
	credentials = {"s3Api"}
)
public class S3Node extends AbstractApiNode {

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("object")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Bucket").value("bucket").description("Manage S3 buckets").build(),
				ParameterOption.builder().name("Object").value("object").description("Manage S3 objects").build()
			)).build());

		// Bucket operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("bucket"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a bucket").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a bucket").build(),
				ParameterOption.builder().name("Get All").value("getAll").description("List all buckets").build()
			)).build());

		// Object operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"))))
			.options(List.of(
				ParameterOption.builder().name("Copy").value("copy").description("Copy an object").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an object").build(),
				ParameterOption.builder().name("Download").value("download").description("Download an object").build(),
				ParameterOption.builder().name("Get All").value("getAll").description("List objects in a bucket").build(),
				ParameterOption.builder().name("Upload").value("upload").description("Upload an object").build()
			)).build());

		addBucketParameters(params);
		addObjectParameters(params);

		return params;
	}

	private void addBucketParameters(List<NodeParameter> params) {
		// Create: bucket name
		params.add(NodeParameter.builder()
			.name("bucketName").displayName("Bucket Name").type(ParameterType.STRING).required(true)
			.placeHolder("my-bucket")
			.displayOptions(Map.of("show", Map.of("resource", List.of("bucket"), "operation", List.of("create", "delete"))))
			.build());

		// Create: region
		params.add(NodeParameter.builder()
			.name("region").displayName("Region").type(ParameterType.STRING)
			.description("The region for the bucket. Provider-specific.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("bucket"), "operation", List.of("create"))))
			.build());
	}

	private void addObjectParameters(List<NodeParameter> params) {
		// Bucket name (all object operations)
		params.add(NodeParameter.builder()
			.name("objectBucket").displayName("Bucket Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"))))
			.build());

		// Object key (for copy, delete, download)
		params.add(NodeParameter.builder()
			.name("objectKey").displayName("Object Key").type(ParameterType.STRING).required(true)
			.placeHolder("path/to/file.txt")
			.description("The key (path) of the object.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"), "operation", List.of("copy", "delete", "download"))))
			.build());

		// Copy: destination bucket
		params.add(NodeParameter.builder()
			.name("destinationBucket").displayName("Destination Bucket").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"), "operation", List.of("copy"))))
			.build());

		// Copy: destination key
		params.add(NodeParameter.builder()
			.name("destinationKey").displayName("Destination Key").type(ParameterType.STRING).required(true)
			.placeHolder("path/to/destination.txt")
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"), "operation", List.of("copy"))))
			.build());

		// GetAll: prefix
		params.add(NodeParameter.builder()
			.name("prefix").displayName("Prefix").type(ParameterType.STRING)
			.description("Filter objects by prefix.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"), "operation", List.of("getAll"))))
			.build());

		// GetAll: max keys
		params.add(NodeParameter.builder()
			.name("maxKeys").displayName("Max Keys").type(ParameterType.NUMBER).defaultValue(100)
			.description("Maximum number of objects to return.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"), "operation", List.of("getAll"))))
			.build());

		// Upload: key
		params.add(NodeParameter.builder()
			.name("uploadKey").displayName("Object Key").type(ParameterType.STRING).required(true)
			.placeHolder("path/to/file.txt")
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"), "operation", List.of("upload"))))
			.build());

		// Upload: content type
		params.add(NodeParameter.builder()
			.name("contentType").displayName("Content Type").type(ParameterType.STRING).defaultValue("application/octet-stream")
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"), "operation", List.of("upload"))))
			.build());

		// Upload: binary data flag
		params.add(NodeParameter.builder()
			.name("binaryData").displayName("Binary Data").type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Use binary data from the input.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"), "operation", List.of("upload"))))
			.build());

		// Upload: binary property name
		params.add(NodeParameter.builder()
			.name("binaryPropertyName").displayName("Binary Property").type(ParameterType.STRING).defaultValue("data")
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"), "operation", List.of("upload"), "binaryData", List.of(true))))
			.build());

		// Upload: file content
		params.add(NodeParameter.builder()
			.name("fileContent").displayName("File Content").type(ParameterType.STRING)
			.typeOptions(Map.of("rows", 5))
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"), "operation", List.of("upload"), "binaryData", List.of(false))))
			.build());
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "object");
		Map<String, Object> credentials = context.getCredentials();

		try {
			return switch (resource) {
				case "bucket" -> executeBucket(context, credentials);
				case "object" -> executeObject(context, credentials);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "S3 API error: " + e.getMessage(), e);
		}
	}

	// ========================= Bucket Operations =========================

	private NodeExecutionResult executeBucket(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		Map<String, String> headers = getAuthHeaders(credentials);
		String endpointUrl = getEndpointUrl(credentials);

		switch (operation) {
			case "create": {
				String bucketName = context.getParameter("bucketName", "");
				String region = context.getParameter("region", "");

				String url = getS3BucketUrl(endpointUrl, bucketName);

				String body = "";
				if (!region.isEmpty()) {
					body = toJson(Map.of("CreateBucketConfiguration", Map.of("LocationConstraint", region)));
				}

				HttpResponse<String> response = put(url, body, headers);
				return toResult(response);
			}
			case "delete": {
				String bucketName = context.getParameter("bucketName", "");
				String url = getS3BucketUrl(endpointUrl, bucketName);
				HttpResponse<String> response = delete(url, headers);
				return toDeleteResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(endpointUrl, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown bucket operation: " + operation);
		}
	}

	// ========================= Object Operations =========================

	private NodeExecutionResult executeObject(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		Map<String, String> headers = getAuthHeaders(credentials);
		String endpointUrl = getEndpointUrl(credentials);

		switch (operation) {
			case "copy": {
				String bucket = context.getParameter("objectBucket", "");
				String key = context.getParameter("objectKey", "");
				String destBucket = context.getParameter("destinationBucket", "");
				String destKey = context.getParameter("destinationKey", "");

				String url = getS3ObjectUrl(endpointUrl, destBucket, destKey);
				Map<String, String> copyHeaders = new LinkedHashMap<>(headers);
				copyHeaders.put("x-amz-copy-source", "/" + bucket + "/" + key);

				HttpResponse<String> response = put(url, "", copyHeaders);
				return toResult(response);
			}
			case "delete": {
				String bucket = context.getParameter("objectBucket", "");
				String key = context.getParameter("objectKey", "");
				String url = getS3ObjectUrl(endpointUrl, bucket, key);
				HttpResponse<String> response = delete(url, headers);
				return toDeleteResult(response);
			}
			case "download": {
				String bucket = context.getParameter("objectBucket", "");
				String key = context.getParameter("objectKey", "");
				String url = getS3ObjectUrl(endpointUrl, bucket, key);
				HttpResponse<String> response = get(url, headers);

				if (response.statusCode() >= 400) {
					return s3Error(response);
				}

				Map<String, Object> result = new LinkedHashMap<>();
				result.put("content", response.body());
				result.put("key", key);
				result.put("bucket", bucket);
				result.put("statusCode", response.statusCode());
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "getAll": {
				String bucket = context.getParameter("objectBucket", "");
				String prefix = context.getParameter("prefix", "");
				int maxKeys = toInt(context.getParameter("maxKeys", 100), 100);

				String url = getS3BucketUrl(endpointUrl, bucket) + "?list-type=2&max-keys=" + maxKeys;
				if (!prefix.isEmpty()) url += "&prefix=" + encode(prefix);

				HttpResponse<String> response = get(url, headers);
				return toResult(response);
			}
			case "upload": {
				String bucket = context.getParameter("objectBucket", "");
				String key = context.getParameter("uploadKey", "");
				String contentType = context.getParameter("contentType", "application/octet-stream");
				String fileContent = context.getParameter("fileContent", "");

				String url = getS3ObjectUrl(endpointUrl, bucket, key);
				Map<String, String> uploadHeaders = new LinkedHashMap<>(headers);
				uploadHeaders.put("Content-Type", contentType);

				HttpResponse<String> response = put(url, fileContent, uploadHeaders);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown object operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		String accessKeyId = String.valueOf(credentials.getOrDefault("accessKeyId", ""));
		String secretAccessKey = String.valueOf(credentials.getOrDefault("secretAccessKey", ""));
		headers.put("Content-Type", "application/json");
		if (!accessKeyId.isEmpty()) {
			headers.put("X-Aws-Access-Key-Id", accessKeyId);
		}
		if (!secretAccessKey.isEmpty()) {
			headers.put("X-Aws-Secret-Access-Key", secretAccessKey);
		}
		String sessionToken = String.valueOf(credentials.getOrDefault("sessionToken", ""));
		if (!sessionToken.isEmpty()) {
			headers.put("X-Aws-Session-Token", sessionToken);
		}
		return headers;
	}

	private String getEndpointUrl(Map<String, Object> credentials) {
		String endpoint = String.valueOf(credentials.getOrDefault("endpoint", ""));
		if (endpoint.isEmpty()) {
			String region = String.valueOf(credentials.getOrDefault("region", "us-east-1"));
			return "https://s3." + region + ".amazonaws.com";
		}
		// Remove trailing slash
		return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
	}

	private String getS3BucketUrl(String endpointUrl, String bucket) {
		// Use path-style addressing for compatibility with non-AWS S3 providers
		return endpointUrl + "/" + bucket;
	}

	private String getS3ObjectUrl(String endpointUrl, String bucket, String key) {
		return endpointUrl + "/" + bucket + "/" + key;
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return s3Error(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return s3Error(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
	}

	private NodeExecutionResult s3Error(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("S3 API error (HTTP " + response.statusCode() + "): " + body);
	}
}
