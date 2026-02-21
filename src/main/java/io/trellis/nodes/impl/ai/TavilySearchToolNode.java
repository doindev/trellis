package io.trellis.nodes.impl.ai;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import dev.langchain4j.agent.tool.Tool;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractAiToolNode;
import io.trellis.nodes.core.NodeExecutionContext;

@Node(
		type = "tavilySearchTool",
		displayName = "Tavily Web Search",
		description = "Tool for searching the web using Tavily API",
		category = "AI / Tools",
		icon = "search",
		credentials = {"tavilyApi"}
)
public class TavilySearchToolNode extends AbstractAiToolNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		WebSearchEngine searchEngine = TavilyWebSearchEngine.builder()
				.apiKey(apiKey)
				.build();
		return new TavilySearchTool(searchEngine);
	}

	public static class TavilySearchTool {
		private final WebSearchEngine searchEngine;

		public TavilySearchTool(WebSearchEngine searchEngine) {
			this.searchEngine = searchEngine;
		}

		@Tool("Search the web for current information about a topic. Returns relevant web search results with snippets.")
		public String searchWeb(String query) {
			try {
				WebSearchResults results = searchEngine.search(query);
				StringBuilder sb = new StringBuilder();
				results.results().forEach(result -> {
					sb.append("Title: ").append(result.title()).append("\n");
					sb.append("URL: ").append(result.url()).append("\n");
					sb.append("Snippet: ").append(result.snippet()).append("\n\n");
				});
				return sb.toString();
			} catch (Exception e) {
				return "Web search failed: " + e.getMessage();
			}
		}
	}
}
