package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.cwc.entity.CredentialEntity;

import java.util.List;

@Repository
public interface CredentialRepository extends JpaRepository<CredentialEntity, String> {
    List<CredentialEntity> findByType(String type);
    List<CredentialEntity> findByNameContainingIgnoreCase(String name);
    List<CredentialEntity> findByProjectId(String projectId);
    List<CredentialEntity> findByProjectIdIsNull();
}
