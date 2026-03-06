package io.cwc.nodes.impl.ai;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractVectorStoreNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.util.List;

@Node(
		type = "vectorStoreMilvus",
		displayName = "Milvus Vector Store",
		description = "Store and retrieve embeddings using Milvus",
		category = "AI / Vector Stores",
		icon = "database",
		credentials = {"milvusApi"}
)
public class MilvusVectorStoreNode extends AbstractVectorStoreNode {

	@Override
	protected EmbeddingStore<TextSegment> createEmbeddingStore(NodeExecutionContext context) {
		String url = context.getCredentialString("url", "http://localhost:19530");
		String username = context.getCredentialString("username", "");
		String password = context.getCredentialString("password", "");
		String token = context.getCredentialString("token", "");
		String collectionName = context.getParameter("collectionName", "");

		var builder = MilvusEmbeddingStore.builder()
				.uri(url)
				.collectionName(collectionName);

		if (username != null && !username.isBlank()) {
			builder.username(username);
		}
		if (password != null && !password.isBlank()) {
			builder.password(password);
		}
		if (token != null && !token.isBlank()) {
			builder.token(token);
		}

		return builder.build();
	}

	@Override
	protected List<NodeParameter> getStoreParameters() {
		return List.of(
				NodeParameter.builder()
						.name("collectionName").displayName("Collection Name")
						.type(ParameterType.STRING).required(true)
						.description("Name of the Milvus collection").build()
		);
	}
}
