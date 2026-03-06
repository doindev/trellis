package io.cwc.nodes.impl.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractAiToolNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * SerpApi (Google Search) Tool — searches Google using the SerpApi service,
 * providing structured search results for AI agent use.
 */
@Node(
		type = "toolSerpApi",
		displayName = "SerpApi (Google Search)",
		description = "Search Google using SerpAPI",
		category = "AI / Tools",
		icon = "search",
		credentials = {"serpApi"},
		searchOnly = true
)
public class SerpApiToolNode extends AbstractAiToolNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		String country = context.getParameter("country", "us");
		String language = context.getParameter("language", "en");
		String device = context.getParameter("device", "desktop");
		String googleDomain = context.getParameter("googleDomain", "google.com");

		return new SerpApiTool(apiKey, country, language, device, googleDomain);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("country").displayName("Country")
						.type(ParameterType.STRING)
						.defaultValue("us")
						.description("Two-letter country code for Google search locale.").build(),
				NodeParameter.builder()
						.name("language").displayName("Language")
						.type(ParameterType.STRING)
						.defaultValue("en")
						.description("Two-letter language code.").build(),
				NodeParameter.builder()
						.name("device").displayName("Device")
						.type(ParameterType.OPTIONS)
						.defaultValue("desktop")
						.options(List.of(
								ParameterOption.builder().name("Desktop").value("desktop").build(),
								ParameterOption.builder().name("Mobile").value("mobile").build(),
								ParameterOption.builder().name("Tablet").value("tablet").build()
						)).build(),
				NodeParameter.builder()
						.name("googleDomain").displayName("Google Domain")
						.type(ParameterType.STRING)
						.defaultValue("google.com")
						.description("Specific Google domain for search (e.g. google.co.uk).").build()
		);
	}

	public static class SerpApiTool {
		private static final ObjectMapper MAPPER = new ObjectMapper();
		private static final String BASE_URL = "https://serpapi.com/search.json";
		private final String apiKey;
		private final String country;
		private final String language;
		private final String device;
		private final String googleDomain;

		public SerpApiTool(String apiKey, String country, String language,
						   String device, String googleDomain) {
			this.apiKey = apiKey;
			this.country = country;
			this.language = language;
			this.device = device;
			this.googleDomain = googleDomain;
		}

		@Tool("Search Google for current information. Returns organic search results with titles, links, and snippets.")
		public String searchGoogle(String query) {
			try {
				String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
				String url = BASE_URL + "?q=" + encoded
						+ "&api_key=" + apiKey
						+ "&gl=" + country
						+ "&hl=" + language
						+ "&device=" + device
						+ "&google_domain=" + googleDomain
						+ "&engine=google";

				HttpClient client = HttpClient.newHttpClient();
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(url))
						.GET()
						.build();

				HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
				JsonNode root = MAPPER.readTree(response.body());
				JsonNode organicResults = root.get("organic_results");

				if (organicResults == null || !organicResults.isArray()) {
					JsonNode answerBox = root.get("answer_box");
					if (answerBox != null) {
						return "Answer: " + answerBox.path("answer").asText(
								answerBox.path("snippet").asText("No results found."));
					}
					return "No results found.";
				}

				StringBuilder sb = new StringBuilder();
				int count = 0;
				for (JsonNode result : organicResults) {
					if (count >= 5) break;
					sb.append("Title: ").append(result.path("title").asText()).append("\n");
					sb.append("Link: ").append(result.path("link").asText()).append("\n");
					sb.append("Snippet: ").append(result.path("snippet").asText()).append("\n\n");
					count++;
				}
				return sb.toString();
			} catch (Exception e) {
				return "SerpApi search failed: " + e.getMessage();
			}
		}
	}
}
