package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.cwc.entity.ProjectRelationEntity;
import io.cwc.entity.ProjectRelationId;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRelationRepository extends JpaRepository<ProjectRelationEntity, ProjectRelationId> {
    List<ProjectRelationEntity> findByProjectId(String projectId);
    List<ProjectRelationEntity> findByUserId(String userId);
    Optional<ProjectRelationEntity> findByProjectIdAndUserId(String projectId, String userId);
    void deleteByProjectIdAndUserId(String projectId, String userId);
    boolean existsByProjectIdAndUserId(String projectId, String userId);
}
