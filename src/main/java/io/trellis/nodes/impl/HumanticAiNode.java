package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Humantic AI — analyze personality profiles using the Humantic AI API.
 */
@Node(
		type = "humanticAi",
		displayName = "Humantic AI",
		description = "Analyze personality profiles using Humantic AI",
		category = "Standalone AI Services",
		icon = "humanticAi",
		credentials = {"humanticAiApi"}
)
public class HumanticAiNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.humantic.ai/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = (String) credentials.getOrDefault("apiKey", "");

		String operation = context.getParameter("operation", "getProfile");

		Map<String, String> headers = Map.of("Accept", "application/json", "Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "getProfile" -> {
						String userId = context.getParameter("userId", "");
						String url = BASE_URL + "/user-profile?apikey=" + encode(apiKey) + "&id=" + encode(userId);
						HttpResponse<String> response = get(url, headers);
						yield parseResponse(response);
					}
					case "createProfile" -> {
						String userId = context.getParameter("userId", "");
						Map<String, Object> body = new LinkedHashMap<>();
						body.put("id", userId);
						String text = context.getParameter("text", "");
						if (!text.isEmpty()) body.put("text", text);
						String linkedinUrl = context.getParameter("linkedinUrl", "");
						if (!linkedinUrl.isEmpty()) body.put("linkedin_url", linkedinUrl);
						String url = BASE_URL + "/user-profile/create?apikey=" + encode(apiKey);
						HttpResponse<String> response = post(url, body, headers);
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
						.type(ParameterType.OPTIONS).defaultValue("getProfile")
						.options(List.of(
								ParameterOption.builder().name("Get Profile").value("getProfile").build(),
								ParameterOption.builder().name("Create Profile").value("createProfile").build()
						)).build(),
				NodeParameter.builder()
						.name("userId").displayName("User ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("User identifier (email or LinkedIn URL).").required(true).build(),
				NodeParameter.builder()
						.name("text").displayName("Text")
						.type(ParameterType.STRING).defaultValue("")
						.description("Text content for profile analysis.").build(),
				NodeParameter.builder()
						.name("linkedinUrl").displayName("LinkedIn URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("LinkedIn profile URL for analysis.").build()
		);
	}
}
