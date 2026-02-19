package io.trellis.repository;

import io.trellis.entity.ProjectEntity;
import io.trellis.entity.ProjectEntity.ProjectType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<ProjectEntity, String> {
    List<ProjectEntity> findByType(ProjectType type);
    Optional<ProjectEntity> findByNameAndType(String name, ProjectType type);
}
