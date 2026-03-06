package io.cwc.nodes.impl.ai;

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.chat.ChatModel;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractChatModelNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import java.util.List;

/**
 * AWS Bedrock Chat Model — uses the Bedrock Converse API to access
 * foundation models (Claude, Titan, Llama, Mistral, etc.) hosted on AWS Bedrock.
 */
@Node(
		type = "lmChatAwsBedrock",
		displayName = "AWS Bedrock Chat Model",
		description = "Chat model via AWS Bedrock Converse API",
		category = "AI / Chat Models",
		icon = "aws",
		credentials = {"aws"},
		searchOnly = true
)
public class BedrockChatModelNode extends AbstractChatModelNode {

	@Override
	protected ChatModel createChatModel(NodeExecutionContext context) {
		String accessKeyId = context.getCredentialString("accessKeyId");
		String secretAccessKey = context.getCredentialString("secretAccessKey");
		String region = context.getCredentialString("region", "us-east-1");
		String model = context.getParameter("model", "anthropic.claude-3-haiku-20240307-v1:0");
		double temperature = toDouble(context.getParameters().get("temperature"), 0.7);
		int maxTokens = toInt(context.getParameters().get("maxTokens"), 2000);

		BedrockRuntimeClient client = BedrockRuntimeClient.builder()
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
				.build();

		return BedrockChatModel.builder()
				.client(client)
				.modelId(model)
				.build();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("model").displayName("Model")
						.type(ParameterType.OPTIONS)
						.defaultValue("anthropic.claude-3-haiku-20240307-v1:0")
						.options(List.of(
								ParameterOption.builder().name("Claude 3.5 Sonnet").value("anthropic.claude-3-5-sonnet-20241022-v2:0").build(),
								ParameterOption.builder().name("Claude 3.5 Haiku").value("anthropic.claude-3-5-haiku-20241022-v1:0").build(),
								ParameterOption.builder().name("Claude 3 Haiku").value("anthropic.claude-3-haiku-20240307-v1:0").build(),
								ParameterOption.builder().name("Llama 3.1 70B Instruct").value("meta.llama3-1-70b-instruct-v1:0").build(),
								ParameterOption.builder().name("Llama 3.1 8B Instruct").value("meta.llama3-1-8b-instruct-v1:0").build(),
								ParameterOption.builder().name("Mistral Large").value("mistral.mistral-large-2407-v1:0").build(),
								ParameterOption.builder().name("Amazon Titan Text Express").value("amazon.titan-text-express-v1").build()
						)).build(),
				NodeParameter.builder()
						.name("temperature").displayName("Temperature")
						.type(ParameterType.NUMBER).defaultValue(0.7)
						.description("Sampling temperature (0–1).").build(),
				NodeParameter.builder()
						.name("maxTokens").displayName("Max Tokens")
						.type(ParameterType.NUMBER).defaultValue(2000)
						.description("Maximum number of tokens to generate.").build()
		);
	}
}
