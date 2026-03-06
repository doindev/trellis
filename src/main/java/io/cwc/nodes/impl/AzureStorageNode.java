package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
	type = "azureStorage",
	displayName = "Azure Storage",
	description = "Manage containers and blobs in Azure Blob Storage.",
	category = "Cloud Storage",
	icon = "azureStorage",
	credentials = {"azureStorageApi"}
)
public class AzureStorageNode extends AbstractApiNode {

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("blob")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Container").value("container").description("Manage Azure Blob Storage containers").build(),
				ParameterOption.builder().name("Blob").value("blob").description("Manage blobs in Azure Blob Storage").build()
			)).build());

		// Container operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("container"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a container").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a container").build(),
				ParameterOption.builder().name("Get All").value("getAll").description("List all containers").build()
			)).build());

		// Blob operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("blob"))))
			.options(List.of(
				ParameterOption.builder().name("Delete").value("delete").description("Delete a blob").build(),
				ParameterOption.builder().name("Download").value("download").description("Download a blob").build(),
				ParameterOption.builder().name("Get All").value("getAll").description("List blobs in a container").build(),
				ParameterOption.builder().name("Upload").value("upload").description("Upload a blob").build()
			)).build());

		addContainerParameters(params);
		addBlobParameters(params);

		return params;
	}

	private void addContainerParameters(List<NodeParameter> params) {
		// Container name (create, delete)
		params.add(NodeParameter.builder()
			.name("containerName").displayName("Container Name").type(ParameterType.STRING).required(true)
			.placeHolder("my-container")
			.displayOptions(Map.of("show", Map.of("resource", List.of("container"), "operation", List.of("create", "delete"))))
			.build());

		// Create: public access level
		params.add(NodeParameter.builder()
			.name("publicAccessLevel").displayName("Public Access Level").type(ParameterType.OPTIONS).defaultValue("none")
			.displayOptions(Map.of("show", Map.of("resource", List.of("container"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("None (Private)").value("none").description("No public access").build(),
				ParameterOption.builder().name("Blob").value("blob").description("Public read access for blobs only").build(),
				ParameterOption.builder().name("Container").value("container").description("Public read and list access for entire container").build()
			)).build());

		// GetAll: prefix
		params.add(NodeParameter.builder()
			.name("containerPrefix").displayName("Prefix").type(ParameterType.STRING)
			.description("Filter containers by prefix.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("container"), "operation", List.of("getAll"))))
			.build());

		// GetAll: max results
		params.add(NodeParameter.builder()
			.name("containerMaxResults").displayName("Max Results").type(ParameterType.NUMBER).defaultValue(100)
			.displayOptions(Map.of("show", Map.of("resource", List.of("container"), "operation", List.of("getAll"))))
			.build());
	}

	private void addBlobParameters(List<NodeParameter> params) {
		// Container name (all blob operations)
		params.add(NodeParameter.builder()
			.name("blobContainer").displayName("Container Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("blob"))))
			.build());

		// Blob name (delete, download, upload)
		params.add(NodeParameter.builder()
			.name("blobName").displayName("Blob Name").type(ParameterType.STRING).required(true)
			.placeHolder("path/to/file.txt")
			.description("The name (path) of the blob.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("blob"), "operation", List.of("delete", "download", "upload"))))
			.build());

		// GetAll: prefix
		params.add(NodeParameter.builder()
			.name("blobPrefix").displayName("Prefix").type(ParameterType.STRING)
			.description("Filter blobs by prefix.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("blob"), "operation", List.of("getAll"))))
			.build());

		// GetAll: max results
		params.add(NodeParameter.builder()
			.name("blobMaxResults").displayName("Max Results").type(ParameterType.NUMBER).defaultValue(100)
			.displayOptions(Map.of("show", Map.of("resource", List.of("blob"), "operation", List.of("getAll"))))
			.build());

		// Upload: content type
		params.add(NodeParameter.builder()
			.name("blobContentType").displayName("Content Type").type(ParameterType.STRING).defaultValue("application/octet-stream")
			.displayOptions(Map.of("show", Map.of("resource", List.of("blob"), "operation", List.of("upload"))))
			.build());

		// Upload: binary data flag
		params.add(NodeParameter.builder()
			.name("binaryData").displayName("Binary Data").type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Use binary data from the input.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("blob"), "operation", List.of("upload"))))
			.build());

		// Upload: binary property name
		params.add(NodeParameter.builder()
			.name("binaryPropertyName").displayName("Binary Property").type(ParameterType.STRING).defaultValue("data")
			.displayOptions(Map.of("show", Map.of("resource", List.of("blob"), "operation", List.of("upload"), "binaryData", List.of(true))))
			.build());

		// Upload: file content
		params.add(NodeParameter.builder()
			.name("fileContent").displayName("File Content").type(ParameterType.STRING)
			.typeOptions(Map.of("rows", 5))
			.displayOptions(Map.of("show", Map.of("resource", List.of("blob"), "operation", List.of("upload"), "binaryData", List.of(false))))
			.build());

		// Upload: blob type
		params.add(NodeParameter.builder()
			.name("blobType").displayName("Blob Type").type(ParameterType.OPTIONS).defaultValue("BlockBlob")
			.displayOptions(Map.of("show", Map.of("resource", List.of("blob"), "operation", List.of("upload"))))
			.options(List.of(
				ParameterOption.builder().name("Block Blob").value("BlockBlob").build(),
				ParameterOption.builder().name("Append Blob").value("AppendBlob").build(),
				ParameterOption.builder().name("Page Blob").value("PageBlob").build()
			)).build());
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "blob");
		Map<String, Object> credentials = context.getCredentials();

		try {
			return switch (resource) {
				case "container" -> executeContainer(context, credentials);
				case "blob" -> executeBlob(context, credentials);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Azure Storage API error: " + e.getMessage(), e);
		}
	}

	// ========================= Container Operations =========================

	private NodeExecutionResult executeContainer(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		Map<String, String> headers = getAuthHeaders(credentials);
		String baseUrl = getBaseUrl(credentials);

		switch (operation) {
			case "create": {
				String containerName = context.getParameter("containerName", "");
				String publicAccess = context.getParameter("publicAccessLevel", "none");

				String url = baseUrl + "/" + encode(containerName) + "?restype=container";
				Map<String, String> createHeaders = new LinkedHashMap<>(headers);
				if (!"none".equals(publicAccess)) {
					createHeaders.put("x-ms-blob-public-access", publicAccess);
				}

				HttpResponse<String> response = put(url, "", createHeaders);
				return toResult(response);
			}
			case "delete": {
				String containerName = context.getParameter("containerName", "");
				String url = baseUrl + "/" + encode(containerName) + "?restype=container";
				HttpResponse<String> response = delete(url, headers);
				return toDeleteResult(response);
			}
			case "getAll": {
				String prefix = context.getParameter("containerPrefix", "");
				int maxResults = toInt(context.getParameter("containerMaxResults", 100), 100);

				String url = baseUrl + "/?comp=list&maxresults=" + maxResults;
				if (!prefix.isEmpty()) url += "&prefix=" + encode(prefix);

				HttpResponse<String> response = get(url, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown container operation: " + operation);
		}
	}

	// ========================= Blob Operations =========================

	private NodeExecutionResult executeBlob(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		Map<String, String> headers = getAuthHeaders(credentials);
		String baseUrl = getBaseUrl(credentials);

		switch (operation) {
			case "delete": {
				String container = context.getParameter("blobContainer", "");
				String blobName = context.getParameter("blobName", "");
				String url = baseUrl + "/" + encode(container) + "/" + blobName;
				HttpResponse<String> response = delete(url, headers);
				return toDeleteResult(response);
			}
			case "download": {
				String container = context.getParameter("blobContainer", "");
				String blobName = context.getParameter("blobName", "");
				String url = baseUrl + "/" + encode(container) + "/" + blobName;
				HttpResponse<String> response = get(url, headers);

				if (response.statusCode() >= 400) {
					return azureError(response);
				}

				Map<String, Object> result = new LinkedHashMap<>();
				result.put("content", response.body());
				result.put("blobName", blobName);
				result.put("container", container);
				result.put("statusCode", response.statusCode());
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "getAll": {
				String container = context.getParameter("blobContainer", "");
				String prefix = context.getParameter("blobPrefix", "");
				int maxResults = toInt(context.getParameter("blobMaxResults", 100), 100);

				String url = baseUrl + "/" + encode(container) + "?restype=container&comp=list&maxresults=" + maxResults;
				if (!prefix.isEmpty()) url += "&prefix=" + encode(prefix);

				HttpResponse<String> response = get(url, headers);
				return toResult(response);
			}
			case "upload": {
				String container = context.getParameter("blobContainer", "");
				String blobName = context.getParameter("blobName", "");
				String contentType = context.getParameter("blobContentType", "application/octet-stream");
				String fileContent = context.getParameter("fileContent", "");
				String blobType = context.getParameter("blobType", "BlockBlob");

				String url = baseUrl + "/" + encode(container) + "/" + blobName;
				Map<String, String> uploadHeaders = new LinkedHashMap<>(headers);
				uploadHeaders.put("Content-Type", contentType);
				uploadHeaders.put("x-ms-blob-type", blobType);

				HttpResponse<String> response = put(url, fileContent, uploadHeaders);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown blob operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		String accountKey = String.valueOf(credentials.getOrDefault("accountKey", ""));
		String sasToken = String.valueOf(credentials.getOrDefault("sasToken", ""));

		headers.put("x-ms-version", "2021-08-06");
		headers.put("x-ms-date", DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()));

		// If using a SAS token, it will be appended to the URL
		// If using an account key, set it in a header for the platform to sign
		if (!accountKey.isEmpty()) {
			headers.put("X-Azure-Account-Key", accountKey);
		}
		if (!sasToken.isEmpty()) {
			headers.put("X-Azure-Sas-Token", sasToken);
		}

		return headers;
	}

	@Override
	protected String getBaseUrl(Map<String, Object> credentials) {
		String accountName = String.valueOf(credentials.getOrDefault("accountName", ""));
		String customEndpoint = String.valueOf(credentials.getOrDefault("endpoint", ""));

		if (!customEndpoint.isEmpty()) {
			return customEndpoint.endsWith("/") ? customEndpoint.substring(0, customEndpoint.length() - 1) : customEndpoint;
		}

		return "https://" + accountName + ".blob.core.windows.net";
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return azureError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		// Azure returns XML for many operations; try JSON first, fall back to raw
		try {
			Map<String, Object> parsed = parseResponse(response);
			return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
		} catch (Exception e) {
			// Response is likely XML; return as raw content
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("content", body);
			result.put("statusCode", response.statusCode());
			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		}
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return azureError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
	}

	private NodeExecutionResult azureError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Azure Storage API error (HTTP " + response.statusCode() + "): " + body);
	}
}
