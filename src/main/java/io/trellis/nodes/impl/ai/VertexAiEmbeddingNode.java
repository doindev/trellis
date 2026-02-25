package io.trellis.nodes.impl.ai;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.vertexai.VertexAiEmbeddingModel;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractEmbeddingNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;

/**
 * Google Vertex AI Embeddings — generates embeddings using Google Cloud
 * Vertex AI text embedding models with service account authentication.
 */
@Node(
		type = "embeddingsGoogleVertex",
		displayName = "Google Vertex AI Embeddings",
		description = "Generate embeddings via Google Cloud Vertex AI",
		category = "AI / Embeddings",
		icon = "google",
		credentials = {"googleApi"}
)
public class VertexAiEmbeddingNode extends AbstractEmbeddingNode {

	@Override
	protected EmbeddingModel createEmbeddingModel(NodeExecutionContext context) {
		String projectId = context.getParameter("projectId", "");
		String location = context.getCredentialString("region", "us-central1");
		String model = context.getParameter("model", "text-embedding-005");

		return VertexAiEmbeddingModel.builder()
				.project(projectId)
				.location(location)
				.modelName(model)
				.build();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("projectId").displayName("Project ID")
						.type(ParameterType.STRING).defaultValue("")
						.required(true)
						.description("Google Cloud project ID.").build(),
				NodeParameter.builder()
						.name("model").displayName("Model")
						.type(ParameterType.STRING)
						.defaultValue("text-embedding-005")
						.description("Embedding model name (e.g. text-embedding-005, text-embedding-004).").build()
		);
	}
}
