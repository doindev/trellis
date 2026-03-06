package io.cwc.nodes.base;

import dev.langchain4j.model.chat.ChatModel;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeOutput;

import java.util.List;

/**
 * Base class for chat model sub-nodes. Outputs an ai_languageModel connection.
 * Subclasses implement createChatModel() to build the provider-specific model instance.
 */
public abstract class AbstractChatModelNode extends AbstractAiSubNode {

	@Override
	public Object supplyData(NodeExecutionContext context) throws Exception {
		return createChatModel(context);
	}

	protected abstract ChatModel createChatModel(NodeExecutionContext context);

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
				NodeOutput.builder()
						.name("ai_languageModel")
						.displayName("Model")
						.type(NodeOutput.OutputType.AI_LANGUAGE_MODEL)
						.build()
		);
	}
}
