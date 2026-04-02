package io.cwc.nodes.impl.ai;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

/**
 * Shared utility for resolving memory configuration across AiAgentNode,
 * AiSubAgentNode, and AiAgentToolNode. Reads the memoryMode parameter
 * and constructs the appropriate ChatMemory instance.
 */
public final class AgentMemoryFactory {

    private AgentMemoryFactory() {}

    /**
     * Resolves the ChatMemory instance based on the agent node's memory parameters.
     *
     * @param context          execution context with parameters
     * @param wiredMemory      externally wired ai_memory input (may be null)
     * @param chatModel        the agent's chat model (needed for summarization)
     * @param fallbackMaxMessages default window size when no config is present
     */
    public static ChatMemory resolveMemory(
            NodeExecutionContext context,
            ChatMemory wiredMemory,
            ChatModel chatModel,
            int fallbackMaxMessages) {

        String mode = context.getParameter("memoryMode", "external");

        return switch (mode) {
            case "sliding_window" -> {
                int windowSize = toInt(context.getParameters().get("memoryWindowSize"), 20);
                yield MessageWindowChatMemory.withMaxMessages(windowSize);
            }
            case "token_budget" -> {
                int tokenBudget = toInt(context.getParameters().get("memoryTokenBudget"), 4000);
                yield TokenWindowChatMemory.builder()
                        .maxTokens(tokenBudget, new OpenAiTokenCountEstimator("gpt-4o"))
                        .build();
            }
            case "summarization" -> {
                int threshold = toInt(context.getParameters().get("memorySummaryThreshold"), 10);
                yield new SummarizingChatMemory(chatModel, threshold, 0);
            }
            case "hybrid" -> {
                int windowSize = toInt(context.getParameters().get("memoryWindowSize"), 20);
                int threshold = toInt(context.getParameters().get("memorySummaryThreshold"), 10);
                yield new SummarizingChatMemory(chatModel, threshold, windowSize);
            }
            default -> { // "external"
                if (wiredMemory != null) {
                    yield wiredMemory;
                }
                yield MessageWindowChatMemory.withMaxMessages(fallbackMaxMessages);
            }
        };
    }

    /**
     * Returns the shared memory configuration parameters for agent nodes.
     * All three agent node types include these in their getParameters() list.
     */
    public static List<NodeParameter> memoryParameters() {
        return List.of(
                NodeParameter.builder()
                        .name("memoryMode").displayName("Memory Mode")
                        .type(ParameterType.OPTIONS)
                        .defaultValue("external")
                        .options(List.of(
                                ParameterOption.builder().name("External (Wired Node)").value("external").build(),
                                ParameterOption.builder().name("Sliding Window").value("sliding_window").build(),
                                ParameterOption.builder().name("Token Budget").value("token_budget").build(),
                                ParameterOption.builder().name("Summarization").value("summarization").build(),
                                ParameterOption.builder().name("Hybrid (Window + Summary)").value("hybrid").build()
                        ))
                        .description("How the agent manages conversation memory. 'External' uses a wired memory node.")
                        .build(),
                NodeParameter.builder()
                        .name("memoryWindowSize").displayName("Window Size")
                        .type(ParameterType.NUMBER)
                        .defaultValue(20)
                        .displayOptions(Map.of("show", Map.of("memoryMode", List.of("sliding_window", "hybrid"))))
                        .description("Number of recent messages to retain in context")
                        .build(),
                NodeParameter.builder()
                        .name("memoryTokenBudget").displayName("Token Budget")
                        .type(ParameterType.NUMBER)
                        .defaultValue(4000)
                        .displayOptions(Map.of("show", Map.of("memoryMode", List.of("token_budget"))))
                        .description("Maximum tokens allocated for conversation history")
                        .build(),
                NodeParameter.builder()
                        .name("memorySummaryThreshold").displayName("Summarize After N Messages")
                        .type(ParameterType.NUMBER)
                        .defaultValue(10)
                        .displayOptions(Map.of("show", Map.of("memoryMode", List.of("summarization", "hybrid"))))
                        .description("Number of messages before older ones are compressed into a summary")
                        .build()
        );
    }

    private static int toInt(Object value, int defaultValue) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { /* ignore */ }
        }
        return defaultValue;
    }
}
