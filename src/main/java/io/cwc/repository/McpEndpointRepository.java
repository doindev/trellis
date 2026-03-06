package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.cwc.entity.McpEndpointEntity;

import java.util.List;
import java.util.Optional;

public interface McpEndpointRepository extends JpaRepository<McpEndpointEntity, String> {
    List<McpEndpointEntity> findByEnabledTrue();
    boolean existsByPath(String path);
    Optional<McpEndpointEntity> findByPath(String path);
}
