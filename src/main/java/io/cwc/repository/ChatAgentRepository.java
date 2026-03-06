package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.cwc.entity.ChatAgentEntity;

import java.util.List;

@Repository
public interface ChatAgentRepository extends JpaRepository<ChatAgentEntity, String> {
    List<ChatAgentEntity> findAllByOrderByCreatedAtDesc();
}
