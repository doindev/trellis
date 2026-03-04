package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Yourls — shorten and manage URLs via a self-hosted YOURLS instance.
 * Authentication via signature token.
 */
@Node(
		type = "yourls",
		displayName = "Yourls",
		description = "Shorten and manage URLs via YOURLS",
		category = "Miscellaneous",
		icon = "link",
		credentials = {"yourlsApi"},
		searchOnly = true
)
public class YourlsNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String baseUrl = context.getCredentialString("url", "");
			String signature = context.getCredentialString("signature", "");
			String operation = context.getParameter("operation", "shorten");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					String apiUrl = baseUrl + "/yourls-api.php?signature=" + encode(signature)
							+ "&format=json&action=" + encode(operation);

					switch (operation) {
						case "shorten" -> {
							String url = context.getParameter("url", "");
							apiUrl += "&url=" + encode(url);
							String keyword = context.getParameter("keyword", "");
							if (!keyword.isBlank()) apiUrl += "&keyword=" + encode(keyword);
							String title = context.getParameter("title", "");
							if (!title.isBlank()) apiUrl += "&title=" + encode(title);
						}
						case "expand" -> {
							String shortUrl = context.getParameter("shortUrl", "");
							apiUrl += "&shorturl=" + encode(shortUrl);
						}
						case "stats" -> {
							String shortUrl = context.getParameter("shortUrl", "");
							apiUrl += "&shorturl=" + encode(shortUrl);
						}
					}

					var response = get(apiUrl, Map.of());
					results.add(wrapInJson(parseResponse(response)));
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
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("shorten")
						.options(List.of(
								ParameterOption.builder().name("Shorten").value("shorten").build(),
								ParameterOption.builder().name("Expand").value("expand").build(),
								ParameterOption.builder().name("Stats").value("stats").build()
						)).build(),
				NodeParameter.builder()
						.name("url").displayName("URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("URL to shorten.").build(),
				NodeParameter.builder()
						.name("keyword").displayName("Keyword")
						.type(ParameterType.STRING).defaultValue("")
						.description("Custom short URL keyword.").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("shortUrl").displayName("Short URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("Short URL to expand or get stats for.").build()
		);
	}
}
