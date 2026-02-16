package io.trellis.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ChatService {

    private final Optional<ChatModel> chatModel;
    private final Optional<StreamingChatModel> streamingModel;
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, List<ChatHistoryEntry>> conversationHistory = new ConcurrentHashMap<>();

    private static final String SYSTEM_PROMPT = "You are a helpful workflow automation assistant for Trellis, " +
            "a platform similar to n8n/Make/Zapier. Help users build, debug, and optimize their workflows. " +
            "Be concise and practical.";

    public ChatService(
            Optional<ChatModel> chatModel,
            Optional<StreamingChatModel> streamingModel,
            SimpMessagingTemplate messagingTemplate) {
        this.chatModel = chatModel;
        this.streamingModel = streamingModel;
        this.messagingTemplate = messagingTemplate;
    }

    public ChatHistoryEntry sendMessage(String workflowId, String content) {
        var history = conversationHistory.computeIfAbsent(workflowId, k -> new ArrayList<>());

        var userEntry = new ChatHistoryEntry("user", content, Instant.now());
        history.add(userEntry);

        if (streamingModel.isPresent()) {
            streamResponse(workflowId, history);
        } else if (chatModel.isPresent()) {
            syncResponse(workflowId, history);
        } else {
            var fallback = new ChatHistoryEntry("assistant",
                    "AI chat is not configured. Set `trellis.ai.openai.api-key` in application.properties to enable it.",
                    Instant.now());
            history.add(fallback);
            sendToWebSocket(workflowId, fallback);
        }

        return userEntry;
    }

    public List<ChatHistoryEntry> getHistory(String workflowId) {
        return conversationHistory.getOrDefault(workflowId, List.of());
    }

    private void syncResponse(String workflowId, List<ChatHistoryEntry> history) {
        try {
            var messages = buildMessages(history);
            ChatResponse response = chatModel.get().chat(messages);
            String text = response.aiMessage().text();

            var entry = new ChatHistoryEntry("assistant", text, Instant.now());
            history.add(entry);
            sendToWebSocket(workflowId, entry);
        } catch (Exception e) {
            log.error("Chat error for workflow {}: {}", workflowId, e.getMessage());
            var errorEntry = new ChatHistoryEntry("assistant",
                    "Sorry, I encountered an error processing your request.", Instant.now());
            history.add(errorEntry);
            sendToWebSocket(workflowId, errorEntry);
        }
    }

    private void streamResponse(String workflowId, List<ChatHistoryEntry> history) {
        try {
            var messages = buildMessages(history);
            StringBuilder fullResponse = new StringBuilder();

            streamingModel.get().chat(messages, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    fullResponse.append(partialResponse);
                }

                @Override
                public void onCompleteResponse(ChatResponse chatResponse) {
                    var entry = new ChatHistoryEntry("assistant", fullResponse.toString(), Instant.now());
                    history.add(entry);
                    sendToWebSocket(workflowId, entry);
                }

                @Override
                public void onError(Throwable error) {
                    log.error("Streaming chat error for workflow {}: {}", workflowId, error.getMessage());
                    var errorEntry = new ChatHistoryEntry("assistant",
                            "Sorry, I encountered an error processing your request.", Instant.now());
                    history.add(errorEntry);
                    sendToWebSocket(workflowId, errorEntry);
                }
            });
        } catch (Exception e) {
            log.error("Chat error for workflow {}: {}", workflowId, e.getMessage());
            var errorEntry = new ChatHistoryEntry("assistant",
                    "Sorry, I encountered an error processing your request.", Instant.now());
            history.add(errorEntry);
            sendToWebSocket(workflowId, errorEntry);
        }
    }

    private List<ChatMessage> buildMessages(List<ChatHistoryEntry> history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(SYSTEM_PROMPT));

        for (ChatHistoryEntry entry : history) {
            if ("user".equals(entry.getRole())) {
                messages.add(UserMessage.from(entry.getContent()));
            } else if ("assistant".equals(entry.getRole())) {
                messages.add(AiMessage.from(entry.getContent()));
            }
        }

        return messages;
    }

    private void sendToWebSocket(String workflowId, ChatHistoryEntry entry) {
        try {
            messagingTemplate.convertAndSend("/topic/chat/" + workflowId, Map.of(
                    "role", entry.getRole(),
                    "content", entry.getContent(),
                    "timestamp", entry.getTimestamp().toString()
            ));
        } catch (Exception e) {
            log.warn("Failed to send chat message via WebSocket: {}", e.getMessage());
        }
    }

    @Data
    public static class ChatHistoryEntry {
        private final String role;
        private final String content;
        private final Instant timestamp;
    }
}
