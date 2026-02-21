package io.trellis.nodes.impl.ai;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.DynamicTool;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

@Node(
		type = "aiAgent",
		displayName = "AI Agent",
		description = "Autonomous AI agent that can use tools, memory, and a language model to process requests",
		category = "AI / Agents",
		icon = "bot"
)
public class AiAgentNode extends AbstractNode {

	interface AgentService {
		String chat(String message);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		ChatModel model = context.getAiInput("ai_languageModel", ChatModel.class);
		if (model == null) {
			return NodeExecutionResult.error("No language model connected. Connect a Chat Model sub-node.");
		}

		ChatMemory memory = context.getAiInput("ai_memory", ChatMemory.class);
		List<Object> tools = context.getAiInputs("ai_tool", Object.class);

		String systemMessageText = context.getParameter("systemMessage", "You are a helpful assistant.");
		String promptTemplate = context.getParameter("prompt", "{{input}}");
		int maxIterations = toInt(context.getParameters().get("maxIterations"), 10);

		// Build AiServices agent
		var builder = AiServices.builder(AgentService.class)
				.chatModel(model);

		if (memory != null) {
			builder.chatMemory(memory);
		} else {
			builder.chatMemory(MessageWindowChatMemory.withMaxMessages(maxIterations));
		}

		if (!tools.isEmpty()) {
			Map<ToolSpecification, ToolExecutor> toolMap = new LinkedHashMap<>();
			for (Object toolObj : tools) {
				if (toolObj instanceof DynamicTool dt) {
					toolMap.put(dt.specification(), dt.executor());
				} else if (toolObj instanceof List<?> toolList) {
					// List of DynamicTools (e.g. from MCP Client Tool)
					for (Object item : toolList) {
						if (item instanceof DynamicTool dt) {
							toolMap.put(dt.specification(), dt.executor());
						}
					}
				} else {
					// @Tool-annotated object — extract specs and build executors
					for (Method method : toolObj.getClass().getDeclaredMethods()) {
						if (method.isAnnotationPresent(Tool.class)) {
							ToolSpecification spec = ToolSpecifications.toolSpecificationFrom(method);
							toolMap.put(spec, new DefaultToolExecutor(toolObj, method));
						}
					}
				}
			}
			if (!toolMap.isEmpty()) {
				builder.tools(toolMap);
			}
		}

		if (systemMessageText != null && !systemMessageText.isBlank()) {
			builder.systemMessageProvider(memoryId -> systemMessageText);
		}

		AgentService agent = builder.build();

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		if (inputData == null || inputData.isEmpty()) {
			// No input data — use the prompt directly
			try {
				String response = agent.chat(promptTemplate);
				results.add(wrapInJson(Map.of("output", response)));
			} catch (Exception e) {
				return handleError(context, "AI Agent failed: " + e.getMessage(), e);
			}
		} else {
			for (Map<String, Object> item : inputData) {
				try {
					Map<String, Object> json = unwrapJson(item);

					// Resolve prompt with input data
					String resolvedPrompt = promptTemplate;
					for (Map.Entry<String, Object> entry : json.entrySet()) {
						resolvedPrompt = resolvedPrompt.replace(
								"{{ " + entry.getKey() + " }}",
								String.valueOf(entry.getValue()));
						resolvedPrompt = resolvedPrompt.replace(
								"{{" + entry.getKey() + "}}",
								String.valueOf(entry.getValue()));
					}

					String response = agent.chat(resolvedPrompt);
					results.add(wrapInJson(Map.of("output", response)));
				} catch (Exception e) {
					if (context.isContinueOnFail()) {
						results.add(wrapInJson(Map.of("error", e.getMessage())));
					} else {
						return handleError(context, "AI Agent failed: " + e.getMessage(), e);
					}
				}
			}
		}

		return NodeExecutionResult.success(results);
	}

	@Override
	public List<NodeInput> getInputs() {
		return List.of(
				NodeInput.builder().name("main").displayName("Main").type(NodeInput.InputType.MAIN).build(),
				NodeInput.builder().name("ai_languageModel").displayName("Model")
						.type(NodeInput.InputType.AI_LANGUAGE_MODEL).required(true).maxConnections(1).build(),
				NodeInput.builder().name("ai_memory").displayName("Memory")
						.type(NodeInput.InputType.AI_MEMORY).required(false).maxConnections(1).build(),
				NodeInput.builder().name("ai_tool").displayName("Tools")
						.type(NodeInput.InputType.AI_TOOL).required(false).maxConnections(-1).build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("systemMessage").displayName("System Message")
						.type(ParameterType.STRING)
						.typeOptions(Map.of("rows", 6))
						.defaultValue("You are a helpful assistant.")
						.description("Instructions for the agent's behavior and persona")
						.build(),
				NodeParameter.builder()
						.name("prompt").displayName("Prompt")
						.type(ParameterType.STRING)
						.typeOptions(Map.of("rows", 4))
						.defaultValue("{{input}}")
						.description("The prompt template. Use {{fieldName}} to inject input data.")
						.build(),
				NodeParameter.builder()
						.name("maxIterations").displayName("Max Iterations")
						.type(ParameterType.NUMBER)
						.defaultValue(10)
						.description("Maximum number of tool-use iterations before stopping")
						.build()
		);
	}
}
