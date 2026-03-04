package io.trellis.nodes.impl.ai;

import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractAiMemoryNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;

@Node(
		type = "tokenBufferMemory",
		displayName = "Token Buffer Memory",
		description = "Keeps messages up to a token limit in memory",
		category = "AI / Memory",
		icon = "brain",
		searchOnly = true
)
public class TokenBufferMemoryNode extends AbstractAiMemoryNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		int maxTokens = toInt(context.getParameters().get("maxTokens"), 2000);

		return TokenWindowChatMemory.builder()
				.maxTokens(maxTokens, new OpenAiTokenCountEstimator("gpt-4o"))
				.build();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("maxTokens").displayName("Max Tokens")
						.type(ParameterType.NUMBER)
						.defaultValue(2000)
						.description("Maximum number of tokens to keep in memory")
						.build()
		);
	}
}
