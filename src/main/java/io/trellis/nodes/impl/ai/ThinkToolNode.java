package io.trellis.nodes.impl.ai;

import dev.langchain4j.agent.tool.Tool;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractAiToolNode;
import io.trellis.nodes.core.NodeExecutionContext;

/**
 * Think Tool — a simple tool that allows the AI agent to use a "scratchpad"
 * for reasoning. The tool accepts a thought string and returns it unchanged.
 * This gives the agent an explicit step to reason through complex problems
 * before producing a final answer.
 */
@Node(
		type = "toolThink",
		displayName = "Think",
		description = "Tool for AI agent reasoning — accepts a thought and returns it unchanged",
		category = "AI / Tools",
		icon = "lightbulb"
)
public class ThinkToolNode extends AbstractAiToolNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		return new ThinkTool();
	}

	public static class ThinkTool {

		@Tool("Use this tool to think about something. It will not obtain new information or change anything, "
				+ "but just return the input as-is. Use it when you need to reason through a problem step by step "
				+ "before taking action.")
		public String think(String thought) {
			return thought;
		}
	}
}
