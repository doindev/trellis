package io.trellis.nodes.impl.ai;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractVectorStoreNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Node(
		type = "vectorStoreInMemory",
		displayName = "Simple Vector Store",
		description = "In-memory vector store for development and testing",
		category = "AI / Vector Stores",
		icon = "database",
		searchOnly = true
)
public class SimpleVectorStoreNode extends AbstractVectorStoreNode {

	private static final Map<String, InMemoryEmbeddingStore<TextSegment>> STORES = new ConcurrentHashMap<>();

	@Override
	protected EmbeddingStore<TextSegment> createEmbeddingStore(NodeExecutionContext context) {
		String memoryKey = context.getParameter("memoryKey", "default");
		String storeKey = context.getWorkflowId() + "__" + memoryKey;

		boolean clearStore = toBoolean(context.getParameters().get("clearStore"), false);
		if (clearStore) {
			STORES.remove(storeKey);
		}

		return STORES.computeIfAbsent(storeKey, k -> new InMemoryEmbeddingStore<>());
	}

	@Override
	protected List<NodeParameter> getStoreParameters() {
		return List.of(
				NodeParameter.builder()
						.name("memoryKey").displayName("Memory Key")
						.type(ParameterType.STRING)
						.defaultValue("default")
						.description("Key to identify this store instance (scoped to workflow)").build(),
				NodeParameter.builder()
						.name("clearStore").displayName("Clear Store Before Insert")
						.type(ParameterType.BOOLEAN)
						.defaultValue(false)
						.description("Clear existing data before inserting")
						.displayOptions(Map.of("show", Map.of("mode", List.of("insert"))))
						.build()
		);
	}
}
