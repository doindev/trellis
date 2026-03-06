package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.cwc.entity.ApiKeyEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, String> {
    Optional<ApiKeyEntity> findByKeyHash(String keyHash);
    List<ApiKeyEntity> findByUserId(String userId);
}
