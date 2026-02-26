package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * DeepL — translate text using the DeepL API.
 */
@Node(
		type = "deepL",
		displayName = "DeepL",
		description = "Translate text using the DeepL API",
		category = "Standalone AI Services",
		icon = "deepL",
		credentials = {"deepLApi"}
)
public class DeepLNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = (String) credentials.getOrDefault("apiKey", "");
		// DeepL free API uses a different base URL
		String baseUrl = apiKey.endsWith(":fx")
				? "https://api-free.deepl.com/v2"
				: "https://api.deepl.com/v2";

		String operation = context.getParameter("operation", "translate");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "DeepL-Auth-Key " + apiKey);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "translate" -> {
						String text = context.getParameter("text", "");
						String targetLanguage = context.getParameter("targetLanguage", "EN");
						String sourceLanguage = context.getParameter("sourceLanguage", "");

						Map<String, Object> body = new LinkedHashMap<>();
						body.put("text", List.of(text));
						body.put("target_lang", targetLanguage);
						if (!sourceLanguage.isEmpty()) {
							body.put("source_lang", sourceLanguage);
						}

						String formality = context.getParameter("formality", "");
						if (!formality.isEmpty()) body.put("formality", formality);

						HttpResponse<String> response = post(baseUrl + "/translate", body, headers);
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
						.type(ParameterType.OPTIONS).defaultValue("translate")
						.options(List.of(
								ParameterOption.builder().name("Translate").value("translate").build()
						)).build(),
				NodeParameter.builder()
						.name("text").displayName("Text")
						.type(ParameterType.STRING).defaultValue("")
						.description("Text to translate.").build(),
				NodeParameter.builder()
						.name("targetLanguage").displayName("Target Language")
						.type(ParameterType.OPTIONS).defaultValue("EN")
						.options(List.of(
								ParameterOption.builder().name("Bulgarian").value("BG").build(),
								ParameterOption.builder().name("Chinese (Simplified)").value("ZH").build(),
								ParameterOption.builder().name("Czech").value("CS").build(),
								ParameterOption.builder().name("Danish").value("DA").build(),
								ParameterOption.builder().name("Dutch").value("NL").build(),
								ParameterOption.builder().name("English").value("EN").build(),
								ParameterOption.builder().name("Estonian").value("ET").build(),
								ParameterOption.builder().name("Finnish").value("FI").build(),
								ParameterOption.builder().name("French").value("FR").build(),
								ParameterOption.builder().name("German").value("DE").build(),
								ParameterOption.builder().name("Greek").value("EL").build(),
								ParameterOption.builder().name("Hungarian").value("HU").build(),
								ParameterOption.builder().name("Indonesian").value("ID").build(),
								ParameterOption.builder().name("Italian").value("IT").build(),
								ParameterOption.builder().name("Japanese").value("JA").build(),
								ParameterOption.builder().name("Korean").value("KO").build(),
								ParameterOption.builder().name("Latvian").value("LV").build(),
								ParameterOption.builder().name("Lithuanian").value("LT").build(),
								ParameterOption.builder().name("Polish").value("PL").build(),
								ParameterOption.builder().name("Portuguese").value("PT").build(),
								ParameterOption.builder().name("Romanian").value("RO").build(),
								ParameterOption.builder().name("Russian").value("RU").build(),
								ParameterOption.builder().name("Slovak").value("SK").build(),
								ParameterOption.builder().name("Slovenian").value("SL").build(),
								ParameterOption.builder().name("Spanish").value("ES").build(),
								ParameterOption.builder().name("Swedish").value("SV").build(),
								ParameterOption.builder().name("Turkish").value("TR").build(),
								ParameterOption.builder().name("Ukrainian").value("UK").build()
						)).build(),
				NodeParameter.builder()
						.name("sourceLanguage").displayName("Source Language")
						.type(ParameterType.STRING).defaultValue("")
						.description("Source language code (auto-detected if empty).").build(),
				NodeParameter.builder()
						.name("formality").displayName("Formality")
						.type(ParameterType.OPTIONS).defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("Default").value("").build(),
								ParameterOption.builder().name("More Formal").value("more").build(),
								ParameterOption.builder().name("Less Formal").value("less").build(),
								ParameterOption.builder().name("Prefer More Formal").value("prefer_more").build(),
								ParameterOption.builder().name("Prefer Less Formal").value("prefer_less").build()
						)).build()
		);
	}
}
