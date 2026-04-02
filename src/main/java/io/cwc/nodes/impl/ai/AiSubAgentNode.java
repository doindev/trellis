package io.cwc.nodes.impl.ai;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractAiSubNode;
import io.cwc.nodes.core.DynamicTool;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@Node(
		type = "aiSubAgent",
		displayName = "AI Sub Agent",
		description = "Sub-agent that connects to an AI Agent or another Sub Agent via the Tool handle. Has its own tools, memory, and optionally its own model.",
		category = "AI",
		icon = "bot-message-square",
		implementationNotes = "Connects to a parent AI Agent or another Sub Agent via the ai_tool handle. " +
			"This node can have its own chat model, memory, and tools wired the same way as aiAgent. " +
			"CRITICAL DUAL-INDEX RULE for wiring sub-nodes to this sub-agent: " +
			"ai_languageModel (handle position 0): single entry {index: 0}. " +
			"ai_memory (handle position 1): two entries {index: 0} AND {index: 1}. " +
			"ai_tool (handle position 2): two entries {index: 0} AND {index: 2}. " +
			"The ai_tool index is ALWAYS 2 even with multiple tools — do NOT increment to 3, 4, 5. " +
			"To connect this sub-agent as a tool on a parent agent, use: " +
			"subAgent1: {ai_tool: [[{node: 'parentAgent', type: 'ai_tool', index: 0}, {node: 'parentAgent', type: 'ai_tool', index: 2}]]}"
)
public class AiSubAgentNode extends AbstractAiSubNode {

	/**
	 * Sub-agent interface that accepts a String request from the supervisor.
	 * The supervisor's @V("request") scope key passes a String, so the sub-agent
	 * must accept String (not Map) to avoid type conflicts.
	 */
	public interface SubAgentService {
		@dev.langchain4j.agentic.Agent
		@dev.langchain4j.service.UserMessage("{{request}}")
		String invoke(@dev.langchain4j.service.V("request") String request);
	}

	@Override
	public Object supplyData(NodeExecutionContext context) throws Exception {
		// 1. Get chat model — own input first, fall back to parent's model
		ChatModel model = context.getAiInput("ai_languageModel", ChatModel.class);
		if (model == null) {
			model = context.getParentAiInput("ai_languageModel", ChatModel.class);
		}
		if (model == null) {
			throw new IllegalStateException(
					"No language model available. Connect a Chat Model sub-node or ensure the parent agent has one.");
		}

		// 2. Get memory (optional) — resolved via memory mode parameters
		ChatMemory wiredMemory = context.getAiInput("ai_memory", ChatMemory.class);

		// 3. Collect tool inputs — separate real tools from nested sub-agents
		List<Object> toolInputs = context.getAiInputs("ai_tool", Object.class);
		Map<ToolSpecification, ToolExecutor> toolMap = new LinkedHashMap<>();
		List<Object> nestedSubAgents = new ArrayList<>();
		separateToolsAndAgents(toolInputs, toolMap, nestedSubAgents);

		// 4. Read parameters
		String agentName = context.getParameter("agentName", "sub_agent");
		String agentDescription = context.getParameter("agentDescription", "A helpful sub-agent");
		String systemMessage = context.getParameter("systemMessage", "You are a helpful assistant.");
		int maxIterations = toInt(context.getParameters().get("maxIterations"), 10);
		ChatMemory memory = AgentMemoryFactory.resolveMemory(context, wiredMemory, model, maxIterations);

		if (!nestedSubAgents.isEmpty()) {
			// --- Supervisor path: this sub-agent has its own sub-agents ---
			var supervisorBuilder = AgenticServices.supervisorBuilder()
					.chatModel(model)
					.name(agentName)
					.description(agentDescription)
					.maxAgentsInvocations(maxIterations)
					.responseStrategy(SupervisorResponseStrategy.SUMMARY)
					.subAgents(nestedSubAgents.toArray());

			if (systemMessage != null && !systemMessage.isBlank()) {
				supervisorBuilder.supervisorContext(systemMessage);
			}

			SupervisorAgent supervisor = supervisorBuilder.build();
			return supervisor;
		} else {
			// --- Simple agent path ---
			var builder = AgenticServices.agentBuilder(SubAgentService.class)
					.chatModel(model)
					.name(agentName)
					.description(agentDescription)
					.outputKey(agentName)
					.systemMessage(systemMessage);

			if (memory != null) {
				builder.chatMemory(memory);
			} else {
				builder.chatMemory(MessageWindowChatMemory.withMaxMessages(maxIterations));
			}

			if (!toolMap.isEmpty()) {
				builder.tools(toolMap);
			}

			SubAgentService agent = builder.build();
			return agent;
		}
	}

	/**
	 * Separate tool input objects into actual tools and nested sub-agents.
	 * Sub-agents are detected by checking for langchain4j agentic types.
	 */
	static void separateToolsAndAgents(List<Object> toolInputs,
			Map<ToolSpecification, ToolExecutor> toolMap,
			List<Object> subAgents) {
		for (Object toolObj : toolInputs) {
			if (isAgenticAgent(toolObj)) {
				subAgents.add(toolObj);
			} else if (toolObj instanceof DynamicTool dt) {
				toolMap.put(dt.specification(), dt.executor());
			} else if (toolObj instanceof List<?> toolList) {
				for (Object item : toolList) {
					if (item instanceof DynamicTool dt) {
						toolMap.put(dt.specification(), dt.executor());
					}
				}
			} else {
				for (Method method : toolObj.getClass().getDeclaredMethods()) {
					if (method.isAnnotationPresent(Tool.class)) {
						ToolSpecification spec = ToolSpecifications.toolSpecificationFrom(method);
						toolMap.put(spec, new DefaultToolExecutor(toolObj, method));
					}
				}
			}
		}
	}

	/**
	 * Check if an object is a langchain4j agentic agent (UntypedAgent, SupervisorAgent, or SubAgentService proxy).
	 */
	static boolean isAgenticAgent(Object obj) {
		if (obj instanceof UntypedAgent || obj instanceof SupervisorAgent || obj instanceof SubAgentService) {
			return true;
		}
		// Also check for InternalAgent interface (added by AgentBuilder proxy)
		try {
			return Class.forName("dev.langchain4j.agentic.internal.InternalAgent").isInstance(obj);
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	@Override
	public List<NodeInput> getInputs() {
		return List.of(
				NodeInput.builder().name("ai_languageModel").displayName("Model")
						.type(NodeInput.InputType.AI_LANGUAGE_MODEL).required(false).maxConnections(1).build(),
				NodeInput.builder().name("ai_memory").displayName("Memory")
						.type(NodeInput.InputType.AI_MEMORY).required(false).maxConnections(1).build(),
				NodeInput.builder().name("ai_tool").displayName("Tools")
						.type(NodeInput.InputType.AI_TOOL).required(false).maxConnections(-1).build()
		);
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
				NodeOutput.builder()
						.name("ai_tool")
						.displayName("Tool")
						.type(NodeOutput.OutputType.AI_TOOL)
						.build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new java.util.ArrayList<>(List.of(
				NodeParameter.builder()
						.name("agentName").displayName("Agent Name")
						.type(ParameterType.STRING)
						.defaultValue("sub_agent")
						.description("Name the supervisor uses to identify this agent")
						.build(),
				NodeParameter.builder()
						.name("agentDescription").displayName("Agent Description")
						.type(ParameterType.STRING)
						.typeOptions(Map.of("rows", 3))
						.defaultValue("A helpful sub-agent")
						.description("Description of what this agent does. The supervisor uses this to decide when to delegate tasks to it.")
						.build(),
				NodeParameter.builder()
						.name("systemMessage").displayName("System Message")
						.type(ParameterType.STRING)
						.typeOptions(Map.of("rows", 4))
						.defaultValue("You are a helpful assistant.")
						.description("Instructions for this sub-agent's behavior and persona")
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
