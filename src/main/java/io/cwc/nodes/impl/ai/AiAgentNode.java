package io.cwc.nodes.impl.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutor;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@Node(
		type = "aiAgent",
		displayName = "AI Agent",
		description = "Autonomous AI agent that can use tools, memory, and a language model to process requests",
		category = "AI",
		icon = "bot",
		implementationNotes = "Sub-nodes (chat model, memory, tools) connect TO this agent using special AI connection " +
			"types, not 'main'. The source is always the sub-node, the target is this agent. " +
			"CRITICAL DUAL-INDEX RULE: Each AI connection needs two entries in the inner array. " +
			"ai_languageModel (handle position 0): single entry {index: 0}. " +
			"ai_memory (handle position 1): two entries {index: 0} AND {index: 1}. " +
			"ai_tool (handle position 2): two entries {index: 0} AND {index: 2}. " +
			"The ai_tool index is ALWAYS 2 even with multiple tools — do NOT increment to 3, 4, 5. " +
			"Every tool connection uses index 0 and index 2 identically. " +
			"Example connections: " +
			"model1: {ai_languageModel: [[{node: 'agent1', type: 'ai_languageModel', index: 0}]]}, " +
			"memory1: {ai_memory: [[{node: 'agent1', type: 'ai_memory', index: 0}, {node: 'agent1', type: 'ai_memory', index: 1}]]}, " +
			"tool1: {ai_tool: [[{node: 'agent1', type: 'ai_tool', index: 0}, {node: 'agent1', type: 'ai_tool', index: 2}]]}. " +
			"MEMORY MODES: The 'memoryMode' parameter controls how conversation context is managed. " +
			"'Default' uses a simple message window capped at maxIterations. " +
			"'Sliding Window' keeps the N most recent messages (configurable via memoryWindowSize). " +
			"'Token Budget' keeps messages up to a token limit (memoryTokenBudget). " +
			"'Summarization' compresses older messages into a summary after memorySummaryThreshold messages. " +
			"'Hybrid' combines sliding window with summarization. " +
			"PERSISTENCE: To persist chat history across restarts or share across instances, wire a database " +
			"memory node (Postgres, MySQL, Oracle, Redis, MongoDB, Xata) to the ai_memory input. " +
			"The database node provides the backing store; the memory mode controls the strategy. " +
			"Simple Memory and Motorhead nodes supply their own complete memory and are used directly."
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

		String systemMessageText = context.getParameter("systemMessage", "You are a helpful assistant.");
		String promptTemplate = context.getParameter("prompt", "{{input}}");
		int maxIterations = toInt(context.getParameters().get("maxIterations"), 10);

		Object wiredMemoryInput = context.getAiInput("ai_memory", Object.class);
		ChatMemory memory = AgentMemoryFactory.resolveMemory(context, wiredMemoryInput, model, maxIterations);

		// Separate tool inputs into real tools and sub-agents
		List<Object> toolInputs = context.getAiInputs("ai_tool", Object.class);
		Map<ToolSpecification, ToolExecutor> toolMap = new LinkedHashMap<>();
		List<Object> subAgents = new ArrayList<>();
		AiSubAgentNode.separateToolsAndAgents(toolInputs, toolMap, subAgents);

		if (!subAgents.isEmpty()) {
			// --- Supervisor path: delegate to sub-agents ---
			return executeSupervisor(context, model, memory, toolMap, subAgents,
					systemMessageText, promptTemplate, maxIterations);
		} else {
			// --- Existing AiServices path (backwards compatible) ---
			return executeAiServices(context, model, memory, toolMap,
					systemMessageText, promptTemplate, maxIterations);
		}
	}

	/**
	 * Wraps a ChatModel to strip markdown code fences from AI responses.
	 * Some models (especially smaller/local ones like Ollama) wrap JSON in ```json ... ```
	 * which breaks LangChain4j's AgentInvocation parser.
	 */
	private static final Pattern MARKDOWN_FENCE = Pattern.compile("^\\s*```(?:json)?\\s*\\n?(.*?)\\n?\\s*```\\s*$", Pattern.DOTALL);

	private static ChatModel stripMarkdownFences(ChatModel delegate) {
		return new ChatModel() {
			@Override
			public ChatResponse chat(ChatRequest request) {
				ChatResponse response = delegate.chat(request);
				AiMessage ai = response.aiMessage();
				if (ai != null && ai.text() != null) {
					var matcher = MARKDOWN_FENCE.matcher(ai.text());
					if (matcher.matches()) {
						String cleaned = matcher.group(1).trim();
						return ChatResponse.builder()
								.aiMessage(AiMessage.from(cleaned))
								.metadata(response.metadata())
								.build();
					}
				}
				return response;
			}
		};
	}

	private NodeExecutionResult executeSupervisor(NodeExecutionContext context, ChatModel model,
			ChatMemory memory, Map<ToolSpecification, ToolExecutor> toolMap,
			List<Object> subAgents, String systemMessageText,
			String promptTemplate, int maxIterations) {

		var supervisorBuilder = AgenticServices.supervisorBuilder()
				.chatModel(stripMarkdownFences(model))
				.maxAgentsInvocations(maxIterations)
				.responseStrategy(SupervisorResponseStrategy.SUMMARY);

		// Add sub-agents
		supervisorBuilder.subAgents(subAgents.toArray());

		if (systemMessageText != null && !systemMessageText.isBlank()) {
			supervisorBuilder.supervisorContext(systemMessageText);
		}

		SupervisorAgent supervisor = supervisorBuilder.build();

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		if (inputData == null || inputData.isEmpty()) {
			try {
				String response = supervisor.invoke(promptTemplate);
				results.add(wrapInJson(Map.of("output", response)));
			} catch (Exception e) {
				return handleError(context, "AI Agent (Supervisor) failed: " + getRootCauseMessage(e), e);
			}
		} else {
			for (Map<String, Object> item : inputData) {
				try {
					Map<String, Object> json = unwrapJson(item);
					String resolvedPrompt = resolvePrompt(promptTemplate, json);
					String response = supervisor.invoke(resolvedPrompt);
					results.add(wrapInJson(Map.of("output", response)));
				} catch (Exception e) {
					if (context.isContinueOnFail()) {
						results.add(wrapInJson(Map.of("error", getRootCauseMessage(e))));
					} else {
						return handleError(context, "AI Agent (Supervisor) failed: " + getRootCauseMessage(e), e);
					}
				}
			}
		}

		return NodeExecutionResult.success(results);
	}

	private NodeExecutionResult executeAiServices(NodeExecutionContext context, ChatModel model,
			ChatMemory memory, Map<ToolSpecification, ToolExecutor> toolMap,
			String systemMessageText, String promptTemplate, int maxIterations) {

		var builder = AiServices.builder(AgentService.class)
				.chatModel(model);

		if (memory != null) {
			builder.chatMemory(memory);
		} else {
			builder.chatMemory(MessageWindowChatMemory.withMaxMessages(maxIterations));
		}

		if (!toolMap.isEmpty()) {
			builder.tools(toolMap);
		}

		if (systemMessageText != null && !systemMessageText.isBlank()) {
			builder.systemMessageProvider(memoryId -> systemMessageText);
		}

		AgentService agent = builder.build();

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		if (inputData == null || inputData.isEmpty()) {
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
					String resolvedPrompt = resolvePrompt(promptTemplate, json);
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

	private String getRootCauseMessage(Throwable e) {
		StringBuilder sb = new StringBuilder();
		sb.append(e.getMessage());
		Throwable cause = e.getCause();
		int depth = 0;
		while (cause != null && depth < 5) {
			sb.append(" -> ").append(cause.getMessage());
			cause = cause.getCause();
			depth++;
		}
		return sb.toString();
	}

	private String resolvePrompt(String promptTemplate, Map<String, Object> json) {
		String resolvedPrompt = promptTemplate;
		for (Map.Entry<String, Object> entry : json.entrySet()) {
			resolvedPrompt = resolvedPrompt.replace(
					"{{ " + entry.getKey() + " }}",
					String.valueOf(entry.getValue()));
			resolvedPrompt = resolvedPrompt.replace(
					"{{" + entry.getKey() + "}}",
					String.valueOf(entry.getValue()));
		}
		return resolvedPrompt;
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
		List<NodeParameter> params = new java.util.ArrayList<>(List.of(
				NodeParameter.builder()
						.name("agentDefinitionId").displayName("Predefined Agent")
						.type(ParameterType.OPTIONS)
						.defaultValue("")
						.description("Select a predefined agent to inherit its model, memory, tools, and system prompt. Leave empty to configure manually.")
						.build(),
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
		));
		params.addAll(AgentMemoryFactory.memoryParameters());
		return params;
	}
}
