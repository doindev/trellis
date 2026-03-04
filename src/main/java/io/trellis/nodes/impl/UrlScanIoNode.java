package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * urlscan.io — scan and analyze URLs for threats using the urlscan.io API.
 */
@Node(
		type = "urlScanIo",
		displayName = "urlscan.io",
		description = "Scan and analyze URLs for threats",
		category = "Miscellaneous",
		icon = "urlScanIo",
		credentials = {"urlScanIoApi"},
		searchOnly = true
)
public class UrlScanIoNode extends AbstractApiNode {

	private static final String BASE_URL = "https://urlscan.io/api/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = (String) credentials.getOrDefault("apiKey", "");

		String operation = context.getParameter("operation", "get");

		Map<String, String> headers = new HashMap<>();
		headers.put("API-Key", apiKey);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "get" -> {
						String scanId = context.getParameter("scanId", "");
						HttpResponse<String> response = get(BASE_URL + "/result/" + encode(scanId), headers);
						yield parseResponse(response);
					}
					case "getAll" -> {
						String query = context.getParameter("query", "");
						int limit = toInt(context.getParameters().get("limit"), 100);
						String url = BASE_URL + "/search/?q=" + encode(query) + "&size=" + limit;
						HttpResponse<String> response = get(url, headers);
						yield parseResponse(response);
					}
					case "perform" -> {
						String scanUrl = context.getParameter("url", "");
						Map<String, Object> body = new LinkedHashMap<>();
						body.put("url", scanUrl);
						String visibility = context.getParameter("visibility", "public");
						body.put("visibility", visibility);
						String tags = context.getParameter("tags", "");
						if (!tags.isEmpty()) {
							body.put("tags", Arrays.stream(tags.split(",")).map(String::trim).toList());
						}
						String customAgent = context.getParameter("customAgent", "");
						if (!customAgent.isEmpty()) body.put("customagent", customAgent);
						String referer = context.getParameter("referer", "");
						if (!referer.isEmpty()) body.put("referer", referer);
						HttpResponse<String> response = post(BASE_URL + "/scan/", body, headers);
						yield parseResponse(response);
					}
					default -> throw new IllegalArgumentException("Unknown operation: " + operation);
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

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("get")
						.options(List.of(
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Perform Scan").value("perform").build()
						)).build(),
				NodeParameter.builder()
						.name("scanId").displayName("Scan ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("url").displayName("URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("URL to scan.").build(),
				NodeParameter.builder()
						.name("query").displayName("Query")
						.type(ParameterType.STRING).defaultValue("")
						.description("Search query for scan results.").build(),
				NodeParameter.builder()
						.name("visibility").displayName("Visibility")
						.type(ParameterType.OPTIONS).defaultValue("public")
						.options(List.of(
								ParameterOption.builder().name("Public").value("public").build(),
								ParameterOption.builder().name("Private").value("private").build(),
								ParameterOption.builder().name("Unlisted").value("unlisted").build()
						)).build(),
				NodeParameter.builder()
						.name("tags").displayName("Tags")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated tags (max 10).").build(),
				NodeParameter.builder()
						.name("customAgent").displayName("Custom User Agent")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("referer").displayName("Referer")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max results to return.").build()
		);
	}
}
