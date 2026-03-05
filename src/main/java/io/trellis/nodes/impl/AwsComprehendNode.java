package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.model.*;

import java.util.*;

/**
 * AWS Comprehend — detect language, sentiment, and entities in text using Amazon Comprehend.
 */
@Node(
		type = "awsComprehend",
		displayName = "AWS Comprehend",
		description = "Detect language, sentiment, and entities in text",
		category = "AWS",
		icon = "aws",
		credentials = {"aws"}
)
public class AwsComprehendNode extends AbstractNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessKeyId = context.getCredentialString("accessKeyId");
		String secretAccessKey = context.getCredentialString("secretAccessKey");
		String region = context.getCredentialString("region", "us-east-1");
		String operation = context.getParameter("operation", "detectDominantLanguage");

		ComprehendClient client = ComprehendClient.builder()
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
				.build();

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "detectDominantLanguage" -> detectDominantLanguage(context, client);
					case "detectSentiment" -> detectSentiment(context, client);
					case "detectEntities" -> detectEntities(context, client);
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

		client.close();
		return NodeExecutionResult.success(results);
	}

	private Map<String, Object> detectDominantLanguage(NodeExecutionContext context, ComprehendClient client) {
		String text = context.getParameter("text", "");
		boolean simplify = toBoolean(context.getParameters().get("simplify"), true);

		DetectDominantLanguageResponse response = client.detectDominantLanguage(
				DetectDominantLanguageRequest.builder().text(text).build());

		Map<String, Object> result = new LinkedHashMap<>();
		if (simplify && !response.languages().isEmpty()) {
			DominantLanguage top = response.languages().get(0);
			result.put("languageCode", top.languageCode());
			result.put("score", top.score());
		} else {
			List<Map<String, Object>> languages = new ArrayList<>();
			for (DominantLanguage lang : response.languages()) {
				Map<String, Object> l = new LinkedHashMap<>();
				l.put("languageCode", lang.languageCode());
				l.put("score", lang.score());
				languages.add(l);
			}
			result.put("languages", languages);
		}
		return result;
	}

	private Map<String, Object> detectSentiment(NodeExecutionContext context, ComprehendClient client) {
		String text = context.getParameter("text", "");
		String languageCode = context.getParameter("languageCode", "en");

		DetectSentimentResponse response = client.detectSentiment(
				DetectSentimentRequest.builder()
						.text(text)
						.languageCode(languageCode)
						.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("sentiment", response.sentimentAsString());

		Map<String, Object> scores = new LinkedHashMap<>();
		SentimentScore ss = response.sentimentScore();
		scores.put("positive", ss.positive());
		scores.put("negative", ss.negative());
		scores.put("neutral", ss.neutral());
		scores.put("mixed", ss.mixed());
		result.put("sentimentScore", scores);
		return result;
	}

	private Map<String, Object> detectEntities(NodeExecutionContext context, ComprehendClient client) {
		String text = context.getParameter("text", "");
		String languageCode = context.getParameter("languageCode", "en");
		String endpointArn = context.getParameter("endpointArn", "");

		DetectEntitiesRequest.Builder builder = DetectEntitiesRequest.builder()
				.text(text)
				.languageCode(languageCode);

		if (!endpointArn.isBlank()) {
			builder.endpointArn(endpointArn);
		}

		DetectEntitiesResponse response = client.detectEntities(builder.build());

		List<Map<String, Object>> entities = new ArrayList<>();
		for (Entity entity : response.entities()) {
			Map<String, Object> e = new LinkedHashMap<>();
			e.put("text", entity.text());
			e.put("type", entity.typeAsString());
			e.put("score", entity.score());
			e.put("beginOffset", entity.beginOffset());
			e.put("endOffset", entity.endOffset());
			entities.add(e);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("entities", entities);
		return result;
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("detectDominantLanguage")
						.options(List.of(
								ParameterOption.builder().name("Detect Dominant Language").value("detectDominantLanguage").build(),
								ParameterOption.builder().name("Detect Sentiment").value("detectSentiment").build(),
								ParameterOption.builder().name("Detect Entities").value("detectEntities").build()
						)).build(),
				NodeParameter.builder()
						.name("text").displayName("Text")
						.type(ParameterType.STRING).defaultValue("")
						.description("The text to analyze.").build(),
				NodeParameter.builder()
						.name("languageCode").displayName("Language Code")
						.type(ParameterType.OPTIONS)
						.defaultValue("en")
						.options(List.of(
								ParameterOption.builder().name("Arabic").value("ar").build(),
								ParameterOption.builder().name("Chinese (Simplified)").value("zh").build(),
								ParameterOption.builder().name("Chinese (Traditional)").value("zh-TW").build(),
								ParameterOption.builder().name("English").value("en").build(),
								ParameterOption.builder().name("French").value("fr").build(),
								ParameterOption.builder().name("German").value("de").build(),
								ParameterOption.builder().name("Hindi").value("hi").build(),
								ParameterOption.builder().name("Italian").value("it").build(),
								ParameterOption.builder().name("Japanese").value("ja").build(),
								ParameterOption.builder().name("Korean").value("ko").build(),
								ParameterOption.builder().name("Portuguese").value("pt").build(),
								ParameterOption.builder().name("Spanish").value("es").build()
						)).build(),
				NodeParameter.builder()
						.name("simplify").displayName("Simplify")
						.type(ParameterType.BOOLEAN).defaultValue(true)
						.description("Return only the top detected language.").build(),
				NodeParameter.builder()
						.name("endpointArn").displayName("Endpoint ARN")
						.type(ParameterType.STRING).defaultValue("")
						.description("Custom endpoint ARN for entity detection (optional).").build()
		);
	}
}
