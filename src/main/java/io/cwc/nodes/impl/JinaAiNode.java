package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Jina AI — read web content, search, and perform deep research using Jina AI.
 */
@Node(
		type = "jinaAi",
		displayName = "Jina AI",
		description = "Read web content, search, and research with Jina AI",
		category = "AI",
		icon = "jinaAi",
		credentials = {"jinaAiApi"},
		searchOnly = true
)
public class JinaAiNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");

		String resource = context.getParameter("resource", "reader");
		String operation = context.getParameter("operation", "read");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + apiKey);
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "reader" -> handleReader(context, headers, operation);
					case "research" -> handleResearch(context, headers, operation);
					default -> throw new IllegalArgumentException("Unknown resource: " + resource);
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

	private Map<String, Object> handleReader(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "read" -> {
				String url = context.getParameter("url", "");
				Map<String, String> reqHeaders = new HashMap<>(headers);
				String outputFormat = context.getParameter("outputFormat", "markdown");
				reqHeaders.put("X-Return-Format", outputFormat);
				String targetSelector = context.getParameter("targetSelector", "");
				if (!targetSelector.isEmpty()) reqHeaders.put("X-Target-Selector", targetSelector);
				String excludeSelector = context.getParameter("excludeSelector", "");
				if (!excludeSelector.isEmpty()) reqHeaders.put("X-Remove-Selector", excludeSelector);
				boolean enableImageCaptioning = toBoolean(context.getParameters().get("enableImageCaptioning"), false);
				if (enableImageCaptioning) reqHeaders.put("X-With-Generated-Alt", "true");
				String waitForSelector = context.getParameter("waitForSelector", "");
				if (!waitForSelector.isEmpty()) reqHeaders.put("X-Wait-For-Selector", waitForSelector);
				HttpResponse<String> response = get("https://r.jina.ai/" + url, reqHeaders);
				yield parseResponse(response);
			}
			case "search" -> {
				String searchQuery = context.getParameter("searchQuery", "");
				Map<String, String> reqHeaders = new HashMap<>(headers);
				String outputFormat = context.getParameter("outputFormat", "markdown");
				reqHeaders.put("X-Return-Format", outputFormat);
				String siteFilter = context.getParameter("siteFilter", "");
				if (!siteFilter.isEmpty()) reqHeaders.put("X-Site", siteFilter);
				StringBuilder searchUrl = new StringBuilder("https://s.jina.ai/?q=" + encode(searchQuery));
				int page = toInt(context.getParameters().get("pageNumber"), 0);
				if (page > 0) searchUrl.append("&page=").append(page);
				HttpResponse<String> response = get(searchUrl.toString(), reqHeaders);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown reader operation: " + operation);
		};
	}

	private Map<String, Object> handleResearch(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "deepResearch" -> {
				String query = context.getParameter("researchQuery", "");
				Map<String, String> reqHeaders = new HashMap<>(headers);
				reqHeaders.put("Content-Type", "application/json");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("messages", List.of(Map.of("role", "user", "content", query)));
				int maxSources = toInt(context.getParameters().get("maxReturnedSources"), 0);
				if (maxSources > 0) body.put("max_returned_urls", maxSources);
				String prioritizeSources = context.getParameter("prioritizeSources", "");
				if (!prioritizeSources.isEmpty()) body.put("boost_hostnames", List.of(prioritizeSources.split(",")));
				String excludeSources = context.getParameter("excludeSources", "");
				if (!excludeSources.isEmpty()) body.put("bad_hostnames", List.of(excludeSources.split(",")));
				HttpResponse<String> response = post("https://deepsearch.jina.ai/v1/chat/completions", body, reqHeaders);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown research operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("reader")
						.options(List.of(
								ParameterOption.builder().name("Reader").value("reader").build(),
								ParameterOption.builder().name("Research").value("research").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("read")
						.options(List.of(
								ParameterOption.builder().name("Read").value("read").build(),
								ParameterOption.builder().name("Search").value("search").build(),
								ParameterOption.builder().name("Deep Research").value("deepResearch").build()
						)).build(),
				NodeParameter.builder()
						.name("url").displayName("URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("URL to read content from.").build(),
				NodeParameter.builder()
						.name("searchQuery").displayName("Search Query")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("researchQuery").displayName("Research Query")
						.type(ParameterType.STRING).defaultValue("")
						.description("Topic or question for deep research.").build(),
				NodeParameter.builder()
						.name("outputFormat").displayName("Output Format")
						.type(ParameterType.OPTIONS).defaultValue("markdown")
						.options(List.of(
								ParameterOption.builder().name("HTML").value("html").build(),
								ParameterOption.builder().name("Markdown").value("markdown").build(),
								ParameterOption.builder().name("Screenshot").value("screenshot").build(),
								ParameterOption.builder().name("Text").value("text").build()
						)).build(),
				NodeParameter.builder()
						.name("targetSelector").displayName("Target Selector")
						.type(ParameterType.STRING).defaultValue("")
						.description("CSS selector for content to extract.").build(),
				NodeParameter.builder()
						.name("excludeSelector").displayName("Exclude Selector")
						.type(ParameterType.STRING).defaultValue("")
						.description("CSS selector for content to exclude.").build(),
				NodeParameter.builder()
						.name("enableImageCaptioning").displayName("Enable Image Captioning")
						.type(ParameterType.BOOLEAN).defaultValue(false).build(),
				NodeParameter.builder()
						.name("waitForSelector").displayName("Wait For Selector")
						.type(ParameterType.STRING).defaultValue("")
						.description("CSS selector to wait for before reading.").build(),
				NodeParameter.builder()
						.name("siteFilter").displayName("Site Filter")
						.type(ParameterType.STRING).defaultValue("")
						.description("Limit search to a specific site.").build(),
				NodeParameter.builder()
						.name("pageNumber").displayName("Page Number")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Search result page number.").build(),
				NodeParameter.builder()
						.name("maxReturnedSources").displayName("Max Returned Sources")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Max URLs to return in deep research results.").build(),
				NodeParameter.builder()
						.name("prioritizeSources").displayName("Prioritize Sources")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated list of domains to prioritize.").build(),
				NodeParameter.builder()
						.name("excludeSources").displayName("Exclude Sources")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated list of domains to exclude.").build()
		);
	}
}
