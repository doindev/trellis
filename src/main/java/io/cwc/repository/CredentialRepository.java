package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.cwc.entity.CredentialEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface CredentialRepository extends JpaRepository<CredentialEntity, String> {
    Optional<CredentialEntity> findByProjectIdAndConfigId(String projectId, String configId);
    List<CredentialEntity> findByType(String type);
    List<CredentialEntity> findByNameContainingIgnoreCase(String name);
    List<CredentialEntity> findByProjectId(String projectId);
    List<CredentialEntity> findByProjectIdIsNull();
}
