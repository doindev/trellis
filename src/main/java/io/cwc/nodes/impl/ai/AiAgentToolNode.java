package io.cwc.nodes.impl.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
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
		type = "aiAgentTool",
		displayName = "AI Agent Tool",
		description = "An AI agent exposed as a tool. Unlike a Sub Agent, it is invoked as a regular tool call by the parent agent rather than being delegated to as a sub-agent.",
		category = "AI",
		icon = "brain-cog"
)
public class AiAgentToolNode extends AbstractAiSubNode {

	private static final ObjectMapper MAPPER = new ObjectMapper();

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

		// 2. Get memory (optional)
		ChatMemory memory = context.getAiInput("ai_memory", ChatMemory.class);

		// 3. Collect tool inputs — separate real tools from nested sub-agents
		List<Object> toolInputs = context.getAiInputs("ai_tool", Object.class);
		Map<ToolSpecification, ToolExecutor> toolMap = new LinkedHashMap<>();
		List<Object> nestedSubAgents = new ArrayList<>();
		AiSubAgentNode.separateToolsAndAgents(toolInputs, toolMap, nestedSubAgents);

		// 4. Read parameters
		String agentName = context.getParameter("agentName", "agent_tool");
		String agentDescription = context.getParameter("agentDescription", "A helpful AI agent tool");
		String systemMessage = context.getParameter("systemMessage", "You are a helpful assistant.");
		int maxIterations = toInt(context.getParameters().get("maxIterations"), 10);

		// 5. Build the agent (supervisor or simple, same as AiSubAgentNode)
		Object agent;
		if (!nestedSubAgents.isEmpty()) {
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

			agent = supervisorBuilder.build();
		} else {
			var builder = AgenticServices.agentBuilder()
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

			agent = builder.build();
		}

		// 6. Wrap as a DynamicTool so it's treated as a tool, not a sub-agent
		ToolSpecification spec = ToolSpecification.builder()
				.name(agentName)
				.description(agentDescription)
				.parameters(JsonObjectSchema.builder()
						.addStringProperty("request", "The request or question to send to the agent")
						.required("request")
						.build())
				.build();

		final Object agentRef = agent;
		ToolExecutor executor = (ToolExecutionRequest request, Object memoryId) -> {
			try {
				String input = extractJsonString(request.arguments(), "request");
				if (input == null || input.isBlank()) {
					input = request.arguments();
				}

				if (agentRef instanceof SupervisorAgent supervisor) {
					return supervisor.invoke(input);
				} else if (agentRef instanceof UntypedAgent untypedAgent) {
					Object result = untypedAgent.invoke(Map.of("request", input));
					return result != null ? result.toString() : "";
				}
				return "Agent invocation failed: unknown agent type";
			} catch (Exception e) {
				return "Agent tool error: " + e.getMessage();
			}
		};

		return new DynamicTool(spec, executor);
	}

	private static String extractJsonString(String json, String key) {
		if (json == null) return null;
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = MAPPER.readValue(json, Map.class);
			Object val = map.get(key);
			return val != null ? val.toString() : null;
		} catch (Exception e) {
			// Fallback: simple string extraction
			String search = "\"" + key + "\"";
			int idx = json.indexOf(search);
			if (idx < 0) return null;
			idx = json.indexOf(':', idx + search.length());
			if (idx < 0) return null;
			idx++;
			while (idx < json.length() && json.charAt(idx) == ' ') idx++;
			if (idx >= json.length() || json.charAt(idx) != '"') return null;
			idx++;
			int end = json.indexOf('"', idx);
			if (end < 0) return null;
			return json.substring(idx, end);
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
		return List.of(
				NodeParameter.builder()
						.name("agentName").displayName("Agent Name")
						.type(ParameterType.STRING)
						.defaultValue("agent_tool")
						.description("Tool name the parent agent uses to call this agent")
						.build(),
				NodeParameter.builder()
						.name("agentDescription").displayName("Agent Description")
						.type(ParameterType.STRING)
						.typeOptions(Map.of("rows", 3))
						.defaultValue("A helpful AI agent tool")
						.description("Description of what this agent does. The parent agent uses this to decide when to call it.")
						.build(),
				NodeParameter.builder()
						.name("systemMessage").displayName("System Message")
						.type(ParameterType.STRING)
						.typeOptions(Map.of("rows", 4))
						.defaultValue("You are a helpful assistant.")
						.description("Instructions for this agent's behavior and persona")
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
