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
	type = "googleDrive",
	displayName = "Google Drive",
	description = "Manage files and shared drives in Google Drive.",
	category = "Cloud Storage",
	icon = "googleDrive",
	credentials = {"googleDriveOAuth2Api"}
)
public class GoogleDriveNode extends AbstractApiNode {

	private static final String BASE_URL = "https://www.googleapis.com/drive/v3";
	private static final String UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3";

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
				ParameterOption.builder().name("File").value("file").description("Manage files in Google Drive").build(),
				ParameterOption.builder().name("Drive").value("drive").description("Manage shared drives").build()
			)).build());

		// File operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("list")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"))))
			.options(List.of(
				ParameterOption.builder().name("Copy").value("copy").description("Copy a file").build(),
				ParameterOption.builder().name("Create Folder").value("createFolder").description("Create a folder").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a file").build(),
				ParameterOption.builder().name("Download").value("download").description("Download a file").build(),
				ParameterOption.builder().name("List").value("list").description("List files and folders").build(),
				ParameterOption.builder().name("Move").value("move").description("Move a file to a different folder").build(),
				ParameterOption.builder().name("Share").value("share").description("Share a file").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a file's metadata").build()
			)).build());

		// Drive operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("list")
			.displayOptions(Map.of("show", Map.of("resource", List.of("drive"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a shared drive").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a shared drive").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a shared drive").build(),
				ParameterOption.builder().name("List").value("list").description("List shared drives").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a shared drive").build()
			)).build());

		addFileParameters(params);
		addDriveParameters(params);

		return params;
	}

	private void addFileParameters(List<NodeParameter> params) {
		// File ID (for copy, delete, download, move, share, update)
		params.add(NodeParameter.builder()
			.name("fileId").displayName("File ID").type(ParameterType.STRING).required(true)
			.description("The ID of the file in Google Drive.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("copy", "delete", "download", "move", "share", "update"))))
			.build());

		// Copy: new name
		params.add(NodeParameter.builder()
			.name("copyName").displayName("New Name").type(ParameterType.STRING)
			.description("The name for the copied file.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("copy"))))
			.build());

		// Copy: parent folder ID
		params.add(NodeParameter.builder()
			.name("copyParentId").displayName("Parent Folder ID").type(ParameterType.STRING)
			.description("The ID of the folder to copy the file into.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("copy"))))
			.build());

		// Create folder: name
		params.add(NodeParameter.builder()
			.name("folderName").displayName("Folder Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("createFolder"))))
			.build());

		// Create folder: parent ID
		params.add(NodeParameter.builder()
			.name("folderParentId").displayName("Parent Folder ID").type(ParameterType.STRING)
			.description("The ID of the parent folder. Leave empty for root.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("createFolder"))))
			.build());

		// List: query
		params.add(NodeParameter.builder()
			.name("queryFilter").displayName("Query Filter").type(ParameterType.STRING)
			.placeHolder("name contains 'report'")
			.description("A Google Drive query string to filter files.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("list"))))
			.build());

		// List: parent folder ID
		params.add(NodeParameter.builder()
			.name("listParentId").displayName("Parent Folder ID").type(ParameterType.STRING)
			.description("Only list files inside this folder.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("list"))))
			.build());

		// List: page size
		params.add(NodeParameter.builder()
			.name("pageSize").displayName("Page Size").type(ParameterType.NUMBER).defaultValue(50)
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("list"))))
			.build());

		// List: return all
		params.add(NodeParameter.builder()
			.name("returnAll").displayName("Return All").type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Automatically paginate to return all results.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("list"))))
			.build());

		// Move: destination folder ID
		params.add(NodeParameter.builder()
			.name("moveToFolderId").displayName("Destination Folder ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("move"))))
			.build());

		// Share: role
		params.add(NodeParameter.builder()
			.name("shareRole").displayName("Role").type(ParameterType.OPTIONS).required(true).defaultValue("reader")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("share"))))
			.options(List.of(
				ParameterOption.builder().name("Reader").value("reader").build(),
				ParameterOption.builder().name("Commenter").value("commenter").build(),
				ParameterOption.builder().name("Writer").value("writer").build(),
				ParameterOption.builder().name("Organizer").value("organizer").build()
			)).build());

		// Share: type
		params.add(NodeParameter.builder()
			.name("shareType").displayName("Type").type(ParameterType.OPTIONS).required(true).defaultValue("user")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("share"))))
			.options(List.of(
				ParameterOption.builder().name("User").value("user").build(),
				ParameterOption.builder().name("Group").value("group").build(),
				ParameterOption.builder().name("Domain").value("domain").build(),
				ParameterOption.builder().name("Anyone").value("anyone").build()
			)).build());

		// Share: email address
		params.add(NodeParameter.builder()
			.name("shareEmailAddress").displayName("Email Address").type(ParameterType.STRING)
			.description("The email address to share with (for user or group type).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("share"), "shareType", List.of("user", "group"))))
			.build());

		// Share: domain
		params.add(NodeParameter.builder()
			.name("shareDomain").displayName("Domain").type(ParameterType.STRING)
			.description("The domain to share with.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("share"), "shareType", List.of("domain"))))
			.build());

		// Update: new name
		params.add(NodeParameter.builder()
			.name("updateName").displayName("New Name").type(ParameterType.STRING)
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("update"))))
			.build());

		// Update: description
		params.add(NodeParameter.builder()
			.name("updateDescription").displayName("Description").type(ParameterType.STRING)
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("update"))))
			.build());

		// Update: starred
		params.add(NodeParameter.builder()
			.name("updateStarred").displayName("Starred").type(ParameterType.BOOLEAN).defaultValue(false)
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("update"))))
			.build());
	}

	private void addDriveParameters(List<NodeParameter> params) {
		// Drive ID (for delete, get, update)
		params.add(NodeParameter.builder()
			.name("driveId").displayName("Drive ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("drive"), "operation", List.of("delete", "get", "update"))))
			.build());

		// Create: name
		params.add(NodeParameter.builder()
			.name("driveName").displayName("Drive Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("drive"), "operation", List.of("create"))))
			.build());

		// List: page size
		params.add(NodeParameter.builder()
			.name("drivePageSize").displayName("Page Size").type(ParameterType.NUMBER).defaultValue(50)
			.displayOptions(Map.of("show", Map.of("resource", List.of("drive"), "operation", List.of("list"))))
			.build());

		// Update: name
		params.add(NodeParameter.builder()
			.name("driveUpdateName").displayName("New Name").type(ParameterType.STRING)
			.displayOptions(Map.of("show", Map.of("resource", List.of("drive"), "operation", List.of("update"))))
			.build());
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "file");
		Map<String, Object> credentials = context.getCredentials();

		try {
			return switch (resource) {
				case "file" -> executeFile(context, credentials);
				case "drive" -> executeDrive(context, credentials);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Google Drive API error: " + e.getMessage(), e);
		}
	}

	// ========================= File Operations =========================

	private NodeExecutionResult executeFile(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "list");
		Map<String, String> headers = getAuthHeaders(credentials);

		switch (operation) {
			case "copy": {
				String fileId = context.getParameter("fileId", "");
				String copyName = context.getParameter("copyName", "");
				String parentId = context.getParameter("copyParentId", "");

				Map<String, Object> body = new LinkedHashMap<>();
				if (!copyName.isEmpty()) body.put("name", copyName);
				if (!parentId.isEmpty()) body.put("parents", List.of(parentId));

				HttpResponse<String> response = post(BASE_URL + "/files/" + encode(fileId) + "/copy", body, headers);
				return toResult(response);
			}
			case "createFolder": {
				String folderName = context.getParameter("folderName", "");
				String parentId = context.getParameter("folderParentId", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", folderName);
				body.put("mimeType", "application/vnd.google-apps.folder");
				if (!parentId.isEmpty()) body.put("parents", List.of(parentId));

				HttpResponse<String> response = post(BASE_URL + "/files", body, headers);
				return toResult(response);
			}
			case "delete": {
				String fileId = context.getParameter("fileId", "");
				HttpResponse<String> response = delete(BASE_URL + "/files/" + encode(fileId), headers);
				return toDeleteResult(response);
			}
			case "download": {
				String fileId = context.getParameter("fileId", "");
				String url = BASE_URL + "/files/" + encode(fileId) + "?alt=media";
				HttpResponse<String> response = get(url, headers);

				if (response.statusCode() >= 400) {
					return driveError(response);
				}

				Map<String, Object> result = new LinkedHashMap<>();
				result.put("content", response.body());
				result.put("fileId", fileId);
				result.put("statusCode", response.statusCode());
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "list": {
				String queryFilter = context.getParameter("queryFilter", "");
				String parentId = context.getParameter("listParentId", "");
				int pageSize = toInt(context.getParameter("pageSize", 50), 50);
				boolean returnAll = toBoolean(context.getParameter("returnAll", false), false);

				StringBuilder query = new StringBuilder();
				if (!parentId.isEmpty()) {
					query.append("'").append(parentId).append("' in parents");
				}
				if (!queryFilter.isEmpty()) {
					if (query.length() > 0) query.append(" and ");
					query.append(queryFilter);
				}

				String url = BASE_URL + "/files?pageSize=" + pageSize
					+ "&fields=nextPageToken,files(id,name,mimeType,size,modifiedTime,createdTime,parents,webViewLink)";
				if (query.length() > 0) url += "&q=" + encode(query.toString());

				List<Map<String, Object>> allItems = new ArrayList<>();
				String pageToken = null;

				do {
					String pagedUrl = pageToken != null ? url + "&pageToken=" + encode(pageToken) : url;
					HttpResponse<String> response = get(pagedUrl, headers);

					if (response.statusCode() >= 400) {
						return driveError(response);
					}

					Map<String, Object> parsed = parseResponse(response);
					Object files = parsed.get("files");
					if (files instanceof List) {
						for (Object file : (List<?>) files) {
							if (file instanceof Map) {
								allItems.add(wrapInJson(file));
							}
						}
					}

					pageToken = returnAll ? (String) parsed.get("nextPageToken") : null;
				} while (pageToken != null);

				return allItems.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(allItems);
			}
			case "move": {
				String fileId = context.getParameter("fileId", "");
				String moveToFolderId = context.getParameter("moveToFolderId", "");

				// First get current parents
				HttpResponse<String> getResponse = get(BASE_URL + "/files/" + encode(fileId) + "?fields=parents", headers);
				if (getResponse.statusCode() >= 400) {
					return driveError(getResponse);
				}

				Map<String, Object> fileInfo = parseResponse(getResponse);
				List<String> currentParents = new ArrayList<>();
				Object parents = fileInfo.get("parents");
				if (parents instanceof List) {
					for (Object p : (List<?>) parents) {
						currentParents.add(String.valueOf(p));
					}
				}

				String removeParents = String.join(",", currentParents);
				String url = BASE_URL + "/files/" + encode(fileId)
					+ "?addParents=" + encode(moveToFolderId)
					+ "&removeParents=" + encode(removeParents);

				HttpResponse<String> response = patch(url, Map.of(), headers);
				return toResult(response);
			}
			case "share": {
				String fileId = context.getParameter("fileId", "");
				String role = context.getParameter("shareRole", "reader");
				String type = context.getParameter("shareType", "user");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("role", role);
				body.put("type", type);

				if ("user".equals(type) || "group".equals(type)) {
					String email = context.getParameter("shareEmailAddress", "");
					body.put("emailAddress", email);
				} else if ("domain".equals(type)) {
					String domain = context.getParameter("shareDomain", "");
					body.put("domain", domain);
				}

				HttpResponse<String> response = post(BASE_URL + "/files/" + encode(fileId) + "/permissions", body, headers);
				return toResult(response);
			}
			case "update": {
				String fileId = context.getParameter("fileId", "");
				String newName = context.getParameter("updateName", "");
				String description = context.getParameter("updateDescription", "");
				boolean starred = toBoolean(context.getParameter("updateStarred", false), false);

				Map<String, Object> body = new LinkedHashMap<>();
				if (!newName.isEmpty()) body.put("name", newName);
				if (!description.isEmpty()) body.put("description", description);
				body.put("starred", starred);

				HttpResponse<String> response = patch(BASE_URL + "/files/" + encode(fileId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown file operation: " + operation);
		}
	}

	// ========================= Drive Operations =========================

	private NodeExecutionResult executeDrive(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "list");
		Map<String, String> headers = getAuthHeaders(credentials);

		switch (operation) {
			case "create": {
				String driveName = context.getParameter("driveName", "");
				String requestId = UUID.randomUUID().toString();

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", driveName);

				HttpResponse<String> response = post(BASE_URL + "/drives?requestId=" + encode(requestId), body, headers);
				return toResult(response);
			}
			case "delete": {
				String driveId = context.getParameter("driveId", "");
				HttpResponse<String> response = delete(BASE_URL + "/drives/" + encode(driveId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String driveId = context.getParameter("driveId", "");
				HttpResponse<String> response = get(BASE_URL + "/drives/" + encode(driveId), headers);
				return toResult(response);
			}
			case "list": {
				int pageSize = toInt(context.getParameter("drivePageSize", 50), 50);
				String url = BASE_URL + "/drives?pageSize=" + pageSize;
				HttpResponse<String> response = get(url, headers);

				if (response.statusCode() >= 400) {
					return driveError(response);
				}

				Map<String, Object> parsed = parseResponse(response);
				Object drives = parsed.get("drives");
				if (drives instanceof List) {
					List<Map<String, Object>> items = new ArrayList<>();
					for (Object drive : (List<?>) drives) {
						if (drive instanceof Map) {
							items.add(wrapInJson(drive));
						}
					}
					return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			case "update": {
				String driveId = context.getParameter("driveId", "");
				String newName = context.getParameter("driveUpdateName", "");

				Map<String, Object> body = new LinkedHashMap<>();
				if (!newName.isEmpty()) body.put("name", newName);

				HttpResponse<String> response = patch(BASE_URL + "/drives/" + encode(driveId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown drive operation: " + operation);
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
			return driveError(response);
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
			return driveError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
	}

	private NodeExecutionResult driveError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Google Drive API error (HTTP " + response.statusCode() + "): " + body);
	}
}
