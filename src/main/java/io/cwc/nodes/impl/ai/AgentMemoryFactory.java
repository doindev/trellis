package io.cwc.nodes.impl.ai;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

/**
 * Shared utility for resolving memory configuration across AiAgentNode,
 * AiSubAgentNode, and AiAgentToolNode.
 *
 * <p>The memory system separates two concerns:
 * <ul>
 *   <li><b>Strategy</b> (memoryMode parameter): how the context window is managed
 *       — sliding window, token budget, summarization, or hybrid.</li>
 *   <li><b>Persistence</b> (wired memory node): where messages are stored
 *       — in-memory (default), or a database-backed {@link ChatMemoryStore}
 *       (Postgres, MySQL, Oracle, Redis, MongoDB, etc.).</li>
 * </ul>
 *
 * <p>When a database memory node is wired, its {@code ChatMemoryStore} is composed
 * with the selected strategy so that chat history is persisted across restarts
 * and instances.  Simple Memory and Motorhead nodes supply a complete
 * {@code ChatMemory} and are used directly.
 */
public final class AgentMemoryFactory {

    private AgentMemoryFactory() {}

    /**
     * Resolves the ChatMemory instance based on the agent node's memory parameters
     * and any wired memory input.
     *
     * @param context          execution context with parameters
     * @param wiredInput       the wired ai_memory input — may be a {@link ChatMemoryStore},
     *                         a {@link ChatMemory}, or {@code null}
     * @param chatModel        the agent's chat model (needed for summarization)
     * @param fallbackMaxMessages default window size when no config is present
     */
    public static ChatMemory resolveMemory(
            NodeExecutionContext context,
            Object wiredInput,
            ChatModel chatModel,
            int fallbackMaxMessages) {

        String mode = context.getParameter("memoryMode", "default");

        // Determine the backing store and session ID
        ChatMemoryStore store;
        if (wiredInput instanceof ChatMemoryStore wiredStore) {
            // Database memory node wired — compose with selected strategy
            store = wiredStore;
        } else if (wiredInput instanceof ChatMemory wiredMemory) {
            // Complete ChatMemory wired (Simple Memory, Motorhead, etc.)
            // These manage their own strategy and persistence — use directly
            if ("default".equals(mode)) {
                return wiredMemory;
            }
            // Non-default mode selected with a ChatMemory node: use it directly
            // since we can't extract its store
            return wiredMemory;
        } else {
            // Nothing wired — pure in-memory
            store = new InMemoryChatMemoryStore();
        }

        String sessionId = resolveSessionId(context);

        return switch (mode) {
            case "sliding_window" -> {
                int windowSize = toInt(context.getParameters().get("memoryWindowSize"), 20);
                yield MessageWindowChatMemory.builder()
                        .id(sessionId)
                        .maxMessages(windowSize)
                        .chatMemoryStore(store)
                        .build();
            }
            case "token_budget" -> {
                int tokenBudget = toInt(context.getParameters().get("memoryTokenBudget"), 4000);
                yield TokenWindowChatMemory.builder()
                        .id(sessionId)
                        .maxTokens(tokenBudget, new OpenAiTokenCountEstimator("gpt-4o"))
                        .chatMemoryStore(store)
                        .build();
            }
            case "summarization" -> {
                int threshold = toInt(context.getParameters().get("memorySummaryThreshold"), 10);
                yield new SummarizingChatMemory(chatModel, threshold, 0, store, sessionId);
            }
            case "hybrid" -> {
                int windowSize = toInt(context.getParameters().get("memoryWindowSize"), 20);
                int threshold = toInt(context.getParameters().get("memorySummaryThreshold"), 10);
                yield new SummarizingChatMemory(chatModel, threshold, windowSize, store, sessionId);
            }
            default -> { // "default"
                yield MessageWindowChatMemory.builder()
                        .id(sessionId)
                        .maxMessages(fallbackMaxMessages)
                        .chatMemoryStore(store)
                        .build();
            }
        };
    }

    /**
     * Resolves the session ID for memory scoping.
     * Combines the workflow ID with a user-configurable session key.
     */
    private static String resolveSessionId(NodeExecutionContext context) {
        String sessionKey = context.getParameter("memorySessionId", "default");
        return context.getWorkflowId() + "__" + sessionKey;
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
                        .defaultValue("default")
                        .options(List.of(
                                ParameterOption.builder().name("Default").value("default").build(),
                                ParameterOption.builder().name("Sliding Window").value("sliding_window").build(),
                                ParameterOption.builder().name("Token Budget").value("token_budget").build(),
                                ParameterOption.builder().name("Summarization").value("summarization").build(),
                                ParameterOption.builder().name("Hybrid (Window + Summary)").value("hybrid").build()
                        ))
                        .description("How the agent manages conversation memory. Connect a database memory node to persist chat history across restarts.")
                        .build(),
                NodeParameter.builder()
                        .name("memorySessionId").displayName("Session ID")
                        .type(ParameterType.STRING)
                        .defaultValue("default")
                        .description("A unique key to isolate this conversation. Use different IDs for different users or threads.")
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
