package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.cwc.entity.ExecutionSettingsEntity;

import java.util.Optional;

public interface ExecutionSettingsRepository extends JpaRepository<ExecutionSettingsEntity, String> {
    Optional<ExecutionSettingsEntity> findFirstByOrderByCreatedAtAsc();
}
