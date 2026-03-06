package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.cwc.entity.AiSettingsEntity;

import java.util.Optional;

public interface AiSettingsRepository extends JpaRepository<AiSettingsEntity, String> {
    Optional<AiSettingsEntity> findFirstByOrderByCreatedAtAsc();
}
