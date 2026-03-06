package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * CircleCI — manage pipelines in CircleCI.
 */
@Node(
		type = "circleCi",
		displayName = "CircleCI",
		description = "Manage pipelines in CircleCI",
		category = "Development / DevOps",
		icon = "circleCi",
		credentials = {"circleCiApi"}
)
public class CircleCiNode extends AbstractApiNode {

	private static final String BASE_URL = "https://circleci.com/api/v2";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = (String) credentials.getOrDefault("apiKey", "");

		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Circle-Token", apiKey);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		String vcs = context.getParameter("vcs", "github");
		String projectSlug = context.getParameter("projectSlug", "");
		String slug = vcs + "/" + projectSlug.replace("/", "%2F");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "get" -> {
						String pipelineNumber = context.getParameter("pipelineNumber", "");
						HttpResponse<String> response = get(BASE_URL + "/project/" + slug + "/pipeline/" + pipelineNumber, headers);
						yield parseResponse(response);
					}
					case "getAll" -> {
						int limit = toInt(context.getParameters().get("limit"), 100);
						String branch = context.getParameter("branch", "");
						String url = BASE_URL + "/project/" + slug + "/pipeline";
						StringBuilder params = new StringBuilder();
						if (!branch.isEmpty()) params.append("branch=").append(encode(branch));
						if (limit > 0) {
							if (!params.isEmpty()) params.append("&");
							params.append("limit=").append(limit);
						}
						if (!params.isEmpty()) url += "?" + params;
						HttpResponse<String> response = get(url, headers);
						yield parseResponse(response);
					}
					case "trigger" -> {
						Map<String, Object> body = new LinkedHashMap<>();
						String branch = context.getParameter("branch", "");
						if (!branch.isEmpty()) body.put("branch", branch);
						String tag = context.getParameter("tag", "");
						if (!tag.isEmpty()) body.put("tag", tag);
						HttpResponse<String> response = post(BASE_URL + "/project/" + slug + "/pipeline", body, headers);
						yield parseResponse(response);
					}
					default -> throw new IllegalArgumentException("Unknown operation: " + operation);
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

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Trigger").value("trigger").build()
						)).build(),
				NodeParameter.builder()
						.name("vcs").displayName("VCS")
						.type(ParameterType.OPTIONS).defaultValue("github")
						.options(List.of(
								ParameterOption.builder().name("GitHub").value("github").build(),
								ParameterOption.builder().name("Bitbucket").value("bitbucket").build()
						)).build(),
				NodeParameter.builder()
						.name("projectSlug").displayName("Project Slug")
						.type(ParameterType.STRING).defaultValue("")
						.description("Project slug in org-name/repo-name format.").build(),
				NodeParameter.builder()
						.name("pipelineNumber").displayName("Pipeline Number")
						.type(ParameterType.NUMBER).defaultValue(1)
						.description("Pipeline number to retrieve.").build(),
				NodeParameter.builder()
						.name("branch").displayName("Branch")
						.type(ParameterType.STRING).defaultValue("")
						.description("Branch name to filter or trigger.").build(),
				NodeParameter.builder()
						.name("tag").displayName("Tag")
						.type(ParameterType.STRING).defaultValue("")
						.description("Tag to trigger pipeline for.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max pipelines to return (1-500).").build()
		);
	}
}
