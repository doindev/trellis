package io.trellis.repository;

import io.trellis.entity.ExecutionEntity;
import io.trellis.entity.ExecutionEntity.ExecutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExecutionRepository extends JpaRepository<ExecutionEntity, String> {
    List<ExecutionEntity> findByWorkflowIdOrderByStartedAtDesc(String workflowId);
    Page<ExecutionEntity> findByWorkflowId(String workflowId, Pageable pageable);
    Page<ExecutionEntity> findByStatus(ExecutionStatus status, Pageable pageable);
    Page<ExecutionEntity> findByWorkflowIdAndStatus(String workflowId, ExecutionStatus status, Pageable pageable);
    List<ExecutionEntity> findByStatus(ExecutionStatus status);
}
