package io.trellis.nodes.impl;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractTriggerNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * RSS Feed Trigger Node -- polls an RSS/Atom feed URL for new items.
 * Parses the XML feed and outputs each item as a separate data item.
 */
@Slf4j
@Node(
	type = "rssFeedReadTrigger",
	displayName = "RSS Feed Trigger",
	description = "Polls an RSS or Atom feed URL for new items",
	category = "Miscellaneous",
	icon = "rss",
	trigger = true,
	polling = true
)
public class RssFeedReadTriggerNode extends AbstractTriggerNode {

	private final HttpClient httpClient = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(30))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
			.name("feedUrl").displayName("Feed URL").type(ParameterType.STRING).required(true)
			.placeHolder("https://example.com/feed.xml")
			.description("The URL of the RSS or Atom feed to poll.")
			.build());

		params.add(NodeParameter.builder()
			.name("maxItems").displayName("Max Items").type(ParameterType.NUMBER)
			.defaultValue(50)
			.description("Maximum number of feed items to return per poll.")
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String feedUrl = context.getParameter("feedUrl", "");
		int maxItems = 50;
		Object maxItemsParam = context.getParameter("maxItems", 50);
		if (maxItemsParam instanceof Number) {
			maxItems = ((Number) maxItemsParam).intValue();
		} else {
			try {
				maxItems = Integer.parseInt(String.valueOf(maxItemsParam));
			} catch (NumberFormatException e) {
				// keep default
			}
		}

		if (feedUrl.isBlank()) {
			return NodeExecutionResult.error("Feed URL is required");
		}

		try {
			// Fetch the feed
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(feedUrl))
				.GET()
				.timeout(Duration.ofSeconds(60))
				.header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
				.header("User-Agent", "Trellis/1.0")
				.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() >= 400) {
				String body = response.body() != null ? response.body() : "";
				if (body.length() > 300) body = body.substring(0, 300) + "...";
				return NodeExecutionResult.error("Failed to fetch RSS feed (HTTP " + response.statusCode() + "): " + body);
			}

			String xml = response.body();
			if (xml == null || xml.isBlank()) {
				return NodeExecutionResult.empty();
			}

			// Parse the XML
			List<Map<String, Object>> items = parseFeed(xml, maxItems);

			if (items.isEmpty()) {
				return NodeExecutionResult.empty();
			}

			List<Map<String, Object>> results = new ArrayList<>();
			for (Map<String, Object> item : items) {
				results.add(createTriggerItem(item));
			}
			return NodeExecutionResult.success(results);

		} catch (Exception e) {
			return handleError(context, "RSS Feed Trigger error: " + e.getMessage(), e);
		}
	}

	// ========================= Feed Parsing =========================

	private List<Map<String, Object>> parseFeed(String xml, int maxItems) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		// Disable external entities for security
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.parse(new InputSource(new StringReader(xml)));
		doc.getDocumentElement().normalize();

		String rootTag = doc.getDocumentElement().getTagName().toLowerCase();

		if (rootTag.contains("feed")) {
			// Atom feed
			return parseAtomFeed(doc, maxItems);
		} else {
			// RSS feed
			return parseRssFeed(doc, maxItems);
		}
	}

	private List<Map<String, Object>> parseRssFeed(Document doc, int maxItems) {
		List<Map<String, Object>> items = new ArrayList<>();
		NodeList itemNodes = doc.getElementsByTagName("item");

		int count = Math.min(itemNodes.getLength(), maxItems);
		for (int i = 0; i < count; i++) {
			Element item = (Element) itemNodes.item(i);
			Map<String, Object> data = new LinkedHashMap<>();
			data.put("title", getElementText(item, "title"));
			data.put("link", getElementText(item, "link"));
			data.put("description", getElementText(item, "description"));
			data.put("pubDate", getElementText(item, "pubDate"));
			data.put("guid", getElementText(item, "guid"));
			data.put("author", getElementText(item, "author"));
			data.put("category", getElementText(item, "category"));

			// Handle content:encoded
			String content = getElementText(item, "content:encoded");
			if (!content.isEmpty()) {
				data.put("content", content);
			}

			items.add(data);
		}
		return items;
	}

	private List<Map<String, Object>> parseAtomFeed(Document doc, int maxItems) {
		List<Map<String, Object>> items = new ArrayList<>();
		NodeList entryNodes = doc.getElementsByTagName("entry");

		int count = Math.min(entryNodes.getLength(), maxItems);
		for (int i = 0; i < count; i++) {
			Element entry = (Element) entryNodes.item(i);
			Map<String, Object> data = new LinkedHashMap<>();
			data.put("title", getElementText(entry, "title"));

			// Get link href attribute
			NodeList links = entry.getElementsByTagName("link");
			if (links.getLength() > 0) {
				Element link = (Element) links.item(0);
				String href = link.getAttribute("href");
				if (href != null && !href.isEmpty()) {
					data.put("link", href);
				} else {
					data.put("link", link.getTextContent());
				}
			} else {
				data.put("link", "");
			}

			data.put("description", getElementText(entry, "summary"));
			data.put("pubDate", getElementText(entry, "published"));
			data.put("updated", getElementText(entry, "updated"));
			data.put("guid", getElementText(entry, "id"));

			// Author
			NodeList authors = entry.getElementsByTagName("author");
			if (authors.getLength() > 0) {
				data.put("author", getElementText((Element) authors.item(0), "name"));
			} else {
				data.put("author", "");
			}

			String content = getElementText(entry, "content");
			if (!content.isEmpty()) {
				data.put("content", content);
			}

			items.add(data);
		}
		return items;
	}

	private String getElementText(Element parent, String tagName) {
		NodeList nodes = parent.getElementsByTagName(tagName);
		if (nodes.getLength() > 0) {
			String text = nodes.item(0).getTextContent();
			return text != null ? text.trim() : "";
		}
		return "";
	}
}
