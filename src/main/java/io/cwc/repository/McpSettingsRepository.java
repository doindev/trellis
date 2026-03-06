package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.cwc.entity.McpSettingsEntity;

import java.util.Optional;

public interface McpSettingsRepository extends JpaRepository<McpSettingsEntity, String> {
    Optional<McpSettingsEntity> findFirstByOrderByCreatedAtAsc();
}
