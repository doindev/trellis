package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.cwc.entity.ChatMessageEntity;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, String> {
    List<ChatMessageEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);
    void deleteBySessionId(String sessionId);
}
