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

@Slf4j
@Node(
	type = "linear",
	displayName = "Linear",
	description = "Manage issues and teams in Linear using the GraphQL API.",
	category = "Project Management",
	icon = "linear",
	credentials = {"linearApi"}
)
public class LinearNode extends AbstractApiNode {

	private static final String GRAPHQL_URL = "https://api.linear.app/graphql";

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
				ParameterOption.builder().name("Team").value("team").description("Get teams").build()
			)).build());

		// Operation selectors
		addOperationSelectors(params);

		// Resource-specific parameters
		addIssueParameters(params);

		return params;
	}

	// ========================= Operation Selectors =========================

	private void addOperationSelectors(List<NodeParameter> params) {
		// Issue operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an issue").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an issue").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an issue").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all issues").build(),
				ParameterOption.builder().name("Update").value("update").description("Update an issue").build()
			)).build());

		// Team operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("team"))))
			.options(List.of(
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all teams").build()
			)).build());
	}

	// ========================= Issue Parameters =========================

	private void addIssueParameters(List<NodeParameter> params) {
		// Issue > Create: title
		params.add(NodeParameter.builder()
			.name("issueTitle").displayName("Title").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("create"))))
			.build());

		// Issue > Create: teamId
		params.add(NodeParameter.builder()
			.name("teamId").displayName("Team ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("create"))))
			.build());

		// Issue > Create: additional fields
		params.add(NodeParameter.builder()
			.name("issueAdditionalFields").displayName("Additional Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("create"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("description").displayName("Description").type(ParameterType.STRING)
					.typeOptions(Map.of("rows", 4)).build(),
				NodeParameter.builder().name("assigneeId").displayName("Assignee ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("priority").displayName("Priority").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("No Priority").value("0").build(),
						ParameterOption.builder().name("Urgent").value("1").build(),
						ParameterOption.builder().name("High").value("2").build(),
						ParameterOption.builder().name("Medium").value("3").build(),
						ParameterOption.builder().name("Low").value("4").build()
					)).build(),
				NodeParameter.builder().name("stateId").displayName("State ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("labelIds").displayName("Label IDs (comma-separated)").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("estimate").displayName("Estimate").type(ParameterType.NUMBER).build(),
				NodeParameter.builder().name("dueDate").displayName("Due Date (ISO 8601)").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("parentId").displayName("Parent Issue ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("projectId").displayName("Project ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("cycleId").displayName("Cycle ID").type(ParameterType.STRING).build()
			)).build());

		// Issue > Get/Delete/Update: issueId
		params.add(NodeParameter.builder()
			.name("issueId").displayName("Issue ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("get", "delete", "update"))))
			.build());

		// Issue > GetAll: filters
		params.add(NodeParameter.builder()
			.name("issueFilters").displayName("Filters")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("getAll"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("teamId").displayName("Team ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("assigneeId").displayName("Assignee ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("limit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(50).build()
			)).build());

		// Issue > Update: fields
		params.add(NodeParameter.builder()
			.name("issueUpdateFields").displayName("Update Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("title").displayName("Title").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("description").displayName("Description").type(ParameterType.STRING)
					.typeOptions(Map.of("rows", 4)).build(),
				NodeParameter.builder().name("assigneeId").displayName("Assignee ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("priority").displayName("Priority").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("No Priority").value("0").build(),
						ParameterOption.builder().name("Urgent").value("1").build(),
						ParameterOption.builder().name("High").value("2").build(),
						ParameterOption.builder().name("Medium").value("3").build(),
						ParameterOption.builder().name("Low").value("4").build()
					)).build(),
				NodeParameter.builder().name("stateId").displayName("State ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("labelIds").displayName("Label IDs (comma-separated)").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("estimate").displayName("Estimate").type(ParameterType.NUMBER).build(),
				NodeParameter.builder().name("dueDate").displayName("Due Date (ISO 8601)").type(ParameterType.STRING).build()
			)).build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "issue");
		Map<String, Object> credentials = context.getCredentials();

		try {
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (resource) {
				case "issue" -> executeIssue(context, headers);
				case "team" -> executeTeam(headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Linear API error: " + e.getMessage(), e);
		}
	}

	// ========================= Issue Execute =========================

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeIssue(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "create": {
				String title = context.getParameter("issueTitle", "");
				String teamId = context.getParameter("teamId", "");
				Map<String, Object> additional = context.getParameter("issueAdditionalFields", Map.of());

				StringBuilder input = new StringBuilder();
				input.append("title: \"").append(escapeGraphQL(title)).append("\", ");
				input.append("teamId: \"").append(escapeGraphQL(teamId)).append("\"");

				if (additional.get("description") != null) {
					input.append(", description: \"").append(escapeGraphQL(String.valueOf(additional.get("description")))).append("\"");
				}
				if (additional.get("assigneeId") != null) {
					input.append(", assigneeId: \"").append(escapeGraphQL(String.valueOf(additional.get("assigneeId")))).append("\"");
				}
				if (additional.get("priority") != null) {
					input.append(", priority: ").append(additional.get("priority"));
				}
				if (additional.get("stateId") != null) {
					input.append(", stateId: \"").append(escapeGraphQL(String.valueOf(additional.get("stateId")))).append("\"");
				}
				if (additional.get("labelIds") != null) {
					String[] ids = String.valueOf(additional.get("labelIds")).split(",");
					input.append(", labelIds: [");
					for (int i = 0; i < ids.length; i++) {
						if (i > 0) input.append(", ");
						input.append("\"").append(escapeGraphQL(ids[i].trim())).append("\"");
					}
					input.append("]");
				}
				if (additional.get("estimate") != null) {
					input.append(", estimate: ").append(additional.get("estimate"));
				}
				if (additional.get("dueDate") != null) {
					input.append(", dueDate: \"").append(escapeGraphQL(String.valueOf(additional.get("dueDate")))).append("\"");
				}
				if (additional.get("parentId") != null) {
					input.append(", parentId: \"").append(escapeGraphQL(String.valueOf(additional.get("parentId")))).append("\"");
				}
				if (additional.get("projectId") != null) {
					input.append(", projectId: \"").append(escapeGraphQL(String.valueOf(additional.get("projectId")))).append("\"");
				}
				if (additional.get("cycleId") != null) {
					input.append(", cycleId: \"").append(escapeGraphQL(String.valueOf(additional.get("cycleId")))).append("\"");
				}

				String query = "mutation { issueCreate(input: { " + input + " }) { success issue { id identifier title description priority state { name } assignee { name } createdAt updatedAt } } }";
				Map<String, Object> result = executeGraphQL(query, headers);
				Map<String, Object> issueCreate = (Map<String, Object>) result.get("issueCreate");
				if (issueCreate != null && issueCreate.get("issue") != null) {
					return NodeExecutionResult.success(List.of(wrapInJson(issueCreate.get("issue"))));
				}
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "delete": {
				String issueId = context.getParameter("issueId", "");
				String query = "mutation { issueDelete(id: \"" + escapeGraphQL(issueId) + "\") { success } }";
				Map<String, Object> result = executeGraphQL(query, headers);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "get": {
				String issueId = context.getParameter("issueId", "");
				String query = "query { issue(id: \"" + escapeGraphQL(issueId) + "\") { id identifier title description priority estimate dueDate state { id name } assignee { id name email } team { id name } labels { nodes { id name } } project { id name } createdAt updatedAt } }";
				Map<String, Object> result = executeGraphQL(query, headers);
				Object issue = result.get("issue");
				if (issue != null) {
					return NodeExecutionResult.success(List.of(wrapInJson(issue)));
				}
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "getAll": {
				Map<String, Object> filters = context.getParameter("issueFilters", Map.of());
				int limit = toInt(filters.getOrDefault("limit", 50), 50);

				StringBuilder filterClause = new StringBuilder();
				if (filters.get("teamId") != null) {
					filterClause.append("filter: { team: { id: { eq: \"").append(escapeGraphQL(String.valueOf(filters.get("teamId")))).append("\" } }");
					if (filters.get("assigneeId") != null) {
						filterClause.append(", assignee: { id: { eq: \"").append(escapeGraphQL(String.valueOf(filters.get("assigneeId")))).append("\" } }");
					}
					filterClause.append(" }, ");
				} else if (filters.get("assigneeId") != null) {
					filterClause.append("filter: { assignee: { id: { eq: \"").append(escapeGraphQL(String.valueOf(filters.get("assigneeId")))).append("\" } } }, ");
				}

				String query = "query { issues(" + filterClause + "first: " + limit + ") { nodes { id identifier title description priority estimate dueDate state { id name } assignee { id name email } team { id name } createdAt updatedAt } } }";
				Map<String, Object> result = executeGraphQL(query, headers);
				Map<String, Object> issues = (Map<String, Object>) result.get("issues");
				if (issues != null && issues.get("nodes") instanceof List) {
					List<Map<String, Object>> items = new ArrayList<>();
					for (Object node : (List<?>) issues.get("nodes")) {
						if (node instanceof Map) {
							items.add(wrapInJson(node));
						}
					}
					return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "update": {
				String issueId = context.getParameter("issueId", "");
				Map<String, Object> updateFields = context.getParameter("issueUpdateFields", Map.of());

				StringBuilder input = new StringBuilder();
				boolean first = true;
				if (updateFields.get("title") != null) {
					input.append("title: \"").append(escapeGraphQL(String.valueOf(updateFields.get("title")))).append("\"");
					first = false;
				}
				if (updateFields.get("description") != null) {
					if (!first) input.append(", ");
					input.append("description: \"").append(escapeGraphQL(String.valueOf(updateFields.get("description")))).append("\"");
					first = false;
				}
				if (updateFields.get("assigneeId") != null) {
					if (!first) input.append(", ");
					input.append("assigneeId: \"").append(escapeGraphQL(String.valueOf(updateFields.get("assigneeId")))).append("\"");
					first = false;
				}
				if (updateFields.get("priority") != null) {
					if (!first) input.append(", ");
					input.append("priority: ").append(updateFields.get("priority"));
					first = false;
				}
				if (updateFields.get("stateId") != null) {
					if (!first) input.append(", ");
					input.append("stateId: \"").append(escapeGraphQL(String.valueOf(updateFields.get("stateId")))).append("\"");
					first = false;
				}
				if (updateFields.get("labelIds") != null) {
					if (!first) input.append(", ");
					String[] ids = String.valueOf(updateFields.get("labelIds")).split(",");
					input.append("labelIds: [");
					for (int i = 0; i < ids.length; i++) {
						if (i > 0) input.append(", ");
						input.append("\"").append(escapeGraphQL(ids[i].trim())).append("\"");
					}
					input.append("]");
					first = false;
				}
				if (updateFields.get("estimate") != null) {
					if (!first) input.append(", ");
					input.append("estimate: ").append(updateFields.get("estimate"));
					first = false;
				}
				if (updateFields.get("dueDate") != null) {
					if (!first) input.append(", ");
					input.append("dueDate: \"").append(escapeGraphQL(String.valueOf(updateFields.get("dueDate")))).append("\"");
				}

				String query = "mutation { issueUpdate(id: \"" + escapeGraphQL(issueId) + "\", input: { " + input + " }) { success issue { id identifier title description priority state { name } assignee { name } createdAt updatedAt } } }";
				Map<String, Object> result = executeGraphQL(query, headers);
				Map<String, Object> issueUpdate = (Map<String, Object>) result.get("issueUpdate");
				if (issueUpdate != null && issueUpdate.get("issue") != null) {
					return NodeExecutionResult.success(List.of(wrapInJson(issueUpdate.get("issue"))));
				}
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			default:
				return NodeExecutionResult.error("Unknown issue operation: " + operation);
		}
	}

	// ========================= Team Execute =========================

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeTeam(Map<String, String> headers) throws Exception {
		String query = "query { teams { nodes { id name key description createdAt updatedAt } } }";
		Map<String, Object> result = executeGraphQL(query, headers);
		Map<String, Object> teams = (Map<String, Object>) result.get("teams");
		if (teams != null && teams.get("nodes") instanceof List) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Object node : (List<?>) teams.get("nodes")) {
				if (node instanceof Map) {
					items.add(wrapInJson(node));
				}
			}
			return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	// ========================= Helpers =========================

	@SuppressWarnings("unchecked")
	private Map<String, Object> executeGraphQL(String query, Map<String, String> headers) throws Exception {
		Map<String, Object> body = Map.of("query", query);
		HttpResponse<String> response = post(GRAPHQL_URL, body, headers);

		if (response.statusCode() >= 400) {
			String responseBody = response.body() != null ? response.body() : "";
			throw new RuntimeException("Linear GraphQL error (HTTP " + response.statusCode() + "): " + responseBody);
		}

		Map<String, Object> parsed = parseResponse(response);

		// Check for GraphQL errors
		if (parsed.get("errors") != null) {
			List<Map<String, Object>> errors = (List<Map<String, Object>>) parsed.get("errors");
			if (!errors.isEmpty()) {
				String message = String.valueOf(errors.get(0).getOrDefault("message", "Unknown GraphQL error"));
				throw new RuntimeException("Linear GraphQL error: " + message);
			}
		}

		Object data = parsed.get("data");
		if (data instanceof Map) {
			return (Map<String, Object>) data;
		}
		return parsed;
	}

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		String apiKey = String.valueOf(credentials.getOrDefault("apiKey", credentials.getOrDefault("accessToken", "")));
		headers.put("Authorization", "Bearer " + apiKey);
		headers.put("Content-Type", "application/json");
		return headers;
	}

	private String escapeGraphQL(String value) {
		if (value == null) return "";
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}
}
