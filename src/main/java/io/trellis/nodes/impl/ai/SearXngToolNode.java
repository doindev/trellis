package io.trellis.nodes.impl.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractAiToolNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * SearXNG Search Tool — searches the web using a self-hosted SearXNG
 * metasearch engine instance.
 */
@Node(
		type = "toolSearXng",
		displayName = "SearXNG",
		description = "Search the web using SearXNG metasearch engine",
		category = "AI / Tools",
		icon = "search",
		credentials = {"searXngApi"}
)
public class SearXngToolNode extends AbstractAiToolNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		String apiUrl = context.getCredentialString("url", "http://localhost:8080");
		int numResults = toInt(context.getParameters().get("numResults"), 10);
		String language = context.getParameter("language", "en");

		return new SearXngTool(apiUrl, numResults, language);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("numResults").displayName("Number of Results")
						.type(ParameterType.NUMBER)
						.defaultValue(10)
						.description("Maximum number of results to return.").build(),
				NodeParameter.builder()
						.name("language").displayName("Language")
						.type(ParameterType.STRING)
						.defaultValue("en")
						.description("Two-letter language code (e.g. en, es, fr).").build()
		);
	}

	public static class SearXngTool {
		private static final ObjectMapper MAPPER = new ObjectMapper();
		private final String apiUrl;
		private final int numResults;
		private final String language;

		public SearXngTool(String apiUrl, int numResults, String language) {
			this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
			this.numResults = numResults;
			this.language = language;
		}

		@Tool("Search the web using SearXNG metasearch engine. Returns relevant search results.")
		public String search(String query) {
			try {
				String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
				String url = apiUrl + "/search?q=" + encoded + "&format=json&language=" + language;

				HttpClient client = HttpClient.newHttpClient();
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(url))
						.GET()
						.build();

				HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
				JsonNode root = MAPPER.readTree(response.body());
				JsonNode results = root.get("results");

				if (results == null || !results.isArray()) {
					return "No results found.";
				}

				StringBuilder sb = new StringBuilder();
				int count = 0;
				for (JsonNode result : results) {
					if (count >= numResults) break;
					sb.append("Title: ").append(result.path("title").asText()).append("\n");
					sb.append("URL: ").append(result.path("url").asText()).append("\n");
					sb.append("Content: ").append(result.path("content").asText()).append("\n\n");
					count++;
				}
				return sb.toString();
			} catch (Exception e) {
				return "SearXNG search failed: " + e.getMessage();
			}
		}
	}
}
