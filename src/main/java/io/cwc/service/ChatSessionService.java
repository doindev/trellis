package io.cwc.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cwc.dto.ChatSessionRequest;
import io.cwc.dto.ChatSessionResponse;
import io.cwc.entity.ChatSessionEntity;
import io.cwc.exception.NotFoundException;
import io.cwc.repository.ChatMessageRepository;
import io.cwc.repository.ChatSessionRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    public List<ChatSessionResponse> listSessions() {
        return chatSessionRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public ChatSessionResponse getSession(String id) {
        return toResponse(findById(id));
    }

    @Transactional
    public ChatSessionResponse createSession(ChatSessionRequest request) {
        ChatSessionEntity entity = ChatSessionEntity.builder()
                .title(request.getTitle() != null ? request.getTitle() : "New Chat")
                .agentId(request.getAgentId())
                .workflowId(request.getWorkflowId())
                .build();
        return toResponse(chatSessionRepository.save(entity));
    }

    @Transactional
    public ChatSessionResponse getOrCreateSession(ChatSessionRequest request) {
        if (request.getWorkflowId() != null && !request.getWorkflowId().isBlank()) {
            var existing = chatSessionRepository
                    .findFirstByWorkflowIdOrderByUpdatedAtDesc(request.getWorkflowId());
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }
        return createSession(request);
    }

    @Transactional
    public ChatSessionResponse updateSession(String id, ChatSessionRequest request) {
        ChatSessionEntity entity = findById(id);
        if (request.getTitle() != null) {
            entity.setTitle(request.getTitle());
        }
        if (request.getAgentId() != null) {
            entity.setAgentId(request.getAgentId());
        }
        return toResponse(chatSessionRepository.save(entity));
    }

    @Transactional
    public void deleteSession(String id) {
        ChatSessionEntity entity = findById(id);
        chatMessageRepository.deleteBySessionId(id);
        chatSessionRepository.delete(entity);
    }

    ChatSessionEntity findById(String id) {
        return chatSessionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Chat session not found: " + id));
    }

    private ChatSessionResponse toResponse(ChatSessionEntity entity) {
        return ChatSessionResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .agentId(entity.getAgentId())
                .workflowId(entity.getWorkflowId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
