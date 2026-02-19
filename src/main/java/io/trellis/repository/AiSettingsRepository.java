package io.trellis.repository;

import io.trellis.entity.AiSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiSettingsRepository extends JpaRepository<AiSettingsEntity, String> {
    Optional<AiSettingsEntity> findFirstByOrderByCreatedAtAsc();
}
