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

/**
 * GitLab Node -- manage issues, releases, repositories, and users
 * via the GitLab REST API.
 */
@Slf4j
@Node(
	type = "gitlab",
	displayName = "GitLab",
	description = "Manage issues, releases, repositories, and users in GitLab",
	category = "Development / DevOps",
	icon = "gitlab",
	credentials = {"gitlabApi"}
)
public class GitLabNode extends AbstractApiNode {

	private static final String DEFAULT_BASE_URL = "https://gitlab.com";

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("issue")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Issue").value("issue").description("Manage issues").build(),
				ParameterOption.builder().name("Release").value("release").description("Manage releases").build(),
				ParameterOption.builder().name("Repository").value("repository").description("Manage repositories").build(),
				ParameterOption.builder().name("User").value("user").description("Manage users").build()
			)).build());

		// Issue operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an issue").build(),
				ParameterOption.builder().name("Create Comment").value("createComment").description("Create a comment on an issue").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an issue").build(),
				ParameterOption.builder().name("Edit").value("edit").description("Edit an issue").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an issue").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many issues").build()
			)).build());

		// Release operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("release"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a release").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a release").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a release").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many releases").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a release").build()
			)).build());

		// Repository operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("repository"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get a repository/project").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many repositories/projects").build()
			)).build());

		// User operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get a user").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many users").build()
			)).build());

		// Common parameters
		params.add(NodeParameter.builder()
			.name("projectId").displayName("Project ID")
			.type(ParameterType.STRING).required(true)
			.description("ID or URL-encoded path of the project.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue", "release", "repository"))))
			.build());

		params.add(NodeParameter.builder()
			.name("issueIid").displayName("Issue IID")
			.type(ParameterType.NUMBER)
			.description("Internal ID of the issue.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("createComment", "delete", "edit", "get"))))
			.build());

		params.add(NodeParameter.builder()
			.name("issueTitle").displayName("Title")
			.type(ParameterType.STRING)
			.description("Title of the issue.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("create", "edit"))))
			.build());

		params.add(NodeParameter.builder()
			.name("issueDescription").displayName("Description")
			.type(ParameterType.STRING)
			.description("Description of the issue.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("create", "edit"))))
			.build());

		params.add(NodeParameter.builder()
			.name("commentBody").displayName("Comment Body")
			.type(ParameterType.STRING)
			.description("Body of the comment.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("createComment"))))
			.build());

		params.add(NodeParameter.builder()
			.name("tagName").displayName("Tag Name")
			.type(ParameterType.STRING)
			.description("Tag name for the release.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("release"))))
			.build());

		params.add(NodeParameter.builder()
			.name("releaseName").displayName("Release Name")
			.type(ParameterType.STRING)
			.description("Name of the release.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("release"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("releaseDescription").displayName("Release Description")
			.type(ParameterType.STRING)
			.description("Description of the release.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("release"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("ref").displayName("Ref (branch/commit)")
			.type(ParameterType.STRING)
			.description("The commit SHA or branch name from which to create the release.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("release"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("userId").displayName("User ID")
			.type(ParameterType.STRING)
			.description("ID of the user.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("get"))))
			.build());

		params.add(NodeParameter.builder()
			.name("additionalFields").displayName("Additional Fields (JSON)")
			.type(ParameterType.JSON).defaultValue("{}")
			.description("Additional fields as JSON.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create", "edit", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("returnAll").displayName("Return All")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Whether to return all results.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit")
			.type(ParameterType.NUMBER).defaultValue(20)
			.description("Max number of results to return.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "issue");
		String operation = context.getParameter("operation", "getAll");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String baseUrl = getGitLabBaseUrl(credentials);
			Map<String, String> headers = authHeaders(credentials);

			return switch (resource) {
				case "issue" -> executeIssue(context, operation, baseUrl, headers);
				case "release" -> executeRelease(context, operation, baseUrl, headers);
				case "repository" -> executeRepository(context, operation, baseUrl, headers);
				case "user" -> executeUser(context, operation, baseUrl, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "GitLab API error: " + e.getMessage(), e);
		}
	}

	// ========================= Issue Operations =========================

	private NodeExecutionResult executeIssue(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		String projectId = context.getParameter("projectId", "");

		switch (operation) {
			case "create": {
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				body.put("title", context.getParameter("issueTitle", ""));
				putIfNotEmpty(body, "description", context.getParameter("issueDescription", ""));
				HttpResponse<String> response = post(baseUrl + "/projects/" + encode(projectId) + "/issues", body, headers);
				return toResult(response);
			}
			case "createComment": {
				int issueIid = toInt(context.getParameters().get("issueIid"), 0);
				String commentBody = context.getParameter("commentBody", "");
				Map<String, Object> body = Map.of("body", commentBody);
				HttpResponse<String> response = post(baseUrl + "/projects/" + encode(projectId) + "/issues/" + issueIid + "/notes", body, headers);
				return toResult(response);
			}
			case "delete": {
				int issueIid = toInt(context.getParameters().get("issueIid"), 0);
				HttpResponse<String> response = delete(baseUrl + "/projects/" + encode(projectId) + "/issues/" + issueIid, headers);
				return toDeleteResult(response, String.valueOf(issueIid));
			}
			case "edit": {
				int issueIid = toInt(context.getParameters().get("issueIid"), 0);
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				putIfNotEmpty(body, "title", context.getParameter("issueTitle", ""));
				putIfNotEmpty(body, "description", context.getParameter("issueDescription", ""));
				HttpResponse<String> response = put(baseUrl + "/projects/" + encode(projectId) + "/issues/" + issueIid, body, headers);
				return toResult(response);
			}
			case "get": {
				int issueIid = toInt(context.getParameters().get("issueIid"), 0);
				HttpResponse<String> response = get(baseUrl + "/projects/" + encode(projectId) + "/issues/" + issueIid, headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(baseUrl + "/projects/" + encode(projectId) + "/issues?per_page=" + limit, headers);
				return toArrayResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown issue operation: " + operation);
		}
	}

	// ========================= Release Operations =========================

	private NodeExecutionResult executeRelease(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		String projectId = context.getParameter("projectId", "");

		switch (operation) {
			case "create": {
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				body.put("tag_name", context.getParameter("tagName", ""));
				putIfNotEmpty(body, "name", context.getParameter("releaseName", ""));
				putIfNotEmpty(body, "description", context.getParameter("releaseDescription", ""));
				putIfNotEmpty(body, "ref", context.getParameter("ref", ""));
				HttpResponse<String> response = post(baseUrl + "/projects/" + encode(projectId) + "/releases", body, headers);
				return toResult(response);
			}
			case "delete": {
				String tagName = context.getParameter("tagName", "");
				HttpResponse<String> response = delete(baseUrl + "/projects/" + encode(projectId) + "/releases/" + encode(tagName), headers);
				return toDeleteResult(response, tagName);
			}
			case "get": {
				String tagName = context.getParameter("tagName", "");
				HttpResponse<String> response = get(baseUrl + "/projects/" + encode(projectId) + "/releases/" + encode(tagName), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(baseUrl + "/projects/" + encode(projectId) + "/releases?per_page=" + limit, headers);
				return toArrayResult(response);
			}
			case "update": {
				String tagName = context.getParameter("tagName", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				putIfNotEmpty(body, "name", context.getParameter("releaseName", ""));
				putIfNotEmpty(body, "description", context.getParameter("releaseDescription", ""));
				HttpResponse<String> response = put(baseUrl + "/projects/" + encode(projectId) + "/releases/" + encode(tagName), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown release operation: " + operation);
		}
	}

	// ========================= Repository Operations =========================

	private NodeExecutionResult executeRepository(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "get": {
				String projectId = context.getParameter("projectId", "");
				HttpResponse<String> response = get(baseUrl + "/projects/" + encode(projectId), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(baseUrl + "/projects?per_page=" + limit + "&order_by=updated_at&sort=desc", headers);
				return toArrayResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown repository operation: " + operation);
		}
	}

	// ========================= User Operations =========================

	private NodeExecutionResult executeUser(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "get": {
				String userId = context.getParameter("userId", "");
				HttpResponse<String> response = get(baseUrl + "/users/" + encode(userId), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(baseUrl + "/users?per_page=" + limit, headers);
				return toArrayResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown user operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private String getGitLabBaseUrl(Map<String, Object> credentials) {
		String serverUrl = String.valueOf(credentials.getOrDefault("serverUrl",
			credentials.getOrDefault("baseUrl", DEFAULT_BASE_URL)));
		if (serverUrl.isEmpty() || "null".equals(serverUrl)) {
			serverUrl = DEFAULT_BASE_URL;
		}
		// Remove trailing slash if present
		if (serverUrl.endsWith("/")) {
			serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
		}
		return serverUrl + "/api/v4";
	}

	private Map<String, String> authHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		String accessToken = stringVal(credentials, "accessToken");
		String privateToken = stringVal(credentials, "token");
		if (!accessToken.isEmpty()) {
			headers.put("Authorization", "Bearer " + accessToken);
		} else if (!privateToken.isEmpty()) {
			headers.put("PRIVATE-TOKEN", privateToken);
		}
		return headers;
	}

	private String stringVal(Map<String, Object> map, String key) {
		Object val = map.get(key);
		return val != null ? String.valueOf(val) : "";
	}

	private int getLimit(NodeExecutionContext context) {
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 20);
		return returnAll ? 100 : Math.min(limit, 100);
	}

	private void putIfNotEmpty(Map<String, Object> map, String key, String value) {
		if (value != null && !value.isEmpty()) {
			map.put(key, value);
		}
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toArrayResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		List<Map<String, Object>> parsed = parseArrayResponse(response);
		List<Map<String, Object>> items = new ArrayList<>();
		for (Map<String, Object> item : parsed) {
			items.add(wrapInJson((Object) item));
		}
		return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response, String id) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "id", id))));
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("GitLab API error (HTTP " + response.statusCode() + "): " + body);
	}
}
