package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.cwc.entity.ChatSessionEntity;

import java.util.List;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, String> {
    List<ChatSessionEntity> findAllByOrderByUpdatedAtDesc();
}
