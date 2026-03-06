package io.cwc.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.cwc.dto.ChatMessageResponse;
import io.cwc.entity.ChatAgentEntity;
import io.cwc.entity.ChatMessageEntity;
import io.cwc.entity.ChatSessionEntity;
import io.cwc.repository.ChatMessageRepository;
import io.cwc.repository.ChatSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
public class ChatService {

    private final Optional<ChatModel> chatModel;
    private final Optional<StreamingChatModel> streamingModel;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatAgentService chatAgentService;

    private static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful workflow automation assistant for CWC" +
            ". Help users build, debug, and optimize their workflows. " +
            "Be concise and practical.";

    public ChatService(
            Optional<ChatModel> chatModel,
            Optional<StreamingChatModel> streamingModel,
            SimpMessagingTemplate messagingTemplate,
            ChatMessageRepository chatMessageRepository,
            ChatSessionRepository chatSessionRepository,
            ChatAgentService chatAgentService) {
        this.chatModel = chatModel;
        this.streamingModel = streamingModel;
        this.messagingTemplate = messagingTemplate;
        this.chatMessageRepository = chatMessageRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatAgentService = chatAgentService;
    }

    @Transactional
    public ChatMessageResponse sendMessage(String sessionId, String content) {
        ChatSessionEntity session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new io.cwc.exception.NotFoundException("Chat session not found: " + sessionId));

        // Save user message
        ChatMessageEntity userMsg = ChatMessageEntity.builder()
                .sessionId(sessionId)
                .role("user")
                .content(content)
                .build();
        chatMessageRepository.save(userMsg);

        // Touch session updatedAt
        session.setUpdatedAt(Instant.now());
        chatSessionRepository.save(session);

        // Resolve system prompt from agent
        String systemPrompt = resolveSystemPrompt(session.getAgentId());

        // Load full history
        List<ChatMessageEntity> history = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

        if (streamingModel.isPresent()) {
            streamResponse(sessionId, systemPrompt, history);
        } else if (chatModel.isPresent()) {
            syncResponse(sessionId, systemPrompt, history);
        } else {
            saveAndSend(sessionId, "AI chat is not configured. Set `cwc.ai.openai.api-key` in application.properties to enable it.");
        }

        return toResponse(userMsg);
    }

    public List<ChatMessageResponse> getHistory(String sessionId) {
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(this::toResponse)
                .toList();
    }

    private String resolveSystemPrompt(String agentId) {
        if (agentId == null) {
            return DEFAULT_SYSTEM_PROMPT;
        }
        try {
            ChatAgentEntity agent = chatAgentService.findById(agentId);
            return agent.getSystemPrompt() != null ? agent.getSystemPrompt() : DEFAULT_SYSTEM_PROMPT;
        } catch (Exception e) {
            return DEFAULT_SYSTEM_PROMPT;
        }
    }

    private void syncResponse(String sessionId, String systemPrompt, List<ChatMessageEntity> history) {
        try {
            var messages = buildMessages(systemPrompt, history);
            ChatResponse response = chatModel.get().chat(messages);
            String text = response.aiMessage().text();
            saveAndSend(sessionId, text);
        } catch (Exception e) {
            log.error("Chat error for session {}: {}", sessionId, e.getMessage());
            saveAndSend(sessionId, "Sorry, I encountered an error processing your request.");
        }
    }

    private void streamResponse(String sessionId, String systemPrompt, List<ChatMessageEntity> history) {
        try {
            var messages = buildMessages(systemPrompt, history);
            StringBuilder fullResponse = new StringBuilder();

            streamingModel.get().chat(messages, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    fullResponse.append(partialResponse);
                }

                @Override
                public void onCompleteResponse(ChatResponse chatResponse) {
                    saveAndSend(sessionId, fullResponse.toString());
                }

                @Override
                public void onError(Throwable error) {
                    log.error("Streaming chat error for session {}: {}", sessionId, error.getMessage());
                    saveAndSend(sessionId, "Sorry, I encountered an error processing your request.");
                }
            });
        } catch (Exception e) {
            log.error("Chat error for session {}: {}", sessionId, e.getMessage());
            saveAndSend(sessionId, "Sorry, I encountered an error processing your request.");
        }
    }

    private List<ChatMessage> buildMessages(String systemPrompt, List<ChatMessageEntity> history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));

        for (ChatMessageEntity entry : history) {
            if ("user".equals(entry.getRole())) {
                messages.add(UserMessage.from(entry.getContent()));
            } else if ("assistant".equals(entry.getRole())) {
                messages.add(AiMessage.from(entry.getContent()));
            }
        }

        return messages;
    }

    private void saveAndSend(String sessionId, String content) {
        ChatMessageEntity assistantMsg = ChatMessageEntity.builder()
                .sessionId(sessionId)
                .role("assistant")
                .content(content)
                .build();
        chatMessageRepository.save(assistantMsg);
        sendToWebSocket(sessionId, assistantMsg);
    }

    private void sendToWebSocket(String sessionId, ChatMessageEntity msg) {
        try {
            messagingTemplate.convertAndSend("/topic/chat/" + sessionId, Map.of(
                    "id", msg.getId(),
                    "role", msg.getRole(),
                    "content", msg.getContent(),
                    "timestamp", msg.getCreatedAt().toString()
            ));
        } catch (Exception e) {
            log.warn("Failed to send chat message via WebSocket: {}", e.getMessage());
        }
    }

    private ChatMessageResponse toResponse(ChatMessageEntity entity) {
        return ChatMessageResponse.builder()
                .id(entity.getId())
                .sessionId(entity.getSessionId())
                .role(entity.getRole())
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
