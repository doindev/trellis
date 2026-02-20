package io.trellis.repository;

import io.trellis.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, String> {
    List<ChatMessageEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);
    void deleteBySessionId(String sessionId);
}
