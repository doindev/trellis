package io.trellis.nodes.impl.ai;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractAiMemoryNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;

@Node(
		type = "windowBufferMemory",
		displayName = "Simple Memory",
		description = "Stores chat history in process memory — no credentials required. Scoped by session ID.",
		category = "AI / Memory",
		icon = "brain",
		searchOnly = true
)
public class WindowBufferMemoryNode extends AbstractAiMemoryNode {

	/**
	 * Singleton in-memory store shared across all Simple Memory nodes.
	 * Session isolation is handled by the memory ID passed to MessageWindowChatMemory.
	 */
	private static final InMemoryChatMemoryStore STORE = new InMemoryChatMemoryStore();

	@Override
	public Object supplyData(NodeExecutionContext context) {
		int windowSize = toInt(context.getParameters().get("contextWindowLength"), 10);
		String sessionId = context.getParameter("sessionId", "default");

		String memoryId = context.getWorkflowId() + "__" + sessionId;

		return MessageWindowChatMemory.builder()
				.id(memoryId)
				.maxMessages(windowSize)
				.chatMemoryStore(STORE)
				.build();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("sessionId").displayName("Session ID")
						.type(ParameterType.STRING)
						.defaultValue("default")
						.description("A unique key to isolate this conversation. Use different IDs for different users or threads.")
						.build(),
				NodeParameter.builder()
						.name("contextWindowLength").displayName("Context Window Length")
						.type(ParameterType.NUMBER)
						.defaultValue(10)
						.description("Number of recent messages to keep in memory")
						.build()
		);
	}
}
