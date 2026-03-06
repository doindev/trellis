package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Google Perspective — analyze text for toxicity and other attributes using the Perspective API.
 */
@Node(
		type = "googlePerspective",
		displayName = "Google Perspective",
		description = "Analyze text for toxicity using the Google Perspective API",
		category = "Google",
		icon = "googlePerspective",
		credentials = {"googlePerspectiveApi"}
)
public class GooglePerspectiveNode extends AbstractApiNode {

	private static final String BASE_URL = "https://commentanalyzer.googleapis.com";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				String text = context.getParameter("text", "");
				String attributes = context.getParameter("attributes", "TOXICITY");
				String language = context.getParameter("language", "");
				double scoreThreshold = toDouble(context.getParameters().get("scoreThreshold"), 0);

				// Build requested attributes
				Map<String, Object> requestedAttributes = new LinkedHashMap<>();
				for (String attr : attributes.split(",")) {
					String trimmed = attr.trim();
					if (!trimmed.isEmpty()) {
						Map<String, Object> attrConfig = new LinkedHashMap<>();
						if (scoreThreshold > 0) {
							attrConfig.put("scoreThreshold", scoreThreshold);
						}
						requestedAttributes.put(trimmed, attrConfig);
					}
				}

				Map<String, Object> comment = Map.of("text", text);

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("comment", comment);
				body.put("requestedAttributes", requestedAttributes);
				if (!language.isEmpty()) {
					body.put("languages", List.of(language));
				}

				HttpResponse<String> response = post(BASE_URL + "/v1alpha1/comments:analyze", body, headers);
				Map<String, Object> result = parseResponse(response);
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
						.type(ParameterType.OPTIONS).defaultValue("analyzeComment")
						.options(List.of(
								ParameterOption.builder().name("Analyze Comment").value("analyzeComment").build()
						)).build(),
				NodeParameter.builder()
						.name("text").displayName("Text")
						.type(ParameterType.STRING).defaultValue("")
						.description("Text content to analyze.").build(),
				NodeParameter.builder()
						.name("attributes").displayName("Attributes")
						.type(ParameterType.STRING).defaultValue("TOXICITY")
						.description("Comma-separated attributes: TOXICITY, SEVERE_TOXICITY, IDENTITY_ATTACK, INSULT, PROFANITY, THREAT, SEXUALLY_EXPLICIT, FLIRTATION.").build(),
				NodeParameter.builder()
						.name("scoreThreshold").displayName("Score Threshold")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Minimum score threshold (0-1). At 0, all scores are returned.").build(),
				NodeParameter.builder()
						.name("language").displayName("Language")
						.type(ParameterType.OPTIONS).defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("Auto-detect").value("").build(),
								ParameterOption.builder().name("English").value("en").build(),
								ParameterOption.builder().name("Spanish").value("es").build(),
								ParameterOption.builder().name("French").value("fr").build(),
								ParameterOption.builder().name("German").value("de").build(),
								ParameterOption.builder().name("Portuguese").value("pt").build(),
								ParameterOption.builder().name("Italian").value("it").build(),
								ParameterOption.builder().name("Russian").value("ru").build()
						)).build()
		);
	}
}
