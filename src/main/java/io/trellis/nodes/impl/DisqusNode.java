package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Disqus — retrieve forum data from the Disqus API.
 */
@Node(
		type = "disqus",
		displayName = "Disqus",
		description = "Get forum data from Disqus",
		category = "Social Media",
		icon = "disqus",
		credentials = {"disqusApi"}
)
public class DisqusNode extends AbstractApiNode {

	private static final String BASE_URL = "https://disqus.com/api/3.0";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");

		String operation = context.getParameter("operation", "get");
		String forum = context.getParameter("forum", "");
		int limit = toInt(context.getParameters().get("limit"), 100);

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "get" -> {
						String url = BASE_URL + "/forums/details.json?forum=" + encode(forum)
								+ "&api_key=" + encode(accessToken);
						HttpResponse<String> response = get(url, Map.of("Accept", "application/json"));
						yield parseResponse(response);
					}
					case "getPosts" -> {
						String url = BASE_URL + "/forums/listPosts.json?forum=" + encode(forum)
								+ "&limit=" + limit + "&api_key=" + encode(accessToken);
						HttpResponse<String> response = get(url, Map.of("Accept", "application/json"));
						yield parseResponse(response);
					}
					case "getCategories" -> {
						String url = BASE_URL + "/forums/listCategories.json?forum=" + encode(forum)
								+ "&limit=" + limit + "&api_key=" + encode(accessToken);
						HttpResponse<String> response = get(url, Map.of("Accept", "application/json"));
						yield parseResponse(response);
					}
					case "getThreads" -> {
						String url = BASE_URL + "/forums/listThreads.json?forum=" + encode(forum)
								+ "&limit=" + limit + "&api_key=" + encode(accessToken);
						HttpResponse<String> response = get(url, Map.of("Accept", "application/json"));
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
						.type(ParameterType.OPTIONS).defaultValue("get")
						.options(List.of(
								ParameterOption.builder().name("Get Forum").value("get").build(),
								ParameterOption.builder().name("Get All Posts").value("getPosts").build(),
								ParameterOption.builder().name("Get All Categories").value("getCategories").build(),
								ParameterOption.builder().name("Get All Threads").value("getThreads").build()
						)).build(),
				NodeParameter.builder()
						.name("forum").displayName("Forum Short Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The forum's unique short name.").required(true).build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max results to return (1-100).").build()
		);
	}
}
