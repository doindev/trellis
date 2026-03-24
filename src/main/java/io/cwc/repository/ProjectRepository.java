package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.cwc.entity.ProjectEntity;
import io.cwc.entity.ProjectEntity.ProjectType;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<ProjectEntity, String> {
    List<ProjectEntity> findByType(ProjectType type);
    Optional<ProjectEntity> findByNameAndType(String name, ProjectType type);
    Optional<ProjectEntity> findByContextPath(String contextPath);
    Optional<ProjectEntity> findByConfigId(String configId);
    List<ProjectEntity> findByContextPathIsNotNull();
}
