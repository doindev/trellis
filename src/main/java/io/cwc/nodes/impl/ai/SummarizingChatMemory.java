package io.cwc.nodes.impl.ai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.util.ArrayList;
import java.util.List;

/**
 * ChatMemory implementation that summarizes older messages when the conversation
 * exceeds a threshold. In hybrid mode, keeps the last {@code windowSize} messages
 * verbatim and summarizes everything before that.
 *
 * <p>Supports an optional {@link ChatMemoryStore} for persistence. When a store
 * is provided, messages are loaded on first access and persisted after every change,
 * enabling chat history to survive restarts and be shared across instances.
 */
public class SummarizingChatMemory implements ChatMemory {

    private final ChatModel chatModel;
    private final int summaryThreshold;
    private final int windowSize; // 0 = pure summarization, >0 = hybrid
    private final ChatMemoryStore store;
    private final Object memoryId;
    private final List<ChatMessage> messages = new ArrayList<>();
    private String cachedSummary;
    private boolean initialized = false;

    /**
     * @param chatModel        model used to generate summaries
     * @param summaryThreshold trigger summarization when messages exceed this count
     * @param windowSize       number of recent messages to keep verbatim (0 for pure summarization)
     * @param store            backing store for persistence (may be an InMemoryChatMemoryStore)
     * @param memoryId         session/memory identifier for the store
     */
    public SummarizingChatMemory(ChatModel chatModel, int summaryThreshold, int windowSize,
                                  ChatMemoryStore store, Object memoryId) {
        this.chatModel = chatModel;
        this.summaryThreshold = Math.max(summaryThreshold, 2);
        this.windowSize = Math.max(windowSize, 0);
        this.store = store;
        this.memoryId = memoryId;
    }

    @Override
    public Object id() {
        return memoryId;
    }

    @Override
    public void add(ChatMessage message) {
        ensureInitialized();
        messages.add(message);
        if (messages.size() > summaryThreshold) {
            compactMessages();
        }
        persist();
    }

    @Override
    public List<ChatMessage> messages() {
        ensureInitialized();
        return List.copyOf(messages);
    }

    @Override
    public void clear() {
        messages.clear();
        cachedSummary = null;
        initialized = true;
        store.deleteMessages(memoryId);
    }

    private void ensureInitialized() {
        if (initialized) return;
        initialized = true;
        List<ChatMessage> loaded = store.getMessages(memoryId);
        if (loaded != null && !loaded.isEmpty()) {
            messages.addAll(loaded);
        }
    }

    private void persist() {
        store.updateMessages(memoryId, List.copyOf(messages));
    }

    private void compactMessages() {
        // Determine how many recent messages to keep verbatim
        int keep = windowSize > 0 ? windowSize : summaryThreshold / 2;
        if (keep >= messages.size()) return;

        // Split into old (to summarize) and recent (to keep)
        List<ChatMessage> toSummarize = new ArrayList<>(messages.subList(0, messages.size() - keep));
        List<ChatMessage> recent = new ArrayList<>(messages.subList(messages.size() - keep, messages.size()));

        // Build the conversation text for summarization
        StringBuilder conversationText = new StringBuilder();
        if (cachedSummary != null) {
            conversationText.append("Previous summary: ").append(cachedSummary).append("\n\n");
        }
        for (ChatMessage msg : toSummarize) {
            if (msg instanceof SystemMessage) {
                // Skip system messages from summarization
                continue;
            } else if (msg instanceof UserMessage user) {
                conversationText.append("User: ").append(user.singleText()).append("\n");
            } else if (msg instanceof AiMessage ai) {
                conversationText.append("Assistant: ").append(ai.text()).append("\n");
            }
        }

        if (conversationText.isEmpty()) return;

        try {
            String summaryPrompt = "Summarize the following conversation concisely, preserving key facts, " +
                    "decisions, and any important context. Output only the summary, no preamble:\n\n" +
                    conversationText;

            var response = chatModel.chat(UserMessage.from(summaryPrompt));
            cachedSummary = response.aiMessage().text();

            // Rebuild messages: summary as system message + recent messages
            messages.clear();
            messages.add(SystemMessage.from("Conversation summary so far: " + cachedSummary));
            messages.addAll(recent);
        } catch (Exception e) {
            // If summarization fails, fall back to simple truncation
            messages.clear();
            messages.addAll(recent);
        }
    }
}
