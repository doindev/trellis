package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RSS Read — reads and parses an RSS/Atom feed from a URL.
 * No authentication required.
 */
@Node(
		type = "rssFeedRead",
		displayName = "RSS Read",
		description = "Read and parse RSS/Atom feeds",
		category = "Miscellaneous",
		icon = "rss"
)
public class RssFeedReadNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String feedUrl = context.getParameter("url", "");

			List<Map<String, Object>> results = new ArrayList<>();

			var response = get(feedUrl, Map.of("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml"));
			String body = response.body();

			if (body == null || body.isBlank()) {
				return NodeExecutionResult.success(List.of(wrapInJson(Map.of("error", "Empty response"))));
			}

			// Simple XML item extraction
			Pattern itemPattern = Pattern.compile("<item>(.*?)</item>|<entry>(.*?)</entry>",
					Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
			Matcher itemMatcher = itemPattern.matcher(body);

			while (itemMatcher.find()) {
				String itemXml = itemMatcher.group(1) != null ? itemMatcher.group(1) : itemMatcher.group(2);
				Map<String, Object> feedItem = new LinkedHashMap<>();
				feedItem.put("title", extractTag(itemXml, "title"));
				feedItem.put("link", extractTag(itemXml, "link"));
				feedItem.put("description", extractTag(itemXml, "description", "summary", "content"));
				feedItem.put("pubDate", extractTag(itemXml, "pubDate", "published", "updated"));
				feedItem.put("creator", extractTag(itemXml, "dc:creator", "author"));
				feedItem.put("guid", extractTag(itemXml, "guid", "id"));
				results.add(wrapInJson(feedItem));
			}

			if (results.isEmpty()) {
				results.add(wrapInJson(Map.of("raw", body)));
			}

			return NodeExecutionResult.success(results);
		} catch (Exception e) {
			return handleError(context, e.getMessage(), e);
		}
	}

	private String extractTag(String xml, String... tagNames) {
		for (String tag : tagNames) {
			Pattern p = Pattern.compile("<" + tag + "[^>]*>(.*?)</" + tag + ">",
					Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
			Matcher m = p.matcher(xml);
			if (m.find()) {
				String val = m.group(1).trim();
				// Strip CDATA
				if (val.startsWith("<![CDATA[")) {
					val = val.substring(9);
					if (val.endsWith("]]>")) val = val.substring(0, val.length() - 3);
				}
				return val;
			}
			// Check for self-closing link with href
			if ("link".equals(tag)) {
				Pattern linkP = Pattern.compile("<link[^>]+href=[\"']([^\"']+)[\"']",
						Pattern.CASE_INSENSITIVE);
				Matcher linkM = linkP.matcher(xml);
				if (linkM.find()) return linkM.group(1);
			}
		}
		return "";
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("url").displayName("URL")
						.type(ParameterType.STRING).defaultValue("")
						.required(true)
						.description("URL of the RSS or Atom feed.").build()
		);
	}
}
