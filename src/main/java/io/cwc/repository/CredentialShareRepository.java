package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.cwc.entity.CredentialShareEntity;

import java.util.List;

@Repository
public interface CredentialShareRepository extends JpaRepository<CredentialShareEntity, String> {
    List<CredentialShareEntity> findByCredentialId(String credentialId);
    List<CredentialShareEntity> findByTargetProjectId(String targetProjectId);
    void deleteByCredentialIdAndTargetProjectId(String credentialId, String targetProjectId);
}
