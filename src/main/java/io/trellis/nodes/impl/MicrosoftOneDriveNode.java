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
	type = "microsoftOneDrive",
	displayName = "Microsoft OneDrive",
	description = "Manage files and folders in Microsoft OneDrive.",
	category = "Cloud Storage",
	icon = "microsoftOneDrive",
	credentials = {"microsoftOneDriveOAuth2Api"}
)
public class MicrosoftOneDriveNode extends AbstractApiNode {

	private static final String BASE_URL = "https://graph.microsoft.com/v1.0/me/drive";

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("list")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Copy").value("copy").description("Copy a file or folder").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a file or folder").build(),
				ParameterOption.builder().name("Download").value("download").description("Download a file").build(),
				ParameterOption.builder().name("Get").value("get").description("Get file or folder metadata").build(),
				ParameterOption.builder().name("List").value("list").description("List files in a folder").build(),
				ParameterOption.builder().name("Move").value("move").description("Move a file or folder").build(),
				ParameterOption.builder().name("Search").value("search").description("Search for files").build(),
				ParameterOption.builder().name("Upload").value("upload").description("Upload a file").build()
			)).build());

		addFileParameters(params);

		return params;
	}

	private void addFileParameters(List<NodeParameter> params) {
		// Item ID (for copy, delete, download, get, move)
		params.add(NodeParameter.builder()
			.name("itemId").displayName("Item ID").type(ParameterType.STRING).required(true)
			.description("The ID of the file or folder.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("copy", "delete", "download", "get", "move"))))
			.build());

		// Copy: destination folder ID
		params.add(NodeParameter.builder()
			.name("copyDestinationFolderId").displayName("Destination Folder ID").type(ParameterType.STRING)
			.description("The ID of the folder to copy the item into.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("copy"))))
			.build());

		// Copy: new name
		params.add(NodeParameter.builder()
			.name("copyNewName").displayName("New Name").type(ParameterType.STRING)
			.description("The new name for the copied item.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("copy"))))
			.build());

		// List: folder ID
		params.add(NodeParameter.builder()
			.name("listFolderId").displayName("Folder ID").type(ParameterType.STRING)
			.description("The ID of the folder to list. Leave empty for root.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("list"))))
			.build());

		// List: top
		params.add(NodeParameter.builder()
			.name("listTop").displayName("Limit").type(ParameterType.NUMBER).defaultValue(50)
			.description("Maximum number of items to return.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("list"))))
			.build());

		// List: return all
		params.add(NodeParameter.builder()
			.name("returnAll").displayName("Return All").type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Automatically paginate to return all results.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("list"))))
			.build());

		// Move: destination folder ID
		params.add(NodeParameter.builder()
			.name("moveDestinationFolderId").displayName("Destination Folder ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("operation", List.of("move"))))
			.build());

		// Move: new name
		params.add(NodeParameter.builder()
			.name("moveNewName").displayName("New Name").type(ParameterType.STRING)
			.description("Optionally rename the item when moving it.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("move"))))
			.build());

		// Search: query
		params.add(NodeParameter.builder()
			.name("searchQuery").displayName("Search Query").type(ParameterType.STRING).required(true)
			.placeHolder("quarterly report")
			.displayOptions(Map.of("show", Map.of("operation", List.of("search"))))
			.build());

		// Search: limit
		params.add(NodeParameter.builder()
			.name("searchLimit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(50)
			.displayOptions(Map.of("show", Map.of("operation", List.of("search"))))
			.build());

		// Upload: folder ID
		params.add(NodeParameter.builder()
			.name("uploadFolderId").displayName("Folder ID").type(ParameterType.STRING)
			.description("The ID of the folder to upload to. Leave empty for root.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("upload"))))
			.build());

		// Upload: file name
		params.add(NodeParameter.builder()
			.name("uploadFileName").displayName("File Name").type(ParameterType.STRING).required(true)
			.placeHolder("document.txt")
			.displayOptions(Map.of("show", Map.of("operation", List.of("upload"))))
			.build());

		// Upload: binary data flag
		params.add(NodeParameter.builder()
			.name("binaryData").displayName("Binary Data").type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Use binary data from the input.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("upload"))))
			.build());

		// Upload: binary property name
		params.add(NodeParameter.builder()
			.name("binaryPropertyName").displayName("Binary Property").type(ParameterType.STRING).defaultValue("data")
			.displayOptions(Map.of("show", Map.of("operation", List.of("upload"), "binaryData", List.of(true))))
			.build());

		// Upload: file content
		params.add(NodeParameter.builder()
			.name("fileContent").displayName("File Content").type(ParameterType.STRING)
			.typeOptions(Map.of("rows", 5))
			.displayOptions(Map.of("show", Map.of("operation", List.of("upload"), "binaryData", List.of(false))))
			.build());

		// Upload: conflict behavior
		params.add(NodeParameter.builder()
			.name("conflictBehavior").displayName("Conflict Behavior").type(ParameterType.OPTIONS).defaultValue("rename")
			.displayOptions(Map.of("show", Map.of("operation", List.of("upload"))))
			.options(List.of(
				ParameterOption.builder().name("Rename").value("rename").description("Rename the uploaded file if one already exists").build(),
				ParameterOption.builder().name("Replace").value("replace").description("Replace the existing file").build(),
				ParameterOption.builder().name("Fail").value("fail").description("Fail if a file with the same name exists").build()
			)).build());
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String operation = context.getParameter("operation", "list");
		Map<String, Object> credentials = context.getCredentials();

		try {
			Map<String, String> headers = getAuthHeaders(credentials);
			return executeOperation(operation, context, headers);
		} catch (Exception e) {
			return handleError(context, "Microsoft OneDrive API error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeOperation(String operation, NodeExecutionContext context, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "copy": {
				String itemId = context.getParameter("itemId", "");
				String destFolderId = context.getParameter("copyDestinationFolderId", "");
				String newName = context.getParameter("copyNewName", "");

				Map<String, Object> body = new LinkedHashMap<>();
				if (!destFolderId.isEmpty()) {
					body.put("parentReference", Map.of("id", destFolderId));
				}
				if (!newName.isEmpty()) {
					body.put("name", newName);
				}

				HttpResponse<String> response = post(BASE_URL + "/items/" + encode(itemId) + "/copy", body, headers);
				// Copy is async - returns 202 Accepted with a Location header
				if (response.statusCode() == 202) {
					Map<String, Object> result = new LinkedHashMap<>();
					result.put("success", true);
					result.put("statusCode", response.statusCode());
					result.put("message", "Copy operation accepted. The file will be copied asynchronously.");
					return NodeExecutionResult.success(List.of(wrapInJson(result)));
				}
				return toResult(response);
			}
			case "delete": {
				String itemId = context.getParameter("itemId", "");
				HttpResponse<String> response = delete(BASE_URL + "/items/" + encode(itemId), headers);
				return toDeleteResult(response);
			}
			case "download": {
				String itemId = context.getParameter("itemId", "");
				String url = BASE_URL + "/items/" + encode(itemId) + "/content";
				HttpResponse<String> response = get(url, headers);

				if (response.statusCode() >= 400) {
					return oneDriveError(response);
				}

				Map<String, Object> result = new LinkedHashMap<>();
				result.put("content", response.body());
				result.put("itemId", itemId);
				result.put("statusCode", response.statusCode());
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "get": {
				String itemId = context.getParameter("itemId", "");
				HttpResponse<String> response = get(BASE_URL + "/items/" + encode(itemId), headers);
				return toResult(response);
			}
			case "list": {
				String folderId = context.getParameter("listFolderId", "");
				int top = toInt(context.getParameter("listTop", 50), 50);
				boolean returnAll = toBoolean(context.getParameter("returnAll", false), false);

				String url;
				if (folderId.isEmpty()) {
					url = BASE_URL + "/root/children?$top=" + top;
				} else {
					url = BASE_URL + "/items/" + encode(folderId) + "/children?$top=" + top;
				}

				List<Map<String, Object>> allItems = new ArrayList<>();
				String nextLink = null;

				do {
					String currentUrl = nextLink != null ? nextLink : url;
					HttpResponse<String> response = get(currentUrl, headers);

					if (response.statusCode() >= 400) {
						return oneDriveError(response);
					}

					Map<String, Object> parsed = parseResponse(response);
					Object value = parsed.get("value");
					if (value instanceof List) {
						for (Object item : (List<?>) value) {
							if (item instanceof Map) {
								allItems.add(wrapInJson(item));
							}
						}
					}

					nextLink = returnAll ? (String) parsed.get("@odata.nextLink") : null;
				} while (nextLink != null);

				return allItems.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(allItems);
			}
			case "move": {
				String itemId = context.getParameter("itemId", "");
				String destFolderId = context.getParameter("moveDestinationFolderId", "");
				String newName = context.getParameter("moveNewName", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("parentReference", Map.of("id", destFolderId));
				if (!newName.isEmpty()) {
					body.put("name", newName);
				}

				HttpResponse<String> response = patch(BASE_URL + "/items/" + encode(itemId), body, headers);
				return toResult(response);
			}
			case "search": {
				String query = context.getParameter("searchQuery", "");
				int limit = toInt(context.getParameter("searchLimit", 50), 50);

				String url = BASE_URL + "/root/search(q='" + encode(query) + "')?$top=" + limit;
				HttpResponse<String> response = get(url, headers);

				if (response.statusCode() >= 400) {
					return oneDriveError(response);
				}

				Map<String, Object> parsed = parseResponse(response);
				Object value = parsed.get("value");
				if (value instanceof List) {
					List<Map<String, Object>> items = new ArrayList<>();
					for (Object item : (List<?>) value) {
						if (item instanceof Map) {
							items.add(wrapInJson(item));
						}
					}
					return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			case "upload": {
				String folderId = context.getParameter("uploadFolderId", "");
				String fileName = context.getParameter("uploadFileName", "");
				String fileContent = context.getParameter("fileContent", "");
				String conflictBehavior = context.getParameter("conflictBehavior", "rename");

				String url;
				if (folderId.isEmpty()) {
					url = BASE_URL + "/root:/" + encode(fileName) + ":/content";
				} else {
					url = BASE_URL + "/items/" + encode(folderId) + ":/" + encode(fileName) + ":/content";
				}
				url += "?@microsoft.graph.conflictBehavior=" + conflictBehavior;

				Map<String, String> uploadHeaders = new LinkedHashMap<>(headers);
				uploadHeaders.put("Content-Type", "application/octet-stream");

				HttpResponse<String> response = put(url, fileContent, uploadHeaders);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown operation: " + operation);
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
			return oneDriveError(response);
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
			return oneDriveError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
	}

	private NodeExecutionResult oneDriveError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Microsoft OneDrive API error (HTTP " + response.statusCode() + "): " + body);
	}
}
