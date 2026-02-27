package io.trellis.repository;

import io.trellis.entity.SwaggerSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SwaggerSettingsRepository extends JpaRepository<SwaggerSettingsEntity, String> {
    Optional<SwaggerSettingsEntity> findFirstByOrderByCreatedAtAsc();
}
