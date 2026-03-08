package io.cwc.nodes.impl.ai;

import dev.langchain4j.model.cohere.CohereScoringModel;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractAiSubNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeOutput.OutputType;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.util.List;

/**
 * Cohere Reranker — uses Cohere's reranking models to reorder documents
 * after retrieval from a vector store by relevance to the given query.
 */
@Node(
		type = "rerankerCohere",
		displayName = "Cohere Reranker",
		description = "Rerank documents by relevance using Cohere",
		category = "AI / Rerankers",
		icon = "brain",
		credentials = {"cohereApi"}
)
public class CohereRerankerNode extends AbstractAiSubNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		String model = context.getParameter("model", "rerank-v3.5");

		return CohereScoringModel.builder()
				.apiKey(apiKey)
				.modelName(model)
				.build();
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
				NodeOutput.builder()
						.name("ai_reranker")
						.displayName("Reranker")
						.type(OutputType.AI_RERANKER)
						.build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("model").displayName("Model")
						.type(ParameterType.OPTIONS)
						.defaultValue("rerank-v3.5")
						.options(List.of(
								ParameterOption.builder().name("rerank-v3.5").value("rerank-v3.5").build(),
								ParameterOption.builder().name("rerank-english-v3.0").value("rerank-english-v3.0").build(),
								ParameterOption.builder().name("rerank-multilingual-v3.0").value("rerank-multilingual-v3.0").build()
						))
						.description("The model to use for reranking documents.").build(),
				NodeParameter.builder()
						.name("topN").displayName("Top N")
						.type(ParameterType.NUMBER)
						.defaultValue(3)
						.description("Maximum number of documents to return after reranking.").build()
		);
	}
}
