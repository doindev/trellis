package io.trellis.repository;

import io.trellis.entity.ChatAgentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatAgentRepository extends JpaRepository<ChatAgentEntity, String> {
    List<ChatAgentEntity> findAllByOrderByCreatedAtDesc();
}
