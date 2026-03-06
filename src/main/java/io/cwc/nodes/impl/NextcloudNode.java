package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Nextcloud — manage files, folders and users via the Nextcloud WebDAV and OCS APIs.
 */
@Node(
		type = "nextCloud",
		displayName = "Nextcloud",
		description = "Manage files, folders and users in Nextcloud",
		category = "Cloud Storage",
		icon = "nextcloud",
		credentials = {"nextCloudApi"}
)
public class NextcloudNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String baseUrl = context.getCredentialString("url", "");
		String userId = context.getCredentialString("userId", "");
		String password = context.getCredentialString("password", "");
		String resource = context.getParameter("resource", "file");
		String operation = context.getParameter("operation", "list");

		if (baseUrl.endsWith("/")) {
			baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		}

		String credentials = Base64.getEncoder().encodeToString((userId + ":" + password).getBytes());
		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Basic " + credentials);

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "file" -> handleFile(context, baseUrl, userId, operation, headers);
					case "folder" -> handleFolder(context, baseUrl, userId, operation, headers);
					case "user" -> handleUser(context, baseUrl, operation, headers);
					default -> throw new IllegalArgumentException("Unknown resource: " + resource);
				};
				results.add(wrapInJson(result));
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					return handleError(context, e.getMessage(), e);
				}
				throw new RuntimeException(e);
			}
		}

		return NodeExecutionResult.success(results);
	}

	private Map<String, Object> handleFile(NodeExecutionContext context, String baseUrl,
			String userId, String operation, Map<String, String> headers) throws Exception {
		String path = context.getParameter("path", "");
		String webdavBase = baseUrl + "/remote.php/dav/files/" + encode(userId);

		return switch (operation) {
			case "copy" -> {
				String toPath = context.getParameter("toPath", "");
				String destination = webdavBase + toPath;
				headers.put("Destination", destination);
				HttpResponse<String> response = makeWebDavRequest("COPY", webdavBase + path, null, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("path", path);
				result.put("toPath", toPath);
				yield result;
			}
			case "delete" -> {
				HttpResponse<String> response = delete(webdavBase + path, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("path", path);
				yield result;
			}
			case "move" -> {
				String toPath = context.getParameter("toPath", "");
				String destination = webdavBase + toPath;
				headers.put("Destination", destination);
				HttpResponse<String> response = makeWebDavRequest("MOVE", webdavBase + path, null, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("path", path);
				result.put("toPath", toPath);
				yield result;
			}
			case "share" -> {
				yield shareResource(context, baseUrl, path, headers);
			}
			case "upload" -> {
				String fileContent = context.getParameter("fileContent", "");
				headers.put("Content-Type", "application/octet-stream");
				HttpResponse<String> response = makeWebDavRequest("PUT", webdavBase + path, fileContent, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("path", path);
				yield result;
			}
			default -> throw new IllegalArgumentException("Unknown file operation: " + operation);
		};
	}

	private Map<String, Object> handleFolder(NodeExecutionContext context, String baseUrl,
			String userId, String operation, Map<String, String> headers) throws Exception {
		String path = context.getParameter("path", "");
		String webdavBase = baseUrl + "/remote.php/dav/files/" + encode(userId);

		return switch (operation) {
			case "create" -> {
				HttpResponse<String> response = makeWebDavRequest("MKCOL", webdavBase + path, null, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("path", path);
				yield result;
			}
			case "delete" -> {
				HttpResponse<String> response = delete(webdavBase + path, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("path", path);
				yield result;
			}
			case "list" -> {
				headers.put("Depth", "1");
				HttpResponse<String> response = makeWebDavRequest("PROPFIND", webdavBase + path, null, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("statusCode", response.statusCode());
				result.put("body", response.body());
				result.put("path", path);
				yield result;
			}
			case "copy" -> {
				String toPath = context.getParameter("toPath", "");
				String destination = webdavBase + toPath;
				headers.put("Destination", destination);
				HttpResponse<String> response = makeWebDavRequest("COPY", webdavBase + path, null, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("path", path);
				result.put("toPath", toPath);
				yield result;
			}
			case "move" -> {
				String toPath = context.getParameter("toPath", "");
				String destination = webdavBase + toPath;
				headers.put("Destination", destination);
				HttpResponse<String> response = makeWebDavRequest("MOVE", webdavBase + path, null, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("path", path);
				result.put("toPath", toPath);
				yield result;
			}
			case "share" -> {
				yield shareResource(context, baseUrl, path, headers);
			}
			default -> throw new IllegalArgumentException("Unknown folder operation: " + operation);
		};
	}

	private Map<String, Object> handleUser(NodeExecutionContext context, String baseUrl,
			String operation, Map<String, String> headers) throws Exception {
		String ocsBase = baseUrl + "/ocs/v1.php/cloud/users";
		headers.put("OCS-APIRequest", "true");

		return switch (operation) {
			case "create" -> {
				String newUserId = context.getParameter("newUserId", "");
				String email = context.getParameter("email", "");
				String displayName = context.getParameter("displayName", "");

				headers.put("Content-Type", "application/x-www-form-urlencoded");
				String formBody = "userid=" + encode(newUserId) + "&email=" + encode(email);
				if (!displayName.isBlank()) {
					formBody += "&displayName=" + encode(displayName);
				}

				HttpResponse<String> response = makeFormPost(ocsBase, formBody, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("userId", newUserId);
				yield result;
			}
			case "delete" -> {
				String targetUserId = context.getParameter("targetUserId", "");
				HttpResponse<String> response = delete(ocsBase + "/" + encode(targetUserId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("userId", targetUserId);
				yield result;
			}
			case "get" -> {
				String targetUserId = context.getParameter("targetUserId", "");
				HttpResponse<String> response = get(ocsBase + "/" + encode(targetUserId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("statusCode", response.statusCode());
				result.put("body", response.body());
				yield result;
			}
			case "getMany" -> {
				boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
				int limit = toInt(context.getParameters().get("limit"), 50);
				String search = context.getParameter("search", "");

				String url = ocsBase + "?format=json";
				if (!search.isBlank()) {
					url += "&search=" + encode(search);
				}
				if (!returnAll) {
					url += "&limit=" + limit;
				}
				HttpResponse<String> response = get(url, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("statusCode", response.statusCode());
				result.put("body", response.body());
				yield result;
			}
			case "update" -> {
				String targetUserId = context.getParameter("targetUserId", "");
				String updateKey = context.getParameter("updateKey", "");
				String updateValue = context.getParameter("updateValue", "");

				headers.put("Content-Type", "application/x-www-form-urlencoded");
				String formBody = "key=" + encode(updateKey) + "&value=" + encode(updateValue);

				HttpResponse<String> response = makeFormPut(
						ocsBase + "/" + encode(targetUserId), formBody, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("userId", targetUserId);
				yield result;
			}
			default -> throw new IllegalArgumentException("Unknown user operation: " + operation);
		};
	}

	private Map<String, Object> shareResource(NodeExecutionContext context, String baseUrl,
			String path, Map<String, String> headers) throws Exception {
		int shareType = toInt(context.getParameters().get("shareType"), 3);
		int permissions = toInt(context.getParameters().get("permissions"), 1);
		String shareWith = context.getParameter("shareWith", "");
		String password = context.getParameter("sharePassword", "");

		String url = baseUrl + "/ocs/v2.php/apps/files_sharing/api/v1/shares";
		headers.put("OCS-APIRequest", "true");
		headers.put("Content-Type", "application/x-www-form-urlencoded");

		StringBuilder formBody = new StringBuilder();
		formBody.append("path=").append(encode(path));
		formBody.append("&shareType=").append(shareType);
		formBody.append("&permissions=").append(permissions);

		if (!shareWith.isBlank()) {
			formBody.append("&shareWith=").append(encode(shareWith));
		}
		if (!password.isBlank()) {
			formBody.append("&password=").append(encode(password));
		}

		HttpResponse<String> response = makeFormPost(url, formBody.toString(), headers);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
		result.put("statusCode", response.statusCode());
		result.put("body", response.body());
		return result;
	}

	private HttpResponse<String> makeWebDavRequest(String method, String url, String body,
			Map<String, String> headers) throws Exception {
		java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
				.uri(java.net.URI.create(url));

		for (Map.Entry<String, String> header : headers.entrySet()) {
			builder.header(header.getKey(), header.getValue());
		}

		if (body != null) {
			builder.method(method, java.net.http.HttpRequest.BodyPublishers.ofString(body));
		} else {
			builder.method(method, java.net.http.HttpRequest.BodyPublishers.noBody());
		}

		java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
		return client.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> makeFormPost(String url, String formBody,
			Map<String, String> headers) throws Exception {
		java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
				.uri(java.net.URI.create(url))
				.POST(java.net.http.HttpRequest.BodyPublishers.ofString(formBody));

		for (Map.Entry<String, String> header : headers.entrySet()) {
			builder.header(header.getKey(), header.getValue());
		}

		java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
		return client.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> makeFormPut(String url, String formBody,
			Map<String, String> headers) throws Exception {
		java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
				.uri(java.net.URI.create(url))
				.PUT(java.net.http.HttpRequest.BodyPublishers.ofString(formBody));

		for (Map.Entry<String, String> header : headers.entrySet()) {
			builder.header(header.getKey(), header.getValue());
		}

		java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
		return client.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS)
						.defaultValue("file")
						.options(List.of(
								ParameterOption.builder().name("File").value("file").build(),
								ParameterOption.builder().name("Folder").value("folder").build(),
								ParameterOption.builder().name("User").value("user").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("list")
						.options(List.of(
								ParameterOption.builder().name("Copy").value("copy").build(),
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get Many").value("getMany").build(),
								ParameterOption.builder().name("List").value("list").build(),
								ParameterOption.builder().name("Move").value("move").build(),
								ParameterOption.builder().name("Share").value("share").build(),
								ParameterOption.builder().name("Update").value("update").build(),
								ParameterOption.builder().name("Upload").value("upload").build()
						)).build(),
				NodeParameter.builder()
						.name("path").displayName("Path")
						.type(ParameterType.STRING).defaultValue("")
						.description("File or folder path (e.g., /Documents/file.txt).").build(),
				NodeParameter.builder()
						.name("toPath").displayName("Destination Path")
						.type(ParameterType.STRING).defaultValue("")
						.description("Destination path for copy/move operations.").build(),
				NodeParameter.builder()
						.name("fileContent").displayName("File Content")
						.type(ParameterType.STRING).defaultValue("")
						.description("Content to upload.").build(),
				NodeParameter.builder()
						.name("shareType").displayName("Share Type")
						.type(ParameterType.OPTIONS)
						.defaultValue("3")
						.options(List.of(
								ParameterOption.builder().name("User").value("0").build(),
								ParameterOption.builder().name("Group").value("1").build(),
								ParameterOption.builder().name("Public Link").value("3").build(),
								ParameterOption.builder().name("Email").value("4").build()
						)).build(),
				NodeParameter.builder()
						.name("shareWith").displayName("Share With")
						.type(ParameterType.STRING).defaultValue("")
						.description("User, group, or email to share with.").build(),
				NodeParameter.builder()
						.name("sharePassword").displayName("Share Password")
						.type(ParameterType.STRING).defaultValue("")
						.description("Password for public link shares.").build(),
				NodeParameter.builder()
						.name("permissions").displayName("Permissions")
						.type(ParameterType.OPTIONS)
						.defaultValue("1")
						.options(List.of(
								ParameterOption.builder().name("Read").value("1").build(),
								ParameterOption.builder().name("Update").value("2").build(),
								ParameterOption.builder().name("Create").value("4").build(),
								ParameterOption.builder().name("Delete").value("8").build(),
								ParameterOption.builder().name("All").value("31").build()
						)).build(),
				NodeParameter.builder()
						.name("newUserId").displayName("User ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("ID for the new user.").build(),
				NodeParameter.builder()
						.name("targetUserId").displayName("Target User ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("ID of the user to operate on.").build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Email address.").build(),
				NodeParameter.builder()
						.name("displayName").displayName("Display Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Display name for the user.").build(),
				NodeParameter.builder()
						.name("updateKey").displayName("Update Field")
						.type(ParameterType.OPTIONS)
						.defaultValue("email")
						.options(List.of(
								ParameterOption.builder().name("Address").value("address").build(),
								ParameterOption.builder().name("Display Name").value("displayname").build(),
								ParameterOption.builder().name("Email").value("email").build(),
								ParameterOption.builder().name("Password").value("password").build(),
								ParameterOption.builder().name("Twitter").value("twitter").build(),
								ParameterOption.builder().name("Website").value("website").build()
						)).build(),
				NodeParameter.builder()
						.name("updateValue").displayName("Update Value")
						.type(ParameterType.STRING).defaultValue("")
						.description("The new value for the field.").build(),
				NodeParameter.builder()
						.name("search").displayName("Search")
						.type(ParameterType.STRING).defaultValue("")
						.description("Search string to filter users.").build(),
				NodeParameter.builder()
						.name("returnAll").displayName("Return All")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether to return all results.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(50)
						.description("Max number of results to return.").build()
		);
	}
}
