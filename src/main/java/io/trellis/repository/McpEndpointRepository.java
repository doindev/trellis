package io.trellis.repository;

import io.trellis.entity.McpEndpointEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface McpEndpointRepository extends JpaRepository<McpEndpointEntity, String> {
    List<McpEndpointEntity> findByEnabledTrue();
    boolean existsByPath(String path);
    Optional<McpEndpointEntity> findByPath(String path);
}
