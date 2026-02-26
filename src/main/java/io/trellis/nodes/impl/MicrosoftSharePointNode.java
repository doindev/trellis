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
 * Microsoft SharePoint Node -- manage sites, drives, drive items, lists,
 * and list items via the Microsoft Graph API.
 */
@Slf4j
@Node(
	type = "microsoftSharePoint",
	displayName = "Microsoft SharePoint",
	description = "Manage sites, drives, drive items, lists, and list items in Microsoft SharePoint",
	category = "Cloud Storage",
	icon = "microsoftSharePoint",
	credentials = {"microsoftSharePointOAuth2Api"}
)
public class MicrosoftSharePointNode extends AbstractApiNode {

	private static final String BASE_URL = "https://graph.microsoft.com/v1.0";

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("driveItem")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Drive").value("drive").description("Manage drives").build(),
				ParameterOption.builder().name("Drive Item").value("driveItem").description("Manage drive items (files/folders)").build(),
				ParameterOption.builder().name("List").value("list").description("Manage lists").build(),
				ParameterOption.builder().name("List Item").value("listItem").description("Manage list items").build(),
				ParameterOption.builder().name("Site").value("site").description("Manage sites").build()
			)).build());

		// Drive operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("drive"))))
			.options(List.of(
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all drives in a site").build()
			)).build());

		// Drive Item operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("driveItem"))))
			.options(List.of(
				ParameterOption.builder().name("Copy").value("copy").description("Copy a drive item").build(),
				ParameterOption.builder().name("Create Folder").value("create").description("Create a folder").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a drive item").build(),
				ParameterOption.builder().name("Download").value("download").description("Download a file").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a drive item").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("List children of a drive item").build(),
				ParameterOption.builder().name("Move").value("move").description("Move a drive item").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a drive item").build(),
				ParameterOption.builder().name("Upload").value("upload").description("Upload a file").build()
			)).build());

		// List operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("list"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a list").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a list").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a list").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many lists").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a list").build()
			)).build());

		// List Item operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("listItem"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a list item").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a list item").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a list item").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many list items").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a list item").build()
			)).build());

		// Site operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("site"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get a site").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many sites").build(),
				ParameterOption.builder().name("Search").value("search").description("Search sites").build()
			)).build());

		// Common parameters
		params.add(NodeParameter.builder()
			.name("siteId").displayName("Site ID")
			.type(ParameterType.STRING).required(true)
			.description("The ID of the SharePoint site.")
			.build());

		params.add(NodeParameter.builder()
			.name("driveId").displayName("Drive ID")
			.type(ParameterType.STRING)
			.description("The ID of the drive.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("driveItem"))))
			.build());

		params.add(NodeParameter.builder()
			.name("itemId").displayName("Item ID")
			.type(ParameterType.STRING)
			.description("The ID of the drive item.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("driveItem"))))
			.build());

		params.add(NodeParameter.builder()
			.name("listId").displayName("List ID")
			.type(ParameterType.STRING)
			.description("The ID of the list.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("list", "listItem"))))
			.build());

		params.add(NodeParameter.builder()
			.name("listItemId").displayName("List Item ID")
			.type(ParameterType.STRING)
			.description("The ID of the list item.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("listItem"), "operation", List.of("delete", "get", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("name").displayName("Name")
			.type(ParameterType.STRING)
			.description("Name of the resource.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("destinationFolderId").displayName("Destination Folder ID")
			.type(ParameterType.STRING)
			.description("ID of the destination folder.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("driveItem"), "operation", List.of("copy", "move"))))
			.build());

		params.add(NodeParameter.builder()
			.name("searchQuery").displayName("Search Query")
			.type(ParameterType.STRING)
			.description("Text to search for.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("site"), "operation", List.of("search"))))
			.build());

		params.add(NodeParameter.builder()
			.name("fileContent").displayName("File Content")
			.type(ParameterType.STRING)
			.description("Content of the file to upload.")
			.typeOptions(Map.of("rows", 5))
			.displayOptions(Map.of("show", Map.of("resource", List.of("driveItem"), "operation", List.of("upload"))))
			.build());

		params.add(NodeParameter.builder()
			.name("fileName").displayName("File Name")
			.type(ParameterType.STRING)
			.description("Name of the file to upload.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("driveItem"), "operation", List.of("upload"))))
			.build());

		params.add(NodeParameter.builder()
			.name("fieldsJson").displayName("Fields (JSON)")
			.type(ParameterType.JSON).defaultValue("{}")
			.description("Fields as JSON for list item create/update.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("listItem"), "operation", List.of("create", "update"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "driveItem");
		String operation = context.getParameter("operation", "getAll");
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));

		try {
			Map<String, String> headers = authHeaders(accessToken);
			return switch (resource) {
				case "drive" -> executeDrive(context, operation, headers);
				case "driveItem" -> executeDriveItem(context, operation, headers);
				case "list" -> executeList(context, operation, headers);
				case "listItem" -> executeListItem(context, operation, headers);
				case "site" -> executeSite(context, operation, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Microsoft SharePoint API error: " + e.getMessage(), e);
		}
	}

	// ========================= Drive Operations =========================

	private NodeExecutionResult executeDrive(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		if ("getAll".equals(operation)) {
			String siteId = context.getParameter("siteId", "");
			HttpResponse<String> response = get(BASE_URL + "/sites/" + encode(siteId) + "/drives", headers);
			return toArrayResult(response, "value");
		}
		return NodeExecutionResult.error("Unknown drive operation: " + operation);
	}

	// ========================= Drive Item Operations =========================

	private NodeExecutionResult executeDriveItem(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String siteId = context.getParameter("siteId", "");
		String driveId = context.getParameter("driveId", "");
		String itemId = context.getParameter("itemId", "");
		String driveBase = BASE_URL + "/sites/" + encode(siteId) + "/drives/" + encode(driveId);

		switch (operation) {
			case "copy": {
				String destFolderId = context.getParameter("destinationFolderId", "");
				String name = context.getParameter("name", "");
				Map<String, Object> body = new LinkedHashMap<>();
				if (!destFolderId.isEmpty()) {
					body.put("parentReference", Map.of("id", destFolderId));
				}
				if (!name.isEmpty()) {
					body.put("name", name);
				}
				HttpResponse<String> response = post(driveBase + "/items/" + encode(itemId) + "/copy", body, headers);
				if (response.statusCode() == 202) {
					return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "message", "Copy operation accepted"))));
				}
				return toResult(response);
			}
			case "create": {
				String name = context.getParameter("name", "");
				String parentId = itemId.isEmpty() ? "root" : itemId;
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", name);
				body.put("folder", Map.of());
				body.put("@microsoft.graph.conflictBehavior", "rename");
				HttpResponse<String> response = post(driveBase + "/items/" + encode(parentId) + "/children", body, headers);
				return toResult(response);
			}
			case "delete": {
				HttpResponse<String> response = delete(driveBase + "/items/" + encode(itemId), headers);
				return toDeleteResult(response);
			}
			case "download": {
				HttpResponse<String> response = get(driveBase + "/items/" + encode(itemId) + "/content", headers);
				if (response.statusCode() >= 400) {
					return sharePointError(response);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(Map.of("content", response.body(), "itemId", itemId))));
			}
			case "get": {
				HttpResponse<String> response = get(driveBase + "/items/" + encode(itemId), headers);
				return toResult(response);
			}
			case "getAll": {
				String parentId = itemId.isEmpty() ? "root" : itemId;
				HttpResponse<String> response = get(driveBase + "/items/" + encode(parentId) + "/children", headers);
				return toArrayResult(response, "value");
			}
			case "move": {
				String destFolderId = context.getParameter("destinationFolderId", "");
				String name = context.getParameter("name", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("parentReference", Map.of("id", destFolderId));
				if (!name.isEmpty()) {
					body.put("name", name);
				}
				HttpResponse<String> response = patch(driveBase + "/items/" + encode(itemId), body, headers);
				return toResult(response);
			}
			case "update": {
				String name = context.getParameter("name", "");
				Map<String, Object> body = new LinkedHashMap<>();
				if (!name.isEmpty()) body.put("name", name);
				HttpResponse<String> response = patch(driveBase + "/items/" + encode(itemId), body, headers);
				return toResult(response);
			}
			case "upload": {
				String fileName = context.getParameter("fileName", "");
				String fileContent = context.getParameter("fileContent", "");
				String parentId = itemId.isEmpty() ? "root" : itemId;
				String url = driveBase + "/items/" + encode(parentId) + ":/" + encode(fileName) + ":/content";
				Map<String, String> uploadHeaders = new LinkedHashMap<>(headers);
				uploadHeaders.put("Content-Type", "application/octet-stream");
				HttpResponse<String> response = put(url, fileContent, uploadHeaders);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown driveItem operation: " + operation);
		}
	}

	// ========================= List Operations =========================

	private NodeExecutionResult executeList(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String siteId = context.getParameter("siteId", "");
		String listId = context.getParameter("listId", "");
		String listBase = BASE_URL + "/sites/" + encode(siteId) + "/lists";

		switch (operation) {
			case "create": {
				String name = context.getParameter("name", "");
				Map<String, Object> body = Map.of("displayName", name, "list", Map.of("template", "genericList"));
				HttpResponse<String> response = post(listBase, body, headers);
				return toResult(response);
			}
			case "delete": {
				HttpResponse<String> response = delete(listBase + "/" + encode(listId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				HttpResponse<String> response = get(listBase + "/" + encode(listId), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(listBase, headers);
				return toArrayResult(response, "value");
			}
			case "update": {
				String name = context.getParameter("name", "");
				Map<String, Object> body = new LinkedHashMap<>();
				if (!name.isEmpty()) body.put("displayName", name);
				HttpResponse<String> response = patch(listBase + "/" + encode(listId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown list operation: " + operation);
		}
	}

	// ========================= List Item Operations =========================

	private NodeExecutionResult executeListItem(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String siteId = context.getParameter("siteId", "");
		String listId = context.getParameter("listId", "");
		String listItemId = context.getParameter("listItemId", "");
		String itemBase = BASE_URL + "/sites/" + encode(siteId) + "/lists/" + encode(listId) + "/items";

		switch (operation) {
			case "create": {
				String fieldsJson = context.getParameter("fieldsJson", "{}");
				Map<String, Object> fields = parseJson(fieldsJson);
				Map<String, Object> body = Map.of("fields", fields);
				HttpResponse<String> response = post(itemBase, body, headers);
				return toResult(response);
			}
			case "delete": {
				HttpResponse<String> response = delete(itemBase + "/" + encode(listItemId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				HttpResponse<String> response = get(itemBase + "/" + encode(listItemId), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(itemBase, headers);
				return toArrayResult(response, "value");
			}
			case "update": {
				String fieldsJson = context.getParameter("fieldsJson", "{}");
				Map<String, Object> fields = parseJson(fieldsJson);
				Map<String, Object> body = Map.of("fields", fields);
				HttpResponse<String> response = patch(itemBase + "/" + encode(listItemId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown listItem operation: " + operation);
		}
	}

	// ========================= Site Operations =========================

	private NodeExecutionResult executeSite(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "get": {
				String siteId = context.getParameter("siteId", "");
				HttpResponse<String> response = get(BASE_URL + "/sites/" + encode(siteId), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(BASE_URL + "/sites?search=*", headers);
				return toArrayResult(response, "value");
			}
			case "search": {
				String query = context.getParameter("searchQuery", "");
				HttpResponse<String> response = get(BASE_URL + "/sites?search=" + encode(query), headers);
				return toArrayResult(response, "value");
			}
			default:
				return NodeExecutionResult.error("Unknown site operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> authHeaders(String accessToken) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return sharePointError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult toArrayResult(HttpResponse<String> response, String key) throws Exception {
		if (response.statusCode() >= 400) {
			return sharePointError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		Object data = parsed.get(key);
		if (data instanceof List) {
			List<Map<String, Object>> results = new ArrayList<>();
			for (Object item : (List<?>) data) {
				if (item instanceof Map) {
					results.add(wrapInJson(item));
				}
			}
			return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return sharePointError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
	}

	private NodeExecutionResult sharePointError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Microsoft SharePoint API error (HTTP " + response.statusCode() + "): " + body);
	}
}
