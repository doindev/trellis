package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.cwc.entity.WorkflowShareEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowShareRepository extends JpaRepository<WorkflowShareEntity, String> {
    List<WorkflowShareEntity> findByWorkflowId(String workflowId);
    List<WorkflowShareEntity> findByUserId(String userId);
    Optional<WorkflowShareEntity> findByWorkflowIdAndUserId(String workflowId, String userId);
    boolean existsByWorkflowIdAndUserId(String workflowId, String userId);
    void deleteByWorkflowId(String workflowId);
}
