package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.cwc.entity.EnvironmentEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnvironmentRepository extends JpaRepository<EnvironmentEntity, String> {
    List<EnvironmentEntity> findAllByOrderBySortOrderAsc();
    Optional<EnvironmentEntity> findByName(String name);
    Optional<EnvironmentEntity> findByGitBranch(String gitBranch);
}
