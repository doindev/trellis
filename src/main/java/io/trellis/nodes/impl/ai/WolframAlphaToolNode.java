package io.trellis.nodes.impl.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractAiToolNode;
import io.trellis.nodes.core.NodeExecutionContext;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Wolfram|Alpha Tool — connects to WolframAlpha's computational intelligence
 * engine to answer factual, mathematical, and scientific queries.
 */
@Node(
		type = "toolWolframAlpha",
		displayName = "Wolfram|Alpha",
		description = "Computational intelligence engine for factual and mathematical queries",
		category = "AI / Tools",
		icon = "calculator",
		credentials = {"wolframAlphaApi"}
)
public class WolframAlphaToolNode extends AbstractAiToolNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		String appId = context.getCredentialString("appId");
		return new WolframAlphaTool(appId);
	}

	public static class WolframAlphaTool {
		private static final ObjectMapper MAPPER = new ObjectMapper();
		private static final String API_URL = "https://api.wolframalpha.com/v2/query";
		private final String appId;

		public WolframAlphaTool(String appId) {
			this.appId = appId;
		}

		@Tool("Query Wolfram|Alpha for factual, mathematical, scientific, or computational answers. Use for calculations, unit conversions, data lookups, and knowledge questions.")
		public String query(String input) {
			try {
				String encoded = URLEncoder.encode(input, StandardCharsets.UTF_8);
				String url = API_URL + "?input=" + encoded
						+ "&appid=" + appId
						+ "&output=json"
						+ "&format=plaintext";

				HttpClient client = HttpClient.newHttpClient();
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(url))
						.GET()
						.build();

				HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
				JsonNode root = MAPPER.readTree(response.body());
				JsonNode queryResult = root.get("queryresult");

				if (queryResult == null || !queryResult.path("success").asBoolean(false)) {
					return "Wolfram|Alpha could not understand the query.";
				}

				JsonNode pods = queryResult.get("pods");
				if (pods == null || !pods.isArray()) {
					return "No results found.";
				}

				StringBuilder sb = new StringBuilder();
				for (JsonNode pod : pods) {
					String title = pod.path("title").asText();
					JsonNode subpods = pod.get("subpods");
					if (subpods != null && subpods.isArray()) {
						for (JsonNode subpod : subpods) {
							String plaintext = subpod.path("plaintext").asText("");
							if (!plaintext.isBlank()) {
								sb.append(title).append(": ").append(plaintext).append("\n");
							}
						}
					}
				}
				return sb.toString().isBlank() ? "No plaintext results available." : sb.toString();
			} catch (Exception e) {
				return "Wolfram|Alpha query failed: " + e.getMessage();
			}
		}
	}
}
