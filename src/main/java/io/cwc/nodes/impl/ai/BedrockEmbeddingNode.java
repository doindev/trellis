package io.cwc.nodes.impl.ai;

import dev.langchain4j.model.bedrock.BedrockTitanEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractEmbeddingNode;
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
 * AWS Bedrock Embeddings — generates embeddings using AWS Bedrock
 * foundation models (Titan, Cohere) via the Bedrock Runtime API.
 */
@Node(
		type = "embeddingsAwsBedrock",
		displayName = "AWS Bedrock Embeddings",
		description = "Generate embeddings via AWS Bedrock",
		category = "AI / Embeddings",
		icon = "aws",
		credentials = {"aws"}
)
public class BedrockEmbeddingNode extends AbstractEmbeddingNode {

	@Override
	protected EmbeddingModel createEmbeddingModel(NodeExecutionContext context) {
		String accessKeyId = context.getCredentialString("accessKeyId");
		String secretAccessKey = context.getCredentialString("secretAccessKey");
		String region = context.getCredentialString("region", "us-east-1");
		String model = context.getParameter("model", "amazon.titan-embed-text-v2:0");

		BedrockRuntimeClient client = BedrockRuntimeClient.builder()
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
				.build();

		return BedrockTitanEmbeddingModel.builder()
				.client(client)
				.model(model)
				.build();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("model").displayName("Model")
						.type(ParameterType.OPTIONS)
						.defaultValue("amazon.titan-embed-text-v2:0")
						.options(List.of(
								ParameterOption.builder().name("Titan Text Embeddings V2").value("amazon.titan-embed-text-v2:0").build(),
								ParameterOption.builder().name("Titan Text Embeddings V1").value("amazon.titan-embed-text-v1").build(),
								ParameterOption.builder().name("Titan Multimodal Embeddings G1").value("amazon.titan-embed-image-v1").build(),
								ParameterOption.builder().name("Cohere Embed English").value("cohere.embed-english-v3").build(),
								ParameterOption.builder().name("Cohere Embed Multilingual").value("cohere.embed-multilingual-v3").build()
						)).build()
		);
	}
}
