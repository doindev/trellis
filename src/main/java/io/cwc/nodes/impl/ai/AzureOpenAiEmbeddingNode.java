package io.cwc.nodes.impl.ai;

import dev.langchain4j.model.azure.AzureOpenAiEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractEmbeddingNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.util.List;

@Node(
		type = "azureOpenAiEmbedding",
		displayName = "Azure OpenAI Embeddings",
		description = "Generate embeddings using Azure OpenAI",
		category = "AI / Embeddings",
		icon = "azure",
		credentials = {"azureOpenAiApi"}
)
public class AzureOpenAiEmbeddingNode extends AbstractEmbeddingNode {

	@Override
	protected EmbeddingModel createEmbeddingModel(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		String endpoint = context.getCredentialString("endpoint");
		String apiVersion = context.getCredentialString("apiVersion", "2024-02-15-preview");
		String deploymentName = context.getParameter("deploymentName", "");
		int dimensions = toInt(context.getParameters().get("dimensions"), 0);

		var builder = AzureOpenAiEmbeddingModel.builder()
				.apiKey(apiKey)
				.endpoint(endpoint)
				.serviceVersion(apiVersion)
				.deploymentName(deploymentName);

		if (dimensions > 0) {
			builder.dimensions(dimensions);
		}

		return builder.build();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("deploymentName").displayName("Deployment Name")
						.type(ParameterType.STRING).required(true)
						.description("The name of the Azure OpenAI embedding deployment").build(),
				NodeParameter.builder()
						.name("dimensions").displayName("Dimensions")
						.type(ParameterType.NUMBER)
						.defaultValue(0)
						.description("Output dimensions. 0 for model default.").build()
		);
	}
}
