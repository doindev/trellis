package io.trellis.repository;

import io.trellis.entity.WorkflowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WorkflowRepository extends JpaRepository<WorkflowEntity, String> {
    List<WorkflowEntity> findByPublished(boolean published);
    List<WorkflowEntity> findByNameContainingIgnoreCase(String name);
    List<WorkflowEntity> findByProjectId(String projectId);
    List<WorkflowEntity> findByProjectIdIsNull();
}
