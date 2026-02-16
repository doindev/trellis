package io.trellis.repository;

import io.trellis.entity.CredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CredentialRepository extends JpaRepository<CredentialEntity, String> {
    List<CredentialEntity> findByType(String type);
    List<CredentialEntity> findByNameContainingIgnoreCase(String name);
}
