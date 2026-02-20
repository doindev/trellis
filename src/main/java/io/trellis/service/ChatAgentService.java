package io.trellis.service;

import io.trellis.dto.ChatAgentRequest;
import io.trellis.dto.ChatAgentResponse;
import io.trellis.entity.ChatAgentEntity;
import io.trellis.exception.NotFoundException;
import io.trellis.repository.ChatAgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatAgentService {

    private final ChatAgentRepository chatAgentRepository;

    public List<ChatAgentResponse> listAgents() {
        return chatAgentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public ChatAgentResponse getAgent(String id) {
        return toResponse(findById(id));
    }

    @Transactional
    public ChatAgentResponse createAgent(ChatAgentRequest request) {
        ChatAgentEntity entity = ChatAgentEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .systemPrompt(request.getSystemPrompt())
                .icon(request.getIcon())
                .model(request.getModel())
                .build();
        return toResponse(chatAgentRepository.save(entity));
    }

    @Transactional
    public ChatAgentResponse updateAgent(String id, ChatAgentRequest request) {
        ChatAgentEntity entity = findById(id);
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setSystemPrompt(request.getSystemPrompt());
        entity.setIcon(request.getIcon());
        entity.setModel(request.getModel());
        return toResponse(chatAgentRepository.save(entity));
    }

    @Transactional
    public void deleteAgent(String id) {
        ChatAgentEntity entity = findById(id);
        chatAgentRepository.delete(entity);
    }

    ChatAgentEntity findById(String id) {
        return chatAgentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Chat agent not found: " + id));
    }

    private ChatAgentResponse toResponse(ChatAgentEntity entity) {
        return ChatAgentResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .systemPrompt(entity.getSystemPrompt())
                .icon(entity.getIcon())
                .model(entity.getModel())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
