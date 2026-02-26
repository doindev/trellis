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
	type = "box",
	displayName = "Box",
	description = "Manage files and folders in Box.",
	category = "Cloud Storage",
	icon = "box",
	credentials = {"boxOAuth2Api"}
)
public class BoxNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.box.com/2.0";
	private static final String UPLOAD_URL = "https://upload.box.com/api/2.0";

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
				ParameterOption.builder().name("File").value("file").description("Manage files in Box").build(),
				ParameterOption.builder().name("Folder").value("folder").description("Manage folders in Box").build()
			)).build());

		// File operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"))))
			.options(List.of(
				ParameterOption.builder().name("Copy").value("copy").description("Copy a file").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a file").build(),
				ParameterOption.builder().name("Download").value("download").description("Download a file").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a file's metadata").build(),
				ParameterOption.builder().name("Search").value("search").description("Search for files").build(),
				ParameterOption.builder().name("Upload").value("upload").description("Upload a file").build()
			)).build());

		// Folder operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("folder"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a folder").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a folder").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a folder's metadata").build(),
				ParameterOption.builder().name("Get All").value("getAll").description("List all items in a folder").build(),
				ParameterOption.builder().name("Search").value("search").description("Search for folders").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a folder").build()
			)).build());

		addFileParameters(params);
		addFolderParameters(params);

		return params;
	}

	private void addFileParameters(List<NodeParameter> params) {
		// File ID (for copy, delete, download, get)
		params.add(NodeParameter.builder()
			.name("fileId").displayName("File ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("copy", "delete", "download", "get"))))
			.build());

		// Copy: destination folder ID
		params.add(NodeParameter.builder()
			.name("parentFolderId").displayName("Destination Folder ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("copy"))))
			.build());

		// Copy: new name
		params.add(NodeParameter.builder()
			.name("newName").displayName("New Name").type(ParameterType.STRING)
			.description("Optional new name for the copied file.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("copy"))))
			.build());

		// Search: query
		params.add(NodeParameter.builder()
			.name("searchQuery").displayName("Search Query").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("search"))))
			.build());

		// Search: limit
		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(50)
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("search"))))
			.build());

		// Upload: folder ID
		params.add(NodeParameter.builder()
			.name("uploadFolderId").displayName("Folder ID").type(ParameterType.STRING).required(true)
			.defaultValue("0")
			.description("The ID of the folder to upload to. Use '0' for the root folder.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("upload"))))
			.build());

		// Upload: file name
		params.add(NodeParameter.builder()
			.name("fileName").displayName("File Name").type(ParameterType.STRING).required(true)
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

		// Upload: file content (text)
		params.add(NodeParameter.builder()
			.name("fileContent").displayName("File Content").type(ParameterType.STRING)
			.typeOptions(Map.of("rows", 5))
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("upload"), "binaryData", List.of(false))))
			.build());
	}

	private void addFolderParameters(List<NodeParameter> params) {
		// Folder ID (for delete, get, getAll, update)
		params.add(NodeParameter.builder()
			.name("folderId").displayName("Folder ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("folder"), "operation", List.of("delete", "get", "getAll", "update"))))
			.build());

		// Create: name
		params.add(NodeParameter.builder()
			.name("folderName").displayName("Folder Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("folder"), "operation", List.of("create"))))
			.build());

		// Create: parent folder ID
		params.add(NodeParameter.builder()
			.name("parentId").displayName("Parent Folder ID").type(ParameterType.STRING).required(true)
			.defaultValue("0")
			.description("The ID of the parent folder. Use '0' for the root folder.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("folder"), "operation", List.of("create"))))
			.build());

		// Search: query
		params.add(NodeParameter.builder()
			.name("folderSearchQuery").displayName("Search Query").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("folder"), "operation", List.of("search"))))
			.build());

		// GetAll: limit
		params.add(NodeParameter.builder()
			.name("folderLimit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(100)
			.displayOptions(Map.of("show", Map.of("resource", List.of("folder"), "operation", List.of("getAll"))))
			.build());

		// Update: fields
		params.add(NodeParameter.builder()
			.name("updateFields").displayName("Update Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("folder"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("description").displayName("Description").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("canNonOwnersInvite").displayName("Can Non-Owners Invite")
					.type(ParameterType.BOOLEAN).build()
			)).build());
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
			return handleError(context, "Box API error: " + e.getMessage(), e);
		}
	}

	// ========================= File Operations =========================

	private NodeExecutionResult executeFile(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "get");
		Map<String, String> headers = getAuthHeaders(credentials);

		switch (operation) {
			case "copy": {
				String fileId = context.getParameter("fileId", "");
				String parentFolderId = context.getParameter("parentFolderId", "");
				String newName = context.getParameter("newName", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("parent", Map.of("id", parentFolderId));
				if (!newName.isEmpty()) {
					body.put("name", newName);
				}

				HttpResponse<String> response = post(BASE_URL + "/files/" + encode(fileId) + "/copy", body, headers);
				return toResult(response);
			}
			case "delete": {
				String fileId = context.getParameter("fileId", "");
				HttpResponse<String> response = delete(BASE_URL + "/files/" + encode(fileId), headers);
				return toDeleteResult(response);
			}
			case "download": {
				String fileId = context.getParameter("fileId", "");
				HttpResponse<String> response = get(BASE_URL + "/files/" + encode(fileId) + "/content", headers);
				return toResult(response);
			}
			case "get": {
				String fileId = context.getParameter("fileId", "");
				HttpResponse<String> response = get(BASE_URL + "/files/" + encode(fileId), headers);
				return toResult(response);
			}
			case "search": {
				String query = context.getParameter("searchQuery", "");
				int limit = toInt(context.getParameter("limit", 50), 50);
				String url = BASE_URL + "/search?query=" + encode(query) + "&type=file&limit=" + limit;
				HttpResponse<String> response = get(url, headers);
				return toListResult(response, "entries");
			}
			case "upload": {
				String folderId = context.getParameter("uploadFolderId", "0");
				String fileName = context.getParameter("fileName", "");
				String fileContent = context.getParameter("fileContent", "");

				Map<String, Object> attributes = new LinkedHashMap<>();
				attributes.put("name", fileName);
				attributes.put("parent", Map.of("id", folderId));

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("attributes", attributes);
				body.put("file", fileContent);

				HttpResponse<String> response = post(UPLOAD_URL + "/files/content", body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown file operation: " + operation);
		}
	}

	// ========================= Folder Operations =========================

	private NodeExecutionResult executeFolder(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "get");
		Map<String, String> headers = getAuthHeaders(credentials);

		switch (operation) {
			case "create": {
				String name = context.getParameter("folderName", "");
				String parentId = context.getParameter("parentId", "0");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", name);
				body.put("parent", Map.of("id", parentId));

				HttpResponse<String> response = post(BASE_URL + "/folders", body, headers);
				return toResult(response);
			}
			case "delete": {
				String folderId = context.getParameter("folderId", "");
				HttpResponse<String> response = delete(BASE_URL + "/folders/" + encode(folderId) + "?recursive=true", headers);
				return toDeleteResult(response);
			}
			case "get": {
				String folderId = context.getParameter("folderId", "");
				HttpResponse<String> response = get(BASE_URL + "/folders/" + encode(folderId), headers);
				return toResult(response);
			}
			case "getAll": {
				String folderId = context.getParameter("folderId", "");
				int limit = toInt(context.getParameter("folderLimit", 100), 100);
				String url = BASE_URL + "/folders/" + encode(folderId) + "/items?limit=" + limit;
				HttpResponse<String> response = get(url, headers);
				return toListResult(response, "entries");
			}
			case "search": {
				String query = context.getParameter("folderSearchQuery", "");
				String url = BASE_URL + "/search?query=" + encode(query) + "&type=folder";
				HttpResponse<String> response = get(url, headers);
				return toListResult(response, "entries");
			}
			case "update": {
				String folderId = context.getParameter("folderId", "");
				Map<String, Object> updateFields = context.getParameter("updateFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				if (updateFields.get("name") != null) body.put("name", updateFields.get("name"));
				if (updateFields.get("description") != null) body.put("description", updateFields.get("description"));
				if (updateFields.get("canNonOwnersInvite") != null) body.put("can_non_owners_invite", updateFields.get("canNonOwnersInvite"));

				HttpResponse<String> response = put(BASE_URL + "/folders/" + encode(folderId), body, headers);
				return toResult(response);
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
			return boxError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult toListResult(HttpResponse<String> response, String dataKey) throws Exception {
		if (response.statusCode() >= 400) {
			return boxError(response);
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
			return boxError(response);
		}
		if (response.body() == null || response.body().isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		return toResult(response);
	}

	private NodeExecutionResult boxError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Box API error (HTTP " + response.statusCode() + "): " + body);
	}
}
