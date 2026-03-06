package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.cwc.entity.SwaggerSettingsEntity;

import java.util.Optional;

public interface SwaggerSettingsRepository extends JpaRepository<SwaggerSettingsEntity, String> {
    Optional<SwaggerSettingsEntity> findFirstByOrderByCreatedAtAsc();
}
