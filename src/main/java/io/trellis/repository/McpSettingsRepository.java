package io.trellis.repository;

import io.trellis.entity.McpSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface McpSettingsRepository extends JpaRepository<McpSettingsEntity, String> {
    Optional<McpSettingsEntity> findFirstByOrderByCreatedAtAsc();
}
