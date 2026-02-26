package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Medium — publish stories and manage publications on Medium.
 */
@Node(
		type = "medium",
		displayName = "Medium",
		description = "Publish stories on Medium",
		category = "Social Media",
		icon = "medium",
		credentials = {"mediumApi"}
)
public class MediumNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.medium.com/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");

		String resource = context.getParameter("resource", "post");
		String operation = context.getParameter("operation", "create");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "post" -> {
						if ("create".equals(operation)) {
							// First, get the authenticated user ID
							HttpResponse<String> meResponse = get(BASE_URL + "/me", headers);
							Map<String, Object> meData = parseResponse(meResponse);
							@SuppressWarnings("unchecked")
							Map<String, Object> data = (Map<String, Object>) meData.getOrDefault("data", meData);
							String authorId = (String) data.getOrDefault("id", "");

							// Create the post
							Map<String, Object> body = new LinkedHashMap<>();
							body.put("title", context.getParameter("title", ""));
							body.put("contentFormat", context.getParameter("contentFormat", "html"));
							body.put("content", context.getParameter("content", ""));

							String canonicalUrl = context.getParameter("canonicalUrl", "");
							if (!canonicalUrl.isEmpty()) body.put("canonicalUrl", canonicalUrl);

							String tags = context.getParameter("tags", "");
							if (!tags.isEmpty()) {
								body.put("tags", Arrays.stream(tags.split(",")).map(String::trim).toList());
							}

							String publishStatus = context.getParameter("publishStatus", "draft");
							body.put("publishStatus", publishStatus);

							// Publish to user or publication
							String publicationId = context.getParameter("publicationId", "");
							String url;
							if (!publicationId.isEmpty()) {
								url = BASE_URL + "/publications/" + publicationId + "/posts";
							} else {
								url = BASE_URL + "/users/" + authorId + "/posts";
							}

							HttpResponse<String> response = post(url, body, headers);
							yield parseResponse(response);
						}
						throw new IllegalArgumentException("Unknown post operation: " + operation);
					}
					case "publication" -> {
						if ("getAll".equals(operation)) {
							HttpResponse<String> meResponse = get(BASE_URL + "/me", headers);
							Map<String, Object> meData = parseResponse(meResponse);
							@SuppressWarnings("unchecked")
							Map<String, Object> data = (Map<String, Object>) meData.getOrDefault("data", meData);
							String userId = (String) data.getOrDefault("id", "");
							HttpResponse<String> response = get(BASE_URL + "/users/" + userId + "/publications", headers);
							yield parseResponse(response);
						}
						throw new IllegalArgumentException("Unknown publication operation: " + operation);
					}
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

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("post")
						.options(List.of(
								ParameterOption.builder().name("Post").value("post").build(),
								ParameterOption.builder().name("Publication").value("publication").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("create")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Get All").value("getAll").build()
						)).build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("contentFormat").displayName("Content Format")
						.type(ParameterType.OPTIONS).defaultValue("html")
						.options(List.of(
								ParameterOption.builder().name("HTML").value("html").build(),
								ParameterOption.builder().name("Markdown").value("markdown").build()
						)).build(),
				NodeParameter.builder()
						.name("content").displayName("Content")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("publishStatus").displayName("Publish Status")
						.type(ParameterType.OPTIONS).defaultValue("draft")
						.options(List.of(
								ParameterOption.builder().name("Draft").value("draft").build(),
								ParameterOption.builder().name("Public").value("public").build(),
								ParameterOption.builder().name("Unlisted").value("unlisted").build()
						)).build(),
				NodeParameter.builder()
						.name("tags").displayName("Tags")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated tags (max 5).").build(),
				NodeParameter.builder()
						.name("canonicalUrl").displayName("Canonical URL")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("publicationId").displayName("Publication ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Publish to a specific publication (leave empty for user profile).").build()
		);
	}
}
