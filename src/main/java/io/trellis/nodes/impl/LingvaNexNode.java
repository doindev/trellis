package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LingvaNex — translate text via the LingvaNex Translation API.
 */
@Node(
		type = "lingvaNex",
		displayName = "LingvaNex",
		description = "Translate text via LingvaNex",
		category = "AI",
		icon = "language",
		credentials = {"lingvaNexApi"}
)
public class LingvaNexNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api-b2b.backenster.com/b1/api/v3";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String apiKey = context.getCredentialString("apiKey");
			String text = context.getParameter("text", "");
			String from = context.getParameter("from", "en");
			String to = context.getParameter("to", "de");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					var response = post(BASE_URL + "/translate",
							Map.of("data", text, "from", from, "to", to, "platform", "api"),
							Map.of("Authorization", "Bearer " + apiKey, "Content-Type", "application/json"));
					results.add(wrapInJson(parseResponse(response)));
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
						.name("text").displayName("Text")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("Text to translate.").build(),
				NodeParameter.builder()
						.name("from").displayName("From Language")
						.type(ParameterType.STRING).defaultValue("en")
						.required(true).description("Source language code.").build(),
				NodeParameter.builder()
						.name("to").displayName("To Language")
						.type(ParameterType.STRING).defaultValue("de")
						.required(true).description("Target language code.").build()
		);
	}
}
