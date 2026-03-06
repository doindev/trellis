package io.cwc.nodes.impl.ai;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.azure.search.AzureAiSearchEmbeddingStore;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractVectorStoreNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.util.List;

@Node(
		type = "vectorStoreAzureAISearch",
		displayName = "Azure AI Search Vector Store",
		description = "Store and retrieve embeddings using Azure AI Search",
		category = "AI / Vector Stores",
		icon = "azure",
		credentials = {"azureAiSearchApi"}
)
public class AzureAiSearchVectorStoreNode extends AbstractVectorStoreNode {

	@Override
	protected boolean supportsUpdate() {
		return true;
	}

	@Override
	protected EmbeddingStore<TextSegment> createEmbeddingStore(NodeExecutionContext context) {
		String endpoint = context.getCredentialString("endpoint");
		String apiKey = context.getCredentialString("apiKey");
		String indexName = context.getParameter("indexName", "n8n-vectorstore");

		return AzureAiSearchEmbeddingStore.builder()
				.endpoint(endpoint)
				.apiKey(apiKey)
				.indexName(indexName)
				.build();
	}

	@Override
	protected List<NodeParameter> getStoreParameters() {
		return List.of(
				NodeParameter.builder()
						.name("indexName").displayName("Index Name")
						.type(ParameterType.STRING)
						.defaultValue("n8n-vectorstore")
						.description("Name of the Azure AI Search index").build()
		);
	}
}
