package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Perplexity — send prompts to Perplexity AI for web-informed responses.
 */
@Node(
		type = "perplexity",
		displayName = "Perplexity",
		description = "Generate web-informed AI responses using Perplexity",
		category = "Standalone AI Services",
		icon = "perplexity",
		credentials = {"perplexityApi"}
)
public class PerplexityNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.perplexity.ai";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = (String) credentials.getOrDefault("apiKey", "");

		String model = context.getParameter("model", "sonar");
		String prompt = context.getParameter("prompt", "");
		double temperature = toDouble(context.getParameters().get("temperature"), 0.7);
		int maxTokens = toInt(context.getParameters().get("maxTokens"), 0);

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + apiKey);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("model", model);
				body.put("messages", List.of(
						Map.of("role", "user", "content", prompt)
				));
				body.put("temperature", temperature);
				if (maxTokens > 0) {
					body.put("max_tokens", maxTokens);
				}

				HttpResponse<String> response = post(BASE_URL + "/chat/completions", body, headers);
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
						.name("model").displayName("Model")
						.type(ParameterType.OPTIONS).defaultValue("sonar")
						.options(List.of(
								ParameterOption.builder().name("Sonar").value("sonar").build(),
								ParameterOption.builder().name("Sonar Pro").value("sonar-pro").build(),
								ParameterOption.builder().name("Sonar Reasoning").value("sonar-reasoning").build(),
								ParameterOption.builder().name("Sonar Reasoning Pro").value("sonar-reasoning-pro").build()
						)).build(),
				NodeParameter.builder()
						.name("prompt").displayName("Prompt")
						.type(ParameterType.STRING).defaultValue("")
						.description("The prompt to send.").build(),
				NodeParameter.builder()
						.name("temperature").displayName("Temperature")
						.type(ParameterType.NUMBER).defaultValue(0.7)
						.description("Sampling temperature (0–2).").build(),
				NodeParameter.builder()
						.name("maxTokens").displayName("Max Tokens")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Maximum tokens to generate. 0 for model default.").build()
		);
	}
}
