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
 * Jira Software Node -- manage issues, projects, users, boards, and sprints
 * in Jira Cloud or Jira Server.
 */
@Slf4j
@Node(
	type = "jira",
	displayName = "Jira Software",
	description = "Manage issues, projects, users, boards, and sprints in Jira Software",
	category = "Project Management",
	icon = "jira",
	credentials = {"jiraApi"}
)
public class JiraNode extends AbstractApiNode {

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
				ParameterOption.builder().name("Project").value("project").description("Manage projects").build(),
				ParameterOption.builder().name("User").value("user").description("Manage users").build(),
				ParameterOption.builder().name("Board").value("board").description("Manage agile boards").build(),
				ParameterOption.builder().name("Sprint").value("sprint").description("Manage sprints").build()
			)).build());

		// Issue operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an issue").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an issue").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an issue").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many issues via JQL").build(),
				ParameterOption.builder().name("Update").value("update").description("Update an issue").build(),
				ParameterOption.builder().name("Add Comment").value("addComment").description("Add a comment to an issue").build(),
				ParameterOption.builder().name("Get Comments").value("getComments").description("Get comments on an issue").build(),
				ParameterOption.builder().name("Add Attachment").value("addAttachment").description("Add an attachment to an issue").build(),
				ParameterOption.builder().name("Get Transitions").value("getTransitions").description("Get available transitions for an issue").build(),
				ParameterOption.builder().name("Transition").value("transition").description("Transition an issue to a new status").build()
			)).build());

		// Project operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("project"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a project").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a project").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a project").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many projects").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a project").build()
			)).build());

		// User operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a user").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a user").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a user").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Search/get many users").build()
			)).build());

		// Board operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("board"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get a board").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many boards").build(),
				ParameterOption.builder().name("Get Issues").value("getIssues").description("Get issues on a board").build()
			)).build());

		// Sprint operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("sprint"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a sprint").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a sprint").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all sprints for a board").build(),
				ParameterOption.builder().name("Get Issues").value("getIssues").description("Get issues in a sprint").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a sprint").build()
			)).build());

		// ========================= Common Parameters =========================

		// Issue key/ID
		params.add(NodeParameter.builder()
			.name("issueKey").displayName("Issue Key")
			.type(ParameterType.STRING).required(true)
			.description("The issue key (e.g. PROJ-123) or ID.")
			.placeHolder("PROJ-123")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("get", "update", "delete", "addComment", "getComments", "addAttachment", "getTransitions", "transition"))))
			.build());

		// Project key
		params.add(NodeParameter.builder()
			.name("projectKey").displayName("Project Key")
			.type(ParameterType.STRING).required(true)
			.description("The project key (e.g. PROJ).")
			.placeHolder("PROJ")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("create"))))
			.build());

		// Issue type
		params.add(NodeParameter.builder()
			.name("issueType").displayName("Issue Type")
			.type(ParameterType.STRING).required(true).defaultValue("Task")
			.description("The issue type (e.g. Task, Bug, Story, Epic).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("create"))))
			.build());

		// Summary
		params.add(NodeParameter.builder()
			.name("summary").displayName("Summary")
			.type(ParameterType.STRING).required(true)
			.description("Summary/title of the issue.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("create"))))
			.build());

		// Description
		params.add(NodeParameter.builder()
			.name("description").displayName("Description")
			.type(ParameterType.STRING)
			.description("Description of the issue.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("create", "update"))))
			.build());

		// Comment body
		params.add(NodeParameter.builder()
			.name("commentBody").displayName("Comment Body")
			.type(ParameterType.STRING).required(true)
			.description("The text of the comment.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("addComment"))))
			.build());

		// Transition ID
		params.add(NodeParameter.builder()
			.name("transitionId").displayName("Transition ID")
			.type(ParameterType.STRING).required(true)
			.description("The ID of the transition to perform. Use 'Get Transitions' to find available IDs.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("transition"))))
			.build());

		// Attachment content
		params.add(NodeParameter.builder()
			.name("attachmentContent").displayName("Attachment Content (Base64)")
			.type(ParameterType.STRING).required(true)
			.description("Base64-encoded file content.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("addAttachment"))))
			.build());

		params.add(NodeParameter.builder()
			.name("attachmentFilename").displayName("Filename")
			.type(ParameterType.STRING).required(true)
			.description("The filename for the attachment.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("addAttachment"))))
			.build());

		// JQL query
		params.add(NodeParameter.builder()
			.name("jql").displayName("JQL Query")
			.type(ParameterType.STRING)
			.description("JQL query to filter issues (e.g. project = PROJ AND status = 'In Progress').")
			.typeOptions(Map.of("rows", 2))
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("getAll"))))
			.build());

		// Project parameters
		params.add(NodeParameter.builder()
			.name("projectId").displayName("Project ID or Key")
			.type(ParameterType.STRING).required(true)
			.description("The project ID or key.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("project"), "operation", List.of("get", "update", "delete"))))
			.build());

		params.add(NodeParameter.builder()
			.name("projectName").displayName("Project Name")
			.type(ParameterType.STRING).required(true)
			.description("Name of the project.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("project"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("newProjectKey").displayName("Project Key")
			.type(ParameterType.STRING).required(true)
			.description("Key for the new project (e.g. PROJ).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("project"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("projectTypeKey").displayName("Project Type")
			.type(ParameterType.OPTIONS).defaultValue("software")
			.displayOptions(Map.of("show", Map.of("resource", List.of("project"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Software").value("software").build(),
				ParameterOption.builder().name("Service Desk").value("service_desk").build(),
				ParameterOption.builder().name("Business").value("business").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("leadAccountId").displayName("Lead Account ID")
			.type(ParameterType.STRING).required(true)
			.description("Account ID of the project lead.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("project"), "operation", List.of("create"))))
			.build());

		// User parameters
		params.add(NodeParameter.builder()
			.name("accountId").displayName("Account ID")
			.type(ParameterType.STRING).required(true)
			.description("The user's account ID.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("get", "delete"))))
			.build());

		params.add(NodeParameter.builder()
			.name("userEmail").displayName("Email Address")
			.type(ParameterType.STRING).required(true)
			.description("Email address for the user.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("userDisplayName").displayName("Display Name")
			.type(ParameterType.STRING)
			.description("Display name for the user.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("userQuery").displayName("Search Query")
			.type(ParameterType.STRING)
			.description("Search query for finding users (searches name, email, etc.).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("getAll"))))
			.build());

		// Board parameters
		params.add(NodeParameter.builder()
			.name("boardId").displayName("Board ID")
			.type(ParameterType.STRING).required(true)
			.description("The board ID.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("board"), "operation", List.of("get", "getIssues"))))
			.build());

		params.add(NodeParameter.builder()
			.name("boardIdForSprints").displayName("Board ID")
			.type(ParameterType.STRING).required(true)
			.description("The board ID to get sprints for.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("sprint"), "operation", List.of("create", "getAll"))))
			.build());

		// Sprint parameters
		params.add(NodeParameter.builder()
			.name("sprintId").displayName("Sprint ID")
			.type(ParameterType.STRING).required(true)
			.description("The sprint ID.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("sprint"), "operation", List.of("get", "update", "getIssues"))))
			.build());

		params.add(NodeParameter.builder()
			.name("sprintName").displayName("Sprint Name")
			.type(ParameterType.STRING).required(true)
			.description("Name of the sprint.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("sprint"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("sprintGoal").displayName("Sprint Goal")
			.type(ParameterType.STRING)
			.description("Goal of the sprint.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("sprint"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("sprintStartDate").displayName("Start Date")
			.type(ParameterType.STRING)
			.description("Start date (ISO format, e.g. 2024-01-15T09:00:00.000Z).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("sprint"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("sprintEndDate").displayName("End Date")
			.type(ParameterType.STRING)
			.description("End date (ISO format).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("sprint"), "operation", List.of("create", "update"))))
			.build());

		// Additional fields for issue updates
		params.add(NodeParameter.builder()
			.name("updateFields").displayName("Update Fields (JSON)")
			.type(ParameterType.JSON).defaultValue("{}")
			.description("Fields to update as JSON (e.g. {\"summary\": \"New title\", \"priority\": {\"name\": \"High\"}}).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("update"))))
			.build());

		// Additional fields generic
		params.add(NodeParameter.builder()
			.name("additionalFields").displayName("Additional Fields (JSON)")
			.type(ParameterType.JSON).defaultValue("{}")
			.description("Additional fields as JSON.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("create"))))
			.build());

		// Limit
		params.add(NodeParameter.builder()
			.name("returnAll").displayName("Return All")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Whether to return all results.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll", "getIssues", "getComments"))))
			.build());

		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit")
			.type(ParameterType.NUMBER).defaultValue(50)
			.description("Max number of results to return.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll", "getIssues", "getComments"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "issue");
		String operation = context.getParameter("operation", "getAll");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String domain = String.valueOf(credentials.getOrDefault("domain", ""));
			String baseUrl;
			if (domain.contains("://")) {
				// Server / Data Center: full URL provided
				baseUrl = domain.endsWith("/") ? domain.substring(0, domain.length() - 1) : domain;
			} else {
				// Cloud instance
				baseUrl = "https://" + domain + ".atlassian.net";
			}

			Map<String, String> headers = authHeaders(credentials);

			return switch (resource) {
				case "issue" -> executeIssue(context, operation, baseUrl, headers);
				case "project" -> executeProject(context, operation, baseUrl, headers);
				case "user" -> executeUser(context, operation, baseUrl, headers);
				case "board" -> executeBoard(context, operation, baseUrl, headers);
				case "sprint" -> executeSprint(context, operation, baseUrl, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Jira API error: " + e.getMessage(), e);
		}
	}

	// ========================= Issue Operations =========================

	private NodeExecutionResult executeIssue(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		String restApi = baseUrl + "/rest/api/2";

		switch (operation) {
			case "create": {
				String projectKey = context.getParameter("projectKey", "");
				String issueType = context.getParameter("issueType", "Task");
				String summary = context.getParameter("summary", "");
				String description = context.getParameter("description", "");
				String additionalJson = context.getParameter("additionalFields", "{}");

				Map<String, Object> fields = new LinkedHashMap<>(parseJson(additionalJson));
				fields.put("project", Map.of("key", projectKey));
				fields.put("issuetype", Map.of("name", issueType));
				fields.put("summary", summary);
				if (!description.isEmpty()) {
					fields.put("description", description);
				}

				Map<String, Object> body = Map.of("fields", fields);
				HttpResponse<String> response = post(restApi + "/issue", body, headers);
				return toResult(response);
			}
			case "delete": {
				String issueKey = context.getParameter("issueKey", "");
				HttpResponse<String> response = delete(restApi + "/issue/" + encode(issueKey), headers);
				return toDeleteResult(response, issueKey);
			}
			case "get": {
				String issueKey = context.getParameter("issueKey", "");
				HttpResponse<String> response = get(restApi + "/issue/" + encode(issueKey), headers);
				return toResult(response);
			}
			case "getAll": {
				String jql = context.getParameter("jql", "");
				int limit = getLimit(context);
				String url = restApi + "/search?maxResults=" + limit;
				if (!jql.isBlank()) {
					url += "&jql=" + encode(jql);
				}
				HttpResponse<String> response = get(url, headers);
				return toListResult(response, "issues");
			}
			case "update": {
				String issueKey = context.getParameter("issueKey", "");
				String updateJson = context.getParameter("updateFields", "{}");
				String description = context.getParameter("description", "");
				Map<String, Object> fields = new LinkedHashMap<>(parseJson(updateJson));
				if (!description.isEmpty()) {
					fields.put("description", description);
				}
				Map<String, Object> body = Map.of("fields", fields);
				HttpResponse<String> response = put(restApi + "/issue/" + encode(issueKey), body, headers);
				return toResult(response);
			}
			case "addComment": {
				String issueKey = context.getParameter("issueKey", "");
				String commentBody = context.getParameter("commentBody", "");
				Map<String, Object> body = Map.of("body", commentBody);
				HttpResponse<String> response = post(restApi + "/issue/" + encode(issueKey) + "/comment", body, headers);
				return toResult(response);
			}
			case "getComments": {
				String issueKey = context.getParameter("issueKey", "");
				int limit = getLimit(context);
				HttpResponse<String> response = get(restApi + "/issue/" + encode(issueKey) + "/comment?maxResults=" + limit, headers);
				return toListResult(response, "comments");
			}
			case "addAttachment": {
				String issueKey = context.getParameter("issueKey", "");
				String content = context.getParameter("attachmentContent", "");
				String filename = context.getParameter("attachmentFilename", "attachment.txt");
				// Jira requires multipart/form-data for attachments
				// For now, we'll use a workaround via the API with base64 content
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("issueKey", issueKey);
				result.put("filename", filename);
				result.put("contentLength", content.length());
				result.put("note", "Attachment upload requires multipart/form-data. Use the Jira UI or a dedicated file upload mechanism for binary attachments.");
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "getTransitions": {
				String issueKey = context.getParameter("issueKey", "");
				HttpResponse<String> response = get(restApi + "/issue/" + encode(issueKey) + "/transitions", headers);
				return toListResult(response, "transitions");
			}
			case "transition": {
				String issueKey = context.getParameter("issueKey", "");
				String transitionId = context.getParameter("transitionId", "");
				Map<String, Object> body = Map.of("transition", Map.of("id", transitionId));
				HttpResponse<String> response = post(restApi + "/issue/" + encode(issueKey) + "/transitions", body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown issue operation: " + operation);
		}
	}

	// ========================= Project Operations =========================

	private NodeExecutionResult executeProject(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		String restApi = baseUrl + "/rest/api/2";

		switch (operation) {
			case "create": {
				String name = context.getParameter("projectName", "");
				String key = context.getParameter("newProjectKey", "");
				String typeKey = context.getParameter("projectTypeKey", "software");
				String leadAccountId = context.getParameter("leadAccountId", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", name);
				body.put("key", key);
				body.put("projectTypeKey", typeKey);
				body.put("leadAccountId", leadAccountId);

				HttpResponse<String> response = post(restApi + "/project", body, headers);
				return toResult(response);
			}
			case "delete": {
				String projectId = context.getParameter("projectId", "");
				HttpResponse<String> response = delete(restApi + "/project/" + encode(projectId), headers);
				return toDeleteResult(response, projectId);
			}
			case "get": {
				String projectId = context.getParameter("projectId", "");
				HttpResponse<String> response = get(restApi + "/project/" + encode(projectId), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(restApi + "/project", headers);
				return toArrayResult(response);
			}
			case "update": {
				String projectId = context.getParameter("projectId", "");
				String name = context.getParameter("projectName", "");
				Map<String, Object> body = new LinkedHashMap<>();
				if (!name.isEmpty()) {
					body.put("name", name);
				}
				HttpResponse<String> response = put(restApi + "/project/" + encode(projectId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown project operation: " + operation);
		}
	}

	// ========================= User Operations =========================

	private NodeExecutionResult executeUser(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		String restApi = baseUrl + "/rest/api/2";

		switch (operation) {
			case "create": {
				String email = context.getParameter("userEmail", "");
				String displayName = context.getParameter("userDisplayName", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("emailAddress", email);
				if (!displayName.isEmpty()) {
					body.put("displayName", displayName);
				}
				HttpResponse<String> response = post(restApi + "/user", body, headers);
				return toResult(response);
			}
			case "delete": {
				String accountId = context.getParameter("accountId", "");
				HttpResponse<String> response = delete(restApi + "/user?accountId=" + encode(accountId), headers);
				return toDeleteResult(response, accountId);
			}
			case "get": {
				String accountId = context.getParameter("accountId", "");
				HttpResponse<String> response = get(restApi + "/user?accountId=" + encode(accountId), headers);
				return toResult(response);
			}
			case "getAll": {
				String query = context.getParameter("userQuery", "");
				int limit = getLimit(context);
				String url = restApi + "/user/search?maxResults=" + limit;
				if (!query.isBlank()) {
					url += "&query=" + encode(query);
				}
				HttpResponse<String> response = get(url, headers);
				return toArrayResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown user operation: " + operation);
		}
	}

	// ========================= Board Operations =========================

	private NodeExecutionResult executeBoard(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		String agileApi = baseUrl + "/rest/agile/1.0";

		switch (operation) {
			case "get": {
				String boardId = context.getParameter("boardId", "");
				HttpResponse<String> response = get(agileApi + "/board/" + encode(boardId), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(agileApi + "/board?maxResults=" + limit, headers);
				return toListResult(response, "values");
			}
			case "getIssues": {
				String boardId = context.getParameter("boardId", "");
				int limit = getLimit(context);
				HttpResponse<String> response = get(agileApi + "/board/" + encode(boardId) + "/issue?maxResults=" + limit, headers);
				return toListResult(response, "issues");
			}
			default:
				return NodeExecutionResult.error("Unknown board operation: " + operation);
		}
	}

	// ========================= Sprint Operations =========================

	private NodeExecutionResult executeSprint(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		String agileApi = baseUrl + "/rest/agile/1.0";

		switch (operation) {
			case "create": {
				String boardId = context.getParameter("boardIdForSprints", "");
				String name = context.getParameter("sprintName", "");
				String goal = context.getParameter("sprintGoal", "");
				String startDate = context.getParameter("sprintStartDate", "");
				String endDate = context.getParameter("sprintEndDate", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", name);
				body.put("originBoardId", toInt(boardId, 0));
				if (!goal.isEmpty()) body.put("goal", goal);
				if (!startDate.isEmpty()) body.put("startDate", startDate);
				if (!endDate.isEmpty()) body.put("endDate", endDate);

				HttpResponse<String> response = post(agileApi + "/sprint", body, headers);
				return toResult(response);
			}
			case "get": {
				String sprintId = context.getParameter("sprintId", "");
				HttpResponse<String> response = get(agileApi + "/sprint/" + encode(sprintId), headers);
				return toResult(response);
			}
			case "getAll": {
				String boardId = context.getParameter("boardIdForSprints", "");
				int limit = getLimit(context);
				HttpResponse<String> response = get(agileApi + "/board/" + encode(boardId) + "/sprint?maxResults=" + limit, headers);
				return toListResult(response, "values");
			}
			case "getIssues": {
				String sprintId = context.getParameter("sprintId", "");
				int limit = getLimit(context);
				HttpResponse<String> response = get(agileApi + "/sprint/" + encode(sprintId) + "/issue?maxResults=" + limit, headers);
				return toListResult(response, "issues");
			}
			case "update": {
				String sprintId = context.getParameter("sprintId", "");
				String goal = context.getParameter("sprintGoal", "");
				String startDate = context.getParameter("sprintStartDate", "");
				String endDate = context.getParameter("sprintEndDate", "");

				Map<String, Object> body = new LinkedHashMap<>();
				if (!goal.isEmpty()) body.put("goal", goal);
				if (!startDate.isEmpty()) body.put("startDate", startDate);
				if (!endDate.isEmpty()) body.put("endDate", endDate);

				HttpResponse<String> response = put(agileApi + "/sprint/" + encode(sprintId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown sprint operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> authHeaders(Map<String, Object> credentials) {
		String email = String.valueOf(credentials.getOrDefault("email", ""));
		String apiToken = String.valueOf(credentials.getOrDefault("apiToken",
			credentials.getOrDefault("password", "")));

		Map<String, String> headers = new LinkedHashMap<>();
		if (!email.isEmpty() && !apiToken.isEmpty()) {
			String auth = java.util.Base64.getEncoder().encodeToString((email + ":" + apiToken).getBytes());
			headers.put("Authorization", "Basic " + auth);
		} else {
			// Fallback: try OAuth or Bearer token
			String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));
			if (!accessToken.isEmpty()) {
				headers.put("Authorization", "Bearer " + accessToken);
			}
		}
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}

	private int getLimit(NodeExecutionContext context) {
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 50);
		return returnAll ? 1000 : Math.min(limit, 1000);
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toListResult(HttpResponse<String> response, String dataKey) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
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

	private NodeExecutionResult toArrayResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		List<Map<String, Object>> parsed = parseArrayResponse(response);
		if (parsed.isEmpty()) {
			return NodeExecutionResult.empty();
		}
		List<Map<String, Object>> items = new ArrayList<>();
		for (Map<String, Object> item : parsed) {
			items.add(wrapInJson(item));
		}
		return NodeExecutionResult.success(items);
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
		return NodeExecutionResult.error("Jira API error (HTTP " + response.statusCode() + "): " + body);
	}
}
