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
 * Jira Trigger Node -- polls for new or updated issues using JQL search.
 */
@Slf4j
@Node(
	type = "jiraTrigger",
	displayName = "Jira Trigger",
	description = "Polls for new or updated issues in Jira using JQL search",
	category = "Project Management",
	icon = "jira",
	trigger = true,
	polling = true,
	credentials = {"jiraApi"}
)
public class JiraTriggerNode extends AbstractApiNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of(); // trigger node has no inputs
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
			.name("triggerOn").displayName("Trigger On")
			.type(ParameterType.OPTIONS).required(true).defaultValue("newIssue")
			.options(List.of(
				ParameterOption.builder().name("New Issues").value("newIssue")
					.description("Trigger when new issues are created").build(),
				ParameterOption.builder().name("Updated Issues").value("updatedIssue")
					.description("Trigger when issues are updated").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("projectKey").displayName("Project Key")
			.type(ParameterType.STRING)
			.description("The project key to filter issues (e.g. PROJ). Leave empty for all projects.")
			.placeHolder("PROJ")
			.build());

		params.add(NodeParameter.builder()
			.name("additionalJql").displayName("Additional JQL")
			.type(ParameterType.STRING)
			.description("Additional JQL clauses to append to the query.")
			.placeHolder("AND status = 'In Progress'")
			.build());

		params.add(NodeParameter.builder()
			.name("maxResults").displayName("Max Results Per Poll")
			.type(ParameterType.NUMBER).defaultValue(50)
			.description("Maximum number of issues to return per poll.")
			.build());

		return params;
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		Map<String, Object> staticData = context.getStaticData();
		if (staticData == null) staticData = new LinkedHashMap<>();

		try {
			String baseUrl = getJiraBaseUrl(credentials);
			Map<String, String> headers = getAuthHeaders(credentials);
			String triggerOn = context.getParameter("triggerOn", "newIssue");

			// Build JQL
			String projectKey = context.getParameter("projectKey", "");
			String additionalJql = context.getParameter("additionalJql", "");
			int maxResults = toInt(context.getParameter("maxResults", 50), 50);

			String lastPollTime = (String) staticData.get("lastPollTime");
			String dateField = "newIssue".equals(triggerOn) ? "created" : "updated";

			StringBuilder jql = new StringBuilder();
			if (!projectKey.isEmpty()) {
				jql.append("project = ").append(projectKey);
			}
			if (lastPollTime != null) {
				if (jql.length() > 0) jql.append(" AND ");
				jql.append(dateField).append(" >= '").append(lastPollTime).append("'");
			}
			if (!additionalJql.isEmpty()) {
				if (jql.length() > 0) jql.append(" ");
				jql.append(additionalJql);
			}
			jql.append(" ORDER BY ").append(dateField).append(" DESC");

			// Make request
			String url = baseUrl + "/rest/api/2/search?jql=" + encode(jql.toString()) + "&maxResults=" + maxResults;
			HttpResponse<String> response = get(url, headers);

			if (response.statusCode() >= 400) {
				String body = response.body() != null ? response.body() : "";
				if (body.length() > 300) body = body.substring(0, 300) + "...";
				return NodeExecutionResult.error("Jira API error (HTTP " + response.statusCode() + "): " + body);
			}

			Map<String, Object> parsed = parseResponse(response);
			Object issuesObj = parsed.get("issues");

			// Update static data with current timestamp
			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
			String now = java.time.Instant.now().toString().replace("T", " ").substring(0, 19);
			newStaticData.put("lastPollTime", now);

			List<Map<String, Object>> items = new ArrayList<>();
			if (issuesObj instanceof List) {
				for (Object issue : (List<?>) issuesObj) {
					if (issue instanceof Map) {
						Map<String, Object> issueMap = new LinkedHashMap<>((Map<String, Object>) issue);
						issueMap.put("_triggerTimestamp", System.currentTimeMillis());
						items.add(wrapInJson(issueMap));
					}
				}
			}

			if (items.isEmpty()) {
				return NodeExecutionResult.builder()
					.output(List.of(List.of()))
					.staticData(newStaticData)
					.build();
			}

			log.debug("Jira trigger: found {} issues", items.size());
			return NodeExecutionResult.builder()
				.output(List.of(items))
				.staticData(newStaticData)
				.build();

		} catch (Exception e) {
			return handleError(context, "Jira Trigger error: " + e.getMessage(), e);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		String email = String.valueOf(credentials.getOrDefault("email", credentials.getOrDefault("username", "")));
		String apiToken = String.valueOf(credentials.getOrDefault("apiToken", credentials.getOrDefault("password", "")));
		String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));

		if (!accessToken.isEmpty()) {
			headers.put("Authorization", "Bearer " + accessToken);
		} else {
			String auth = base64Encoder.encodeToString((email + ":" + apiToken).getBytes());
			headers.put("Authorization", "Basic " + auth);
		}
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}

	private String getJiraBaseUrl(Map<String, Object> credentials) {
		String baseUrl = String.valueOf(credentials.getOrDefault("baseUrl",
				credentials.getOrDefault("domain", "https://your-domain.atlassian.net")));
		if (baseUrl.endsWith("/")) {
			baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		}
		return baseUrl;
	}

	private static final java.util.Base64.Encoder base64Encoder = java.util.Base64.getEncoder();
}
