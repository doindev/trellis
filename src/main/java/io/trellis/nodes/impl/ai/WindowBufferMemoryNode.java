package io.trellis.nodes.impl.ai;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractAiMemoryNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;

@Node(
		type = "windowBufferMemory",
		displayName = "Window Buffer Memory",
		description = "Keeps the last N messages in memory",
		category = "AI / Memory",
		icon = "brain"
)
public class WindowBufferMemoryNode extends AbstractAiMemoryNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		int windowSize = toInt(context.getParameters().get("contextWindowLength"), 10);

		return MessageWindowChatMemory.builder()
				.maxMessages(windowSize)
				.build();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("contextWindowLength").displayName("Context Window Length")
						.type(ParameterType.NUMBER)
						.defaultValue(10)
						.description("Number of recent messages to keep in memory")
						.build()
		);
	}
}
