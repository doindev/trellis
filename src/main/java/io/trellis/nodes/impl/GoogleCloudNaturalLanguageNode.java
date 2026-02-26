package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Google Cloud Natural Language — analyze sentiment of text using Google Cloud NLP API.
 */
@Node(
		type = "googleCloudNaturalLanguage",
		displayName = "Google Cloud Natural Language",
		description = "Analyze sentiment of text using Google Cloud Natural Language",
		category = "Google Services",
		icon = "googleCloudNaturalLanguage",
		credentials = {"googleCloudNaturalLanguageApi"}
)
public class GoogleCloudNaturalLanguageNode extends AbstractApiNode {

	private static final String BASE_URL = "https://language.googleapis.com";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");

		String operation = context.getParameter("operation", "analyzeSentiment");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "analyzeSentiment" -> {
						String source = context.getParameter("source", "content");

						Map<String, Object> document = new LinkedHashMap<>();
						String documentType = context.getParameter("documentType", "PLAIN_TEXT");
						document.put("type", documentType);

						String language = context.getParameter("language", "");
						if (!language.isEmpty()) document.put("language", language);

						if ("content".equals(source)) {
							document.put("content", context.getParameter("content", ""));
						} else {
							document.put("gcsContentUri", context.getParameter("gcsContentUri", ""));
						}

						String encodingType = context.getParameter("encodingType", "UTF16");

						Map<String, Object> body = new LinkedHashMap<>();
						body.put("document", document);
						body.put("encodingType", encodingType);

						HttpResponse<String> response = post(BASE_URL + "/v1/documents:analyzeSentiment", body, headers);
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
						.type(ParameterType.OPTIONS).defaultValue("analyzeSentiment")
						.options(List.of(
								ParameterOption.builder().name("Analyze Sentiment").value("analyzeSentiment").build()
						)).build(),
				NodeParameter.builder()
						.name("source").displayName("Source")
						.type(ParameterType.OPTIONS).defaultValue("content")
						.options(List.of(
								ParameterOption.builder().name("Content").value("content").build(),
								ParameterOption.builder().name("Google Cloud Storage URI").value("gcsContentUri").build()
						)).build(),
				NodeParameter.builder()
						.name("content").displayName("Content")
						.type(ParameterType.STRING).defaultValue("")
						.description("Text to analyze.").build(),
				NodeParameter.builder()
						.name("gcsContentUri").displayName("GCS Content URI")
						.type(ParameterType.STRING).defaultValue("")
						.description("Google Cloud Storage URI (gs://bucket/object).").build(),
				NodeParameter.builder()
						.name("documentType").displayName("Document Type")
						.type(ParameterType.OPTIONS).defaultValue("PLAIN_TEXT")
						.options(List.of(
								ParameterOption.builder().name("HTML").value("HTML").build(),
								ParameterOption.builder().name("Plain Text").value("PLAIN_TEXT").build()
						)).build(),
				NodeParameter.builder()
						.name("encodingType").displayName("Encoding Type")
						.type(ParameterType.OPTIONS).defaultValue("UTF16")
						.options(List.of(
								ParameterOption.builder().name("None").value("NONE").build(),
								ParameterOption.builder().name("UTF-8").value("UTF8").build(),
								ParameterOption.builder().name("UTF-16").value("UTF16").build(),
								ParameterOption.builder().name("UTF-32").value("UTF32").build()
						)).build(),
				NodeParameter.builder()
						.name("language").displayName("Language")
						.type(ParameterType.OPTIONS).defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("Auto-detect").value("").build(),
								ParameterOption.builder().name("Arabic").value("ar").build(),
								ParameterOption.builder().name("Chinese").value("zh").build(),
								ParameterOption.builder().name("Dutch").value("nl").build(),
								ParameterOption.builder().name("English").value("en").build(),
								ParameterOption.builder().name("French").value("fr").build(),
								ParameterOption.builder().name("German").value("de").build(),
								ParameterOption.builder().name("Indonesian").value("id").build(),
								ParameterOption.builder().name("Italian").value("it").build(),
								ParameterOption.builder().name("Japanese").value("ja").build(),
								ParameterOption.builder().name("Korean").value("ko").build(),
								ParameterOption.builder().name("Portuguese").value("pt").build(),
								ParameterOption.builder().name("Spanish").value("es").build(),
								ParameterOption.builder().name("Thai").value("th").build(),
								ParameterOption.builder().name("Turkish").value("tr").build(),
								ParameterOption.builder().name("Vietnamese").value("vi").build()
						)).build()
		);
	}
}
