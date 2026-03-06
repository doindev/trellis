package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.cwc.entity.WorkflowEntity;

import java.util.List;

@Repository
public interface WorkflowRepository extends JpaRepository<WorkflowEntity, String> {
    List<WorkflowEntity> findByPublished(boolean published);
    List<WorkflowEntity> findByNameContainingIgnoreCase(String name);
    List<WorkflowEntity> findByProjectId(String projectId);
    List<WorkflowEntity> findByProjectIdAndPublished(String projectId, boolean published);
    List<WorkflowEntity> findByProjectIdIsNull();
    List<WorkflowEntity> findByMcpEnabledTrue();
    List<WorkflowEntity> findBySwaggerEnabledTrue();
}
