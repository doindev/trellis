package io.cwc.nodes.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Hacker News — retrieve articles and users from the Hacker News API.
 * No authentication required (public API).
 */
@Node(
		type = "hackerNews",
		displayName = "Hacker News",
		description = "Retrieve articles and users from Hacker News",
		category = "Miscellaneous",
		icon = "newspaper",
		searchOnly = true
)
public class HackerNewsNode extends AbstractApiNode {

	private static final String BASE_URL = "https://hacker-news.firebaseio.com/v0";
	private static final String SEARCH_URL = "https://hn.algolia.com/api/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String resource = context.getParameter("resource", "article");
			String operation = context.getParameter("operation", "get");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					Map<String, Object> result;
					if ("article".equals(resource)) {
						if ("get".equals(operation)) {
							String articleId = context.getParameter("articleId", "");
							var response = get(BASE_URL + "/item/" + encode(articleId) + ".json", Map.of());
							result = parseResponse(response);
						} else {
							// getAll
							int limit = toInt(context.getParameters().get("limit"), 10);
							String keyword = context.getParameter("keyword", "");
							String tags = context.getParameter("tags", "story");
							String url = SEARCH_URL + "/search?tags=" + encode(tags)
									+ "&hitsPerPage=" + limit;
							if (!keyword.isBlank()) url += "&query=" + encode(keyword);
							var response = get(url, Map.of());
							result = parseResponse(response);
						}
					} else {
						// user
						String username = context.getParameter("username", "");
						var response = get(BASE_URL + "/user/" + encode(username) + ".json", Map.of());
						result = parseResponse(response);
					}
					results.add(wrapInJson(result));
				} catch (Exception e) {
					if (context.isContinueOnFail()) {
						results.add(wrapInJson(Map.of("error", e.getMessage())));
					} else {
						return handleError(context, e.getMessage(), e);
					}
				}
			}
			return NodeExecutionResult.success(results);
		} catch (Exception e) {
			return handleError(context, e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("article")
						.options(List.of(
								ParameterOption.builder().name("Article").value("article").build(),
								ParameterOption.builder().name("User").value("user").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("get")
						.options(List.of(
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get Many").value("getAll").build()
						)).build(),
				NodeParameter.builder()
						.name("articleId").displayName("Article ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Hacker News item ID.").build(),
				NodeParameter.builder()
						.name("username").displayName("Username")
						.type(ParameterType.STRING).defaultValue("")
						.description("Hacker News username.").build(),
				NodeParameter.builder()
						.name("keyword").displayName("Keyword")
						.type(ParameterType.STRING).defaultValue("")
						.description("Search keyword for articles.").build(),
				NodeParameter.builder()
						.name("tags").displayName("Tags")
						.type(ParameterType.OPTIONS).defaultValue("story")
						.options(List.of(
								ParameterOption.builder().name("Story").value("story").build(),
								ParameterOption.builder().name("Comment").value("comment").build(),
								ParameterOption.builder().name("Ask HN").value("ask_hn").build(),
								ParameterOption.builder().name("Show HN").value("show_hn").build(),
								ParameterOption.builder().name("Front Page").value("front_page").build()
						)).build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(10)
						.description("Max results to return.").build()
		);
	}
}
