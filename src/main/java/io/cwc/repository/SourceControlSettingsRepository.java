package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.cwc.entity.SourceControlSettingsEntity;

import java.util.Optional;

@Repository
public interface SourceControlSettingsRepository extends JpaRepository<SourceControlSettingsEntity, String> {
    Optional<SourceControlSettingsEntity> findFirstByOrderByCreatedAtAsc();
}
