package io.cwc.nodes.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Bitly — shorten, retrieve, and update links via the Bitly API.
 */
@Node(
		type = "bitly",
		displayName = "Bitly",
		description = "Shorten and manage links via Bitly",
		category = "Miscellaneous",
		icon = "link",
		credentials = {"bitlyApi"},
		searchOnly = true
)
public class BitlyNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api-ssl.bitly.com/v4";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String accessToken = context.getCredentialString("accessToken");
			String operation = context.getParameter("operation", "create");

			Map<String, String> headers = Map.of(
					"Authorization", "Bearer " + accessToken,
					"Content-Type", "application/json");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					Map<String, Object> result;
					switch (operation) {
						case "create" -> {
							String longUrl = context.getParameter("longUrl", "");
							Map<String, Object> body = new HashMap<>();
							body.put("long_url", longUrl);
							String domain = context.getParameter("domain", "");
							if (!domain.isBlank()) body.put("domain", domain);
							var response = post(BASE_URL + "/shorten", body, headers);
							result = parseResponse(response);
						}
						case "get" -> {
							String bitlink = context.getParameter("bitlink", "");
							var response = get(BASE_URL + "/bitlinks/" + encode(bitlink), headers);
							result = parseResponse(response);
						}
						case "update" -> {
							String bitlink = context.getParameter("bitlink", "");
							String title = context.getParameter("title", "");
							Map<String, Object> body = new HashMap<>();
							if (!title.isBlank()) body.put("title", title);
							var response = patch(BASE_URL + "/bitlinks/" + encode(bitlink), body, headers);
							result = parseResponse(response);
						}
						default -> result = Map.of("error", "Unknown operation: " + operation);
					}
					results.add(wrapInJson(result));
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
						.type(ParameterType.OPTIONS).defaultValue("create")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("longUrl").displayName("Long URL")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("URL to shorten.").build(),
				NodeParameter.builder()
						.name("domain").displayName("Domain")
						.type(ParameterType.STRING).defaultValue("")
						.description("Custom domain for the short link.").build(),
				NodeParameter.builder()
						.name("bitlink").displayName("Bitlink")
						.type(ParameterType.STRING).defaultValue("")
						.description("Bitlink ID (e.g. bit.ly/xxxxx).").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("").build()
		);
	}
}
