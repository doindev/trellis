package io.cwc.repository;

import io.cwc.entity.ProjectSourceControlEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectSourceControlRepository extends JpaRepository<ProjectSourceControlEntity, String> {

    Optional<ProjectSourceControlEntity> findByProjectIdAndUserId(String projectId, String userId);

    List<ProjectSourceControlEntity> findByProjectId(String projectId);

    void deleteByProjectId(String projectId);

    void deleteByProjectIdAndUserId(String projectId, String userId);
}
