package io.cwc.nodes.impl.ai;

import dev.langchain4j.agent.tool.Tool;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractAiToolNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Node(
		type = "wikipediaTool",
		displayName = "Wikipedia",
		description = "Tool for searching and retrieving Wikipedia articles",
		category = "AI / Tools",
		icon = "book-open",
		searchOnly = true
)
public class WikipediaToolNode extends AbstractAiToolNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		String language = context.getParameter("language", "en");
		int maxResults = toInt(context.getParameters().get("maxResults"), 3);
		return new WikipediaTool(language, maxResults);
	}

	public static class WikipediaTool {
		private final String language;
		private final int maxResults;

		public WikipediaTool(String language, int maxResults) {
			this.language = language;
			this.maxResults = maxResults;
		}

		@Tool("Search Wikipedia for information about a topic. Returns article summaries.")
		public String searchWikipedia(String query) {
			try {
				String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
				String url = String.format(
						"https://%s.wikipedia.org/api/rest_v1/page/summary/%s",
						language, encoded);

				HttpClient client = HttpClient.newHttpClient();
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(url))
						.header("User-Agent", "CWCWorkflowEngine/1.0")
						.GET()
						.build();

				HttpResponse<String> response = client.send(request,
						HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() == 200) {
					return response.body();
				}

				// Fallback: use search API
				String searchUrl = String.format(
						"https://%s.wikipedia.org/w/api.php?action=query&list=search&srsearch=%s&srlimit=%d&format=json",
						language, encoded, maxResults);

				HttpResponse<String> searchResponse = client.send(
						HttpRequest.newBuilder().uri(URI.create(searchUrl))
								.header("User-Agent", "CWCWorkflowEngine/1.0")
								.GET().build(),
						HttpResponse.BodyHandlers.ofString());
				return searchResponse.body();
			} catch (Exception e) {
				return "Wikipedia search failed: " + e.getMessage();
			}
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("language").displayName("Language")
						.type(ParameterType.STRING)
						.defaultValue("en")
						.description("Wikipedia language code (e.g. en, de, fr)")
						.build(),
				NodeParameter.builder()
						.name("maxResults").displayName("Max Results")
						.type(ParameterType.NUMBER)
						.defaultValue(3)
						.build()
		);
	}
}
