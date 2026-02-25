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
 * Google Translate — translate text via the Google Cloud Translation API v2.
 */
@Node(
		type = "googleTranslate",
		displayName = "Google Translate",
		description = "Translate text via Google Cloud Translation API",
		category = "Google",
		icon = "google",
		credentials = {"googleTranslateApi"}
)
public class GoogleTranslateNode extends AbstractApiNode {

	private static final String BASE_URL = "https://translation.googleapis.com/language/translate/v2";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String apiKey = context.getCredentialString("apiKey");
			String text = context.getParameter("text", "");
			String targetLanguage = context.getParameter("translateTo", "en");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					Map<String, Object> body = Map.of(
							"q", text,
							"target", targetLanguage);

					var response = post(BASE_URL + "?key=" + encode(apiKey), body,
							Map.of("Content-Type", "application/json"));
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
						.name("translateTo").displayName("Translate To")
						.type(ParameterType.STRING).defaultValue("en")
						.required(true).description("Target language code (e.g. 'en', 'de', 'fr', 'es').").build()
		);
	}
}
