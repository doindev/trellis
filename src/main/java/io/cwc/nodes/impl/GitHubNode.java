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
 * GitHub Node -- manage files, issues, releases, repositories, reviews,
 * and users via the GitHub REST API.
 */
@Slf4j
@Node(
	type = "github",
	displayName = "GitHub",
	description = "Manage files, issues, releases, repositories, reviews, and users in GitHub",
	category = "Development / DevOps",
	icon = "github",
	credentials = {"githubApi"}
)
public class GitHubNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.github.com";

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("repository")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("File").value("file").description("Manage repository files").build(),
				ParameterOption.builder().name("Issue").value("issue").description("Manage issues").build(),
				ParameterOption.builder().name("Release").value("release").description("Manage releases").build(),
				ParameterOption.builder().name("Repository").value("repository").description("Manage repositories").build(),
				ParameterOption.builder().name("Review").value("review").description("Manage pull request reviews").build(),
				ParameterOption.builder().name("User").value("user").description("Manage users").build()
			)).build());

		// File operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a file").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a file").build(),
				ParameterOption.builder().name("Edit").value("edit").description("Edit a file").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a file").build()
			)).build());

		// Issue operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an issue").build(),
				ParameterOption.builder().name("Create Comment").value("createComment").description("Create a comment on an issue").build(),
				ParameterOption.builder().name("Edit").value("edit").description("Edit an issue").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an issue").build(),
				ParameterOption.builder().name("Lock").value("lock").description("Lock an issue").build()
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
				ParameterOption.builder().name("Get").value("get").description("Get a repository").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many repositories").build(),
				ParameterOption.builder().name("Get Issues").value("getIssues").description("Get issues for a repository").build(),
				ParameterOption.builder().name("Get Profile").value("getProfile").description("Get the community profile of a repository").build()
			)).build());

		// Review operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("review"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a review").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a review").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many reviews").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a review").build()
			)).build());

		// User operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getRepositories")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"))))
			.options(List.of(
				ParameterOption.builder().name("Get Repositories").value("getRepositories").description("Get repositories of a user").build(),
				ParameterOption.builder().name("Invite").value("invite").description("Invite a user to a repository").build()
			)).build());

		// Common parameters
		params.add(NodeParameter.builder()
			.name("owner").displayName("Repository Owner")
			.type(ParameterType.STRING).required(true)
			.description("Owner (user or organization) of the repository.")
			.build());

		params.add(NodeParameter.builder()
			.name("repo").displayName("Repository Name")
			.type(ParameterType.STRING).required(true)
			.description("Name of the repository.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file", "issue", "release", "repository", "review"))))
			.build());

		params.add(NodeParameter.builder()
			.name("filePath").displayName("File Path")
			.type(ParameterType.STRING).required(true)
			.description("Path of the file in the repository.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"))))
			.build());

		params.add(NodeParameter.builder()
			.name("fileContent").displayName("File Content")
			.type(ParameterType.STRING)
			.description("Content of the file (will be Base64 encoded).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("create", "edit"))))
			.build());

		params.add(NodeParameter.builder()
			.name("commitMessage").displayName("Commit Message")
			.type(ParameterType.STRING)
			.description("Commit message for file operations.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"))))
			.build());

		params.add(NodeParameter.builder()
			.name("fileSha").displayName("File SHA")
			.type(ParameterType.STRING)
			.description("SHA of the file to update or delete.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("edit", "delete"))))
			.build());

		params.add(NodeParameter.builder()
			.name("branch").displayName("Branch")
			.type(ParameterType.STRING).defaultValue("main")
			.description("Branch name.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"))))
			.build());

		params.add(NodeParameter.builder()
			.name("issueNumber").displayName("Issue Number")
			.type(ParameterType.NUMBER)
			.description("Number of the issue.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("createComment", "edit", "get", "lock"))))
			.build());

		params.add(NodeParameter.builder()
			.name("issueTitle").displayName("Title")
			.type(ParameterType.STRING)
			.description("Title of the issue.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("create", "edit"))))
			.build());

		params.add(NodeParameter.builder()
			.name("issueBody").displayName("Body")
			.type(ParameterType.STRING)
			.description("Body content of the issue or comment.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("create", "createComment", "edit"))))
			.build());

		params.add(NodeParameter.builder()
			.name("lockReason").displayName("Lock Reason")
			.type(ParameterType.OPTIONS)
			.description("Reason for locking the issue.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("lock"))))
			.options(List.of(
				ParameterOption.builder().name("Off Topic").value("off-topic").build(),
				ParameterOption.builder().name("Too Heated").value("too heated").build(),
				ParameterOption.builder().name("Resolved").value("resolved").build(),
				ParameterOption.builder().name("Spam").value("spam").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("releaseId").displayName("Release ID")
			.type(ParameterType.STRING)
			.description("ID of the release.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("release"), "operation", List.of("get", "update", "delete"))))
			.build());

		params.add(NodeParameter.builder()
			.name("tagName").displayName("Tag Name")
			.type(ParameterType.STRING)
			.description("Tag name for the release.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("release"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("releaseName").displayName("Release Name")
			.type(ParameterType.STRING)
			.description("Name of the release.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("release"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("releaseBody").displayName("Release Body")
			.type(ParameterType.STRING)
			.description("Body/description of the release.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("release"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("draft").displayName("Draft")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Whether the release is a draft.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("release"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("prerelease").displayName("Prerelease")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Whether the release is a prerelease.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("release"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("pullNumber").displayName("Pull Request Number")
			.type(ParameterType.NUMBER)
			.description("Number of the pull request.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("review"))))
			.build());

		params.add(NodeParameter.builder()
			.name("reviewId").displayName("Review ID")
			.type(ParameterType.STRING)
			.description("ID of the review.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("review"), "operation", List.of("get", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("reviewBody").displayName("Review Body")
			.type(ParameterType.STRING)
			.description("Body of the review.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("review"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("reviewEvent").displayName("Review Event")
			.type(ParameterType.OPTIONS)
			.description("The event to perform on the review.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("review"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Approve").value("APPROVE").build(),
				ParameterOption.builder().name("Request Changes").value("REQUEST_CHANGES").build(),
				ParameterOption.builder().name("Comment").value("COMMENT").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("inviteUsername").displayName("Username")
			.type(ParameterType.STRING)
			.description("Username of the user to invite.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("invite"))))
			.build());

		params.add(NodeParameter.builder()
			.name("invitePermission").displayName("Permission")
			.type(ParameterType.OPTIONS).defaultValue("push")
			.description("Permission level for the invitation.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("invite"))))
			.options(List.of(
				ParameterOption.builder().name("Read").value("pull").build(),
				ParameterOption.builder().name("Write").value("push").build(),
				ParameterOption.builder().name("Admin").value("admin").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("returnAll").displayName("Return All")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Whether to return all results.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll", "getIssues", "getRepositories"))))
			.build());

		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit")
			.type(ParameterType.NUMBER).defaultValue(30)
			.description("Max number of results to return.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll", "getIssues", "getRepositories"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "repository");
		String operation = context.getParameter("operation", "getAll");
		Map<String, Object> credentials = context.getCredentials();

		try {
			Map<String, String> headers = authHeaders(credentials);

			return switch (resource) {
				case "file" -> executeFile(context, operation, headers);
				case "issue" -> executeIssue(context, operation, headers);
				case "release" -> executeRelease(context, operation, headers);
				case "repository" -> executeRepository(context, operation, headers);
				case "review" -> executeReview(context, operation, headers);
				case "user" -> executeUser(context, operation, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "GitHub API error: " + e.getMessage(), e);
		}
	}

	// ========================= File Operations =========================

	private NodeExecutionResult executeFile(NodeExecutionContext context, String operation, Map<String, String> headers) throws Exception {
		String owner = context.getParameter("owner", "");
		String repo = context.getParameter("repo", "");
		String filePath = context.getParameter("filePath", "");
		String branch = context.getParameter("branch", "main");

		switch (operation) {
			case "create": {
				String content = context.getParameter("fileContent", "");
				String message = context.getParameter("commitMessage", "Create " + filePath);
				String encoded = Base64.getEncoder().encodeToString(content.getBytes());
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("message", message);
				body.put("content", encoded);
				body.put("branch", branch);
				HttpResponse<String> response = put(BASE_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/contents/" + filePath, body, headers);
				return toResult(response);
			}
			case "delete": {
				String message = context.getParameter("commitMessage", "Delete " + filePath);
				String sha = context.getParameter("fileSha", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("message", message);
				body.put("sha", sha);
				body.put("branch", branch);
				HttpResponse<String> response = deleteWithBody(BASE_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/contents/" + filePath, body, headers);
				return toResult(response);
			}
			case "edit": {
				String content = context.getParameter("fileContent", "");
				String message = context.getParameter("commitMessage", "Update " + filePath);
				String sha = context.getParameter("fileSha", "");
				String encoded = Base64.getEncoder().encodeToString(content.getBytes());
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("message", message);
				body.put("content", encoded);
				body.put("sha", sha);
				body.put("branch", branch);
				HttpResponse<String> response = put(BASE_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/contents/" + filePath, body, headers);
				return toResult(response);
			}
			case "get": {
				String url = BASE_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/contents/" + filePath + "?ref=" + encode(branch);
				HttpResponse<String> response = get(url, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown file operation: " + operation);
		}
	}

	// ========================= Issue Operations =========================

	private NodeExecutionResult executeIssue(NodeExecutionContext context, String operation, Map<String, String> headers) throws Exception {
		String owner = context.getParameter("owner", "");
		String repo = context.getParameter("repo", "");

		switch (operation) {
			case "create": {
				String title = context.getParameter("issueTitle", "");
				String body = context.getParameter("issueBody", "");
				Map<String, Object> reqBody = new LinkedHashMap<>();
				reqBody.put("title", title);
				if (body != null && !body.isEmpty()) {
					reqBody.put("body", body);
				}
				HttpResponse<String> response = post(BASE_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/issues", reqBody, headers);
				return toResult(response);
			}
			case "createComment": {
				int issueNumber = toInt(context.getParameters().get("issueNumber"), 0);
				String body = context.getParameter("issueBody", "");
				Map<String, Object> reqBody = Map.of("body", body);
				HttpResponse<String> response = post(BASE_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/issues/" + issueNumber + "/comments", reqBody, headers);
				return toResult(response);
			}
			case "edit": {
				int issueNumber = toInt(context.getParameters().get("issueNumber"), 0);
				Map<String, Object> reqBody = new LinkedHashMap<>();
				String title = context.getParameter("issueTitle", "");
				String body = context.getParameter("issueBody", "");
				if (title != null && !title.isEmpty()) reqBody.put("title", title);
				if (body != null && !body.isEmpty()) reqBody.put("body", body);
				HttpResponse<String> response = patch(BASE_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/issues/" + issueNumber, reqBody, headers);
				return toResult(response);
			}
			case "get": {
				int issueNumber = toInt(context.getParameters().get("issueNumber"), 0);
				HttpResponse<String> response = get(BASE_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/issues/" + issueNumber, headers);
				return toResult(response);
			}
			case "lock": {
				int issueNumber = toInt(context.getParameters().get("issueNumber"), 0);
				String lockReason = context.getParameter("lockReason", "");
				Map<String, Object> reqBody = new LinkedHashMap<>();
				if (lockReason != null && !lockReason.isEmpty()) {
					reqBody.put("lock_reason", lockReason);
				}
				HttpResponse<String> response = put(BASE_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/issues/" + issueNumber + "/lock", reqBody, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown issue operation: " + operation);
		}
	}

	// ========================= Release Operations =========================

	private NodeExecutionResult executeRelease(NodeExecutionContext context, String operation, Map<String, String> headers) throws Exception {
		String owner = context.getParameter("owner", "");
		String repo = context.getParameter("repo", "");

		switch (operation) {
			case "create": {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("tag_name", context.getParameter("tagName", ""));
				putIfNotEmpty(body, "name", context.getParameter("releaseName", ""));
				putIfNotEmpty(body, "body", context.getParameter("releaseBody", ""));
				body.put("draft", toBoolean(context.getParameters().get("draft"), false));
				body.put("prerelease", toBoolean(context.getParameters().get("prerelease"), false));
				HttpResponse<String> response = post(BASE_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/releases", body, headers);
				return toResult(response);
			}
			case "delete": {
				String releaseId = context.getParameter("releaseId", "");
				HttpResponse<String> response = delete(BASE_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/releases/" + encode(releaseId), headers);
				return toDeleteResult(response, releaseId);
			}
			case "get": {
				String releaseId = context.getParameter("releaseId", "");
				HttpResponse<String> response = get(BASE_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/releases/" + encode(releaseId), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(BASE_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/releases?per_page=" + limit, headers);
				return toArrayResult(response);
			}
			case "update": {
				String releaseId = context.getParameter("releaseId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				putIfNotEmpty(body, "tag_name", context.getParameter("tagName", ""));
				putIfNotEmpty(body, "name", context.getParameter("releaseName", ""));
				putIfNotEmpty(body, "body", context.getParameter("releaseBody", ""));
				body.put("draft", toBoolean(context.getParameters().get("draft"), false));
				body.put("prerelease", toBoolean(context.getParameters().get("prerelease"), false));
				HttpResponse<String> response = patch(BASE_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/releases/" + encode(releaseId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown release operation: " + operation);
		}
	}

	// ========================= Repository Operations =========================

	private NodeExecutionResult executeRepository(NodeExecutionContext context, String operation, Map<String, String> headers) throws Exception {
		String owner = context.getParameter("owner", "");
		String repo = context.getParameter("repo", "");

		switch (operation) {
			case "get": {
				HttpResponse<String> response = get(BASE_URL + "/repos/" + encode(owner) + "/" + encode(repo), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(BASE_URL + "/users/" + encode(owner) + "/repos?per_page=" + limit + "&sort=updated", headers);
				return toArrayResult(response);
			}
			case "getIssues": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(BASE_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/issues?per_page=" + limit + "&state=all", headers);
				return toArrayResult(response);
			}
			case "getProfile": {
				HttpResponse<String> response = get(BASE_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/community/profile", headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown repository operation: " + operation);
		}
	}

	// ========================= Review Operations =========================

	private NodeExecutionResult executeReview(NodeExecutionContext context, String operation, Map<String, String> headers) throws Exception {
		String owner = context.getParameter("owner", "");
		String repo = context.getParameter("repo", "");
		int pullNumber = toInt(context.getParameters().get("pullNumber"), 0);

		switch (operation) {
			case "create": {
				Map<String, Object> body = new LinkedHashMap<>();
				putIfNotEmpty(body, "body", context.getParameter("reviewBody", ""));
				String event = context.getParameter("reviewEvent", "");
				if (event != null && !event.isEmpty()) {
					body.put("event", event);
				}
				HttpResponse<String> response = post(BASE_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/pulls/" + pullNumber + "/reviews", body, headers);
				return toResult(response);
			}
			case "get": {
				String reviewId = context.getParameter("reviewId", "");
				HttpResponse<String> response = get(BASE_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/pulls/" + pullNumber + "/reviews/" + encode(reviewId), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(BASE_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/pulls/" + pullNumber + "/reviews?per_page=" + limit, headers);
				return toArrayResult(response);
			}
			case "update": {
				String reviewId = context.getParameter("reviewId", "");
				Map<String, Object> body = Map.of("body", context.getParameter("reviewBody", ""));
				HttpResponse<String> response = put(BASE_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/pulls/" + pullNumber + "/reviews/" + encode(reviewId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown review operation: " + operation);
		}
	}

	// ========================= User Operations =========================

	private NodeExecutionResult executeUser(NodeExecutionContext context, String operation, Map<String, String> headers) throws Exception {
		String owner = context.getParameter("owner", "");

		switch (operation) {
			case "getRepositories": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(BASE_URL + "/users/" + encode(owner) + "/repos?per_page=" + limit + "&sort=updated", headers);
				return toArrayResult(response);
			}
			case "invite": {
				String repo = context.getParameter("repo", "");
				String username = context.getParameter("inviteUsername", "");
				String permission = context.getParameter("invitePermission", "push");
				Map<String, Object> body = Map.of("permission", permission);
				HttpResponse<String> response = put(BASE_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/collaborators/" + encode(username), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown user operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> authHeaders(Map<String, Object> credentials) {
		String token = String.valueOf(credentials.getOrDefault("accessToken",
			credentials.getOrDefault("token", credentials.getOrDefault("apiKey", ""))));
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + token);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/vnd.github+json");
		headers.put("X-GitHub-Api-Version", "2022-11-28");
		return headers;
	}

	private int getLimit(NodeExecutionContext context) {
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 30);
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
		return NodeExecutionResult.error("GitHub API error (HTTP " + response.statusCode() + "): " + body);
	}
}
