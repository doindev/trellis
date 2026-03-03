package io.trellis.repository;

import io.trellis.entity.CacheDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CacheDefinitionRepository extends JpaRepository<CacheDefinitionEntity, String> {

    List<CacheDefinitionEntity> findAllByOrderByCreatedAtDesc();

    Optional<CacheDefinitionEntity> findByName(String name);

    List<CacheDefinitionEntity> findByProjectIdOrderByCreatedAtDesc(String projectId);

    List<CacheDefinitionEntity> findByProjectIdIsNull();
}
