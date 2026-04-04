package io.cwc.repository;

import io.cwc.entity.McpApiTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface McpApiTokenRepository extends JpaRepository<McpApiTokenEntity, String> {

    Optional<McpApiTokenEntity> findByTokenHash(String tokenHash);

    List<McpApiTokenEntity> findByUserIdAndMcpEndpointId(String userId, String mcpEndpointId);

    List<McpApiTokenEntity> findByUserId(String userId);

    List<McpApiTokenEntity> findByMcpEndpointId(String mcpEndpointId);

    long countByUserIdAndMcpEndpointId(String userId, String mcpEndpointId);

    void deleteByMcpEndpointId(String mcpEndpointId);
}
