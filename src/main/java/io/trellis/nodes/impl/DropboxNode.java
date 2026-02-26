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
	type = "dropbox",
	displayName = "Dropbox",
	description = "Manage files and folders in Dropbox.",
	category = "Cloud Storage",
	icon = "dropbox",
	credentials = {"dropboxOAuth2Api"}
)
public class DropboxNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.dropboxapi.com/2";
	private static final String CONTENT_URL = "https://content.dropboxapi.com/2";

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("file")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("File").value("file").description("Manage files in Dropbox").build(),
				ParameterOption.builder().name("Folder").value("folder").description("Manage folders in Dropbox").build()
			)).build());

		// File operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("download")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"))))
			.options(List.of(
				ParameterOption.builder().name("Copy").value("copy").description("Copy a file").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a file").build(),
				ParameterOption.builder().name("Download").value("download").description("Download a file").build(),
				ParameterOption.builder().name("Move").value("move").description("Move a file").build(),
				ParameterOption.builder().name("Search").value("search").description("Search for files").build(),
				ParameterOption.builder().name("Upload").value("upload").description("Upload a file").build()
			)).build());

		// Folder operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("folder"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a folder").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a folder").build(),
				ParameterOption.builder().name("Get All").value("getAll").description("List folder contents").build(),
				ParameterOption.builder().name("Search").value("search").description("Search for folders").build()
			)).build());

		addFileParameters(params);
		addFolderParameters(params);

		return params;
	}

	private void addFileParameters(List<NodeParameter> params) {
		// File path (for copy, delete, download, move)
		params.add(NodeParameter.builder()
			.name("filePath").displayName("File Path").type(ParameterType.STRING).required(true)
			.placeHolder("/Documents/example.txt")
			.description("The path of the file in Dropbox.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("copy", "delete", "download", "move"))))
			.build());

		// Copy/Move: destination path
		params.add(NodeParameter.builder()
			.name("toPath").displayName("Destination Path").type(ParameterType.STRING).required(true)
			.placeHolder("/Documents/copy_of_example.txt")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("copy", "move"))))
			.build());

		// Search: query
		params.add(NodeParameter.builder()
			.name("searchQuery").displayName("Search Query").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("search"))))
			.build());

		// Search: limit
		params.add(NodeParameter.builder()
			.name("searchLimit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(50)
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("search"))))
			.build());

		// Upload: path
		params.add(NodeParameter.builder()
			.name("uploadPath").displayName("Upload Path").type(ParameterType.STRING).required(true)
			.placeHolder("/Documents/uploaded_file.txt")
			.description("The path where the file will be uploaded in Dropbox.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("upload"))))
			.build());

		// Upload: binary data flag
		params.add(NodeParameter.builder()
			.name("binaryData").displayName("Binary Data").type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Use binary data from the input.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("upload"))))
			.build());

		// Upload: binary property name
		params.add(NodeParameter.builder()
			.name("binaryPropertyName").displayName("Binary Property").type(ParameterType.STRING).defaultValue("data")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("upload"), "binaryData", List.of(true))))
			.build());

		// Upload: file content
		params.add(NodeParameter.builder()
			.name("fileContent").displayName("File Content").type(ParameterType.STRING)
			.typeOptions(Map.of("rows", 5))
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("upload"), "binaryData", List.of(false))))
			.build());

		// Upload: write mode
		params.add(NodeParameter.builder()
			.name("writeMode").displayName("Write Mode").type(ParameterType.OPTIONS).defaultValue("add")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("upload"))))
			.options(List.of(
				ParameterOption.builder().name("Add").value("add").description("Do not overwrite existing file").build(),
				ParameterOption.builder().name("Overwrite").value("overwrite").description("Overwrite existing file").build()
			)).build());
	}

	private void addFolderParameters(List<NodeParameter> params) {
		// Folder path (for create, delete, getAll)
		params.add(NodeParameter.builder()
			.name("folderPath").displayName("Folder Path").type(ParameterType.STRING).required(true)
			.placeHolder("/Documents")
			.displayOptions(Map.of("show", Map.of("resource", List.of("folder"), "operation", List.of("create", "delete", "getAll"))))
			.build());

		// GetAll: recursive
		params.add(NodeParameter.builder()
			.name("recursive").displayName("Recursive").type(ParameterType.BOOLEAN).defaultValue(false)
			.displayOptions(Map.of("show", Map.of("resource", List.of("folder"), "operation", List.of("getAll"))))
			.build());

		// GetAll: limit
		params.add(NodeParameter.builder()
			.name("folderLimit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(100)
			.displayOptions(Map.of("show", Map.of("resource", List.of("folder"), "operation", List.of("getAll"))))
			.build());

		// Search: query
		params.add(NodeParameter.builder()
			.name("folderSearchQuery").displayName("Search Query").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("folder"), "operation", List.of("search"))))
			.build());
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "file");
		Map<String, Object> credentials = context.getCredentials();

		try {
			return switch (resource) {
				case "file" -> executeFile(context, credentials);
				case "folder" -> executeFolder(context, credentials);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Dropbox API error: " + e.getMessage(), e);
		}
	}

	// ========================= File Operations =========================

	private NodeExecutionResult executeFile(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "download");
		Map<String, String> headers = getAuthHeaders(credentials);

		switch (operation) {
			case "copy": {
				String fromPath = context.getParameter("filePath", "");
				String toPath = context.getParameter("toPath", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("from_path", fromPath);
				body.put("to_path", toPath);

				HttpResponse<String> response = post(BASE_URL + "/files/copy_v2", body, headers);
				return toResult(response);
			}
			case "delete": {
				String path = context.getParameter("filePath", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("path", path);

				HttpResponse<String> response = post(BASE_URL + "/files/delete_v2", body, headers);
				return toResult(response);
			}
			case "download": {
				String path = context.getParameter("filePath", "");

				Map<String, String> downloadHeaders = new LinkedHashMap<>(headers);
				downloadHeaders.put("Dropbox-API-Arg", toJson(Map.of("path", path)));

				HttpResponse<String> response = post(CONTENT_URL + "/files/download", "", downloadHeaders);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("content", response.body());
				result.put("statusCode", response.statusCode());
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "move": {
				String fromPath = context.getParameter("filePath", "");
				String toPath = context.getParameter("toPath", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("from_path", fromPath);
				body.put("to_path", toPath);

				HttpResponse<String> response = post(BASE_URL + "/files/move_v2", body, headers);
				return toResult(response);
			}
			case "search": {
				String query = context.getParameter("searchQuery", "");
				int limit = toInt(context.getParameter("searchLimit", 50), 50);

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("query", query);
				Map<String, Object> options = new LinkedHashMap<>();
				options.put("max_results", limit);
				options.put("file_status", "active");
				body.put("options", options);

				HttpResponse<String> response = post(BASE_URL + "/files/search_v2", body, headers);
				return toSearchResult(response);
			}
			case "upload": {
				String path = context.getParameter("uploadPath", "");
				String fileContent = context.getParameter("fileContent", "");
				String writeMode = context.getParameter("writeMode", "add");

				Map<String, Object> arg = new LinkedHashMap<>();
				arg.put("path", path);
				arg.put("mode", writeMode);
				arg.put("autorename", true);

				Map<String, String> uploadHeaders = new LinkedHashMap<>();
				uploadHeaders.put("Authorization", headers.get("Authorization"));
				uploadHeaders.put("Content-Type", "application/octet-stream");
				uploadHeaders.put("Dropbox-API-Arg", toJson(arg));

				HttpResponse<String> response = post(CONTENT_URL + "/files/upload", fileContent, uploadHeaders);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown file operation: " + operation);
		}
	}

	// ========================= Folder Operations =========================

	private NodeExecutionResult executeFolder(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		Map<String, String> headers = getAuthHeaders(credentials);

		switch (operation) {
			case "create": {
				String path = context.getParameter("folderPath", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("path", path);
				body.put("autorename", false);

				HttpResponse<String> response = post(BASE_URL + "/files/create_folder_v2", body, headers);
				return toResult(response);
			}
			case "delete": {
				String path = context.getParameter("folderPath", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("path", path);

				HttpResponse<String> response = post(BASE_URL + "/files/delete_v2", body, headers);
				return toResult(response);
			}
			case "getAll": {
				String path = context.getParameter("folderPath", "");
				boolean recursive = toBoolean(context.getParameter("recursive", false), false);
				int limit = toInt(context.getParameter("folderLimit", 100), 100);

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("path", path.isEmpty() ? "" : path);
				body.put("recursive", recursive);
				body.put("limit", limit);

				HttpResponse<String> response = post(BASE_URL + "/files/list_folder", body, headers);
				return toListResult(response, "entries");
			}
			case "search": {
				String query = context.getParameter("folderSearchQuery", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("query", query);
				Map<String, Object> options = new LinkedHashMap<>();
				options.put("file_status", "active");
				options.put("filename_only", false);
				body.put("options", options);

				HttpResponse<String> response = post(BASE_URL + "/files/search_v2", body, headers);
				return toSearchResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown folder operation: " + operation);
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
			return dropboxError(response);
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
			return dropboxError(response);
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

	@SuppressWarnings("unchecked")
	private NodeExecutionResult toSearchResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return dropboxError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		Object matches = parsed.get("matches");
		if (matches instanceof List) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Object match : (List<?>) matches) {
				if (match instanceof Map) {
					Map<String, Object> matchMap = (Map<String, Object>) match;
					Object metadata = matchMap.get("metadata");
					if (metadata instanceof Map) {
						Map<String, Object> metadataMap = (Map<String, Object>) metadata;
						Object inner = metadataMap.get("metadata");
						if (inner instanceof Map) {
							items.add(wrapInJson(inner));
						} else {
							items.add(wrapInJson(metadataMap));
						}
					}
				}
			}
			return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult dropboxError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Dropbox API error (HTTP " + response.statusCode() + "): " + body);
	}
}
