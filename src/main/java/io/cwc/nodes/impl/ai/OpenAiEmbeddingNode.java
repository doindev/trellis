package io.cwc.nodes.impl.ai;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractEmbeddingNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.util.List;

@Node(
		type = "openAiEmbedding",
		displayName = "OpenAI Embeddings",
		description = "Generate embeddings using OpenAI models",
		category = "AI / Embeddings",
		icon = "openai",
		credentials = {"openAiApi"}
)
public class OpenAiEmbeddingNode extends AbstractEmbeddingNode {

	@Override
	protected EmbeddingModel createEmbeddingModel(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		String baseUrl = context.getCredentialString("baseUrl", "");
		String model = context.getParameter("model", "text-embedding-3-small");
		int dimensions = toInt(context.getParameters().get("dimensions"), 0);

		var builder = OpenAiEmbeddingModel.builder()
				.apiKey(apiKey)
				.modelName(model);

		if (dimensions > 0) {
			builder.dimensions(dimensions);
		}
		if (baseUrl != null && !baseUrl.isBlank()) {
			builder.baseUrl(baseUrl);
		}

		return builder.build();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("model").displayName("Model")
						.type(ParameterType.OPTIONS)
						.defaultValue("text-embedding-3-small")
						.options(List.of(
								ParameterOption.builder().name("text-embedding-3-small").value("text-embedding-3-small").build(),
								ParameterOption.builder().name("text-embedding-3-large").value("text-embedding-3-large").build(),
								ParameterOption.builder().name("text-embedding-ada-002").value("text-embedding-ada-002").build()
						)).build(),
				NodeParameter.builder()
						.name("dimensions").displayName("Dimensions")
						.type(ParameterType.NUMBER)
						.defaultValue(0)
						.description("Output dimensions. 0 for model default.").build()
		);
	}
}
